package com.ilxomjon.dur_novvot_dokon.data.share

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.extras.Extras
import com.ilxomjon.dur_novvot_dokon.domain.CartLine
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.DealMode
import com.ilxomjon.dur_novvot_dokon.domain.PriceType
import com.ilxomjon.dur_novvot_dokon.domain.Pricing
import uz.imsoft.shared.OrderRequest

/**
 * v5.1.1: 2-ilovadan kelgan buyurtmani TASDIQLASH — savatchaga tegmasdan darhol savdo bo'lib rasmiylashadi
 * (optom narx, so'm, qarzga) va chek tanlangan printerdan chiqadi. Bildirishnomadagi "Tasdiqlash" doim
 * [PRINTER_DEFAULT] (Printer 1) bilan ishlaydi; ilova ichida Admin printerni o'zi tanlaydi.
 *
 * "Savatchaga olish" (eski yo'l) saqlanib qoldi: tovar yoki miqdorni o'zgartirish kerak bo'lsa.
 */
object OrderConfirm {
    /** Bildirishnomadan tasdiqlanganda chek shu printerdan chiqadi. */
    const val PRINTER_DEFAULT = "1"
    /** "Cheksiz" (chek chiqmaydi) — [com.ilxomjon.dur_novvot_dokon.ui.payment.PaymentFragment] bilan bir xil kod. */
    const val PRINTER_NONE = "3"

    class Result(val code: String, val added: Int, val missing: Int, val storeName: String)

    /** Printer kodini odam o'qiydigan nomga aylantiradi. */
    fun printerName(ctx: Context, printer: String): String =
        if (printer == PRINTER_NONE) ctx.getString(R.string.finish_no_printer)
        else ctx.getString(R.string.finish_printer_n, printer.toIntOrNull() ?: 1)

    /** Ilovada ko'rsatiladigan printer tanlovi: kod -> nom. */
    fun printerChoices(ctx: Context): List<Pair<String, String>> =
        listOf("1", "2", PRINTER_NONE).map { it to printerName(ctx, it) }

    /**
     * Buyurtma qatorlarini savdo qatorlariga aylantiradi: katalogda bor tovarlar qoladi, bir tovar bir necha qatorda
     * bo'lsa — miqdorlari qo'shiladi, tartibi saqlanadi. Narx har doim JORIY katalogdan ([price]).
     * @return qatorlar va katalogda topilmagan (yoki miqdori 0) qatorlar soni.
     */
    fun cartLines(
        lines: List<uz.imsoft.shared.OrderLine>,
        product: (String) -> com.ilxomjon.dur_novvot_dokon.domain.Product?,
        price: (com.ilxomjon.dur_novvot_dokon.domain.Product) -> Double,
    ): Pair<List<CartLine>, Int> {
        var missing = 0
        val found = LinkedHashMap<String, com.ilxomjon.dur_novvot_dokon.domain.Product>()
        val qtyById = LinkedHashMap<String, Double>()
        for (l in lines) {
            val p = l.productId.takeIf { it.isNotEmpty() }?.let(product)
            if (p == null || l.qty <= 0.0) { missing++; continue }
            found[p.id] = p
            qtyById[p.id] = (qtyById[p.id] ?: 0.0) + l.qty
        }
        var row = 1L
        val out = qtyById.map { (id, q) ->
            val p = found.getValue(id)
            CartLine(row++, p.id, p.name, p.blockSize, q, price(p))
        }
        return out to missing
    }

    suspend fun accept(ctx: Context, clientId: String, orderId: String, printer: String): Result {
        val found = ServiceLocator.share.orders.value.firstOrNull { it.order.id == orderId }
            ?: runCatching { ServiceLocator.share.refreshOrders(notify = false) }.getOrNull()?.firstOrNull { it.order.id == orderId }
            ?: throw IllegalStateException(ctx.getString(R.string.ord_gone))
        if (found.order.status == OrderRequest.Status.TAKEN) throw IllegalStateException(ctx.getString(R.string.ord_already))
        val client = ServiceLocator.shareStore.client(clientId.ifEmpty { found.clientId })
            ?: throw IllegalStateException(ctx.getString(R.string.cr_err_no_client))
        val store = client.storeId.takeIf { it.isNotEmpty() }?.let { ServiceLocator.catalog.storeById(it) }
            ?: throw IllegalStateException(ctx.getString(R.string.cl_need_store, client.name))
        val cat = ServiceLocator.catalog.state.value
        val (lines, missing) = cartLines(
            found.order.lines, { cat.productsById[it] },
            { p -> Pricing.unitPrice(p, PriceType.WHOLESALE, Currency.SOM, cat.rate) },
        )
        if (lines.isEmpty()) throw IllegalStateException(ctx.getString(R.string.repeat_nothing))
        val code = ServiceLocator.orders.sendDirect(store, DealMode.SALE, Currency.SOM, PriceType.WHOLESALE, lines, printer)
        runCatching { ServiceLocator.share.setOrderStatus(client.id, orderId, OrderRequest.Status.TAKEN) }
        return Result(code, lines.size, missing, store.name)
    }

    /** Natijani (yoki xatoni) bildirishnoma bilan aytadi — ilova yopiq bo'lsa ham ko'rinadi. */
    fun report(ctx: Context, notifId: Int, r: Result?, printer: String, error: String?) {
        if (r != null) {
            val parts = ArrayList<String>()
            parts += ctx.getString(R.string.ord_done, r.storeName, r.added, printerName(ctx, printer))
            if (r.missing > 0) parts += ctx.getString(R.string.ord_missing_n, r.missing)
            Extras.notify(ctx, notifId, ctx.getString(R.string.ord_done_title), parts.joinToString(" · "))
        } else {
            Extras.notify(ctx, notifId, ctx.getString(R.string.ord_fail_title), error.orEmpty())
        }
    }
}

/**
 * Bildirishnomadagi "Tasdiqlash"/"Rad etish" uchun: BroadcastReceiver uzoq ishlay olmaydi, shuning uchun ish
 * WorkManager'ga topshiriladi (ilova yopiq bo'lsa ham bajariladi).
 */
class OrderActionWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        ServiceLocator.ensureInit(ctx)
        val cid = inputData.getString(KEY_CLIENT).orEmpty()
        val oid = inputData.getString(KEY_ORDER).orEmpty()
        val printer = inputData.getString(KEY_PRINTER) ?: OrderConfirm.PRINTER_DEFAULT
        val notifId = inputData.getInt(KEY_NOTIF, 0)
        if (oid.isEmpty()) return Result.success()
        if (inputData.getBoolean(KEY_REJECT, false)) {
            runCatching { ServiceLocator.share.setOrderStatus(cid, oid, OrderRequest.Status.REJECTED) }
            return Result.success()
        }
        try {
            val r = OrderConfirm.accept(ctx, cid, oid, printer)
            OrderConfirm.report(ctx, notifId, r, printer, null)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            OrderConfirm.report(ctx, notifId, null, printer, e.message ?: e.javaClass.simpleName)
        }
        return Result.success()
    }

    companion object {
        const val KEY_CLIENT = "cid"
        const val KEY_ORDER = "oid"
        const val KEY_PRINTER = "printer"
        const val KEY_NOTIF = "notif"
        const val KEY_REJECT = "reject"

        fun enqueue(ctx: Context, clientId: String, orderId: String, printer: String, notifId: Int, reject: Boolean) {
            val data = Data.Builder()
                .putString(KEY_CLIENT, clientId).putString(KEY_ORDER, orderId).putString(KEY_PRINTER, printer)
                .putInt(KEY_NOTIF, notifId).putBoolean(KEY_REJECT, reject).build()
            // Expedited ishlatilmaydi: minSdk 26 da u getForegroundInfo() ni talab qiladi. Cheklovsiz ish baribir
            // darhol boshlanadi; savdo mahalliy navbatga tushadi, internet bo'lmasa keyin yuboriladi.
            val req = OneTimeWorkRequestBuilder<OrderActionWorker>().setInputData(data).build()
            runCatching { WorkManager.getInstance(ctx).enqueueUniqueWork("ord_act_" + orderId, ExistingWorkPolicy.KEEP, req) }
        }
    }
}

/** Bildirishnoma tugmalari shu yerga keladi (manifestda e'lon qilingan, tashqaridan chaqirilmaydi). */
class OrderActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: android.content.Intent) {
        val cid = intent.getStringExtra(OrderActionWorker.KEY_CLIENT).orEmpty()
        val oid = intent.getStringExtra(OrderActionWorker.KEY_ORDER).orEmpty()
        if (oid.isEmpty()) return
        val notifId = intent.getIntExtra(OrderActionWorker.KEY_NOTIF, 0)
        val reject = intent.action == ACTION_REJECT
        val ctx = context.applicationContext
        if (notifId != 0) runCatching {
            ctx.getSystemService(android.app.NotificationManager::class.java).cancel(notifId)
        }
        if (!reject) Extras.notifyOrderWorking(ctx, notifId)
        OrderActionWorker.enqueue(ctx, cid, oid, OrderConfirm.PRINTER_DEFAULT, notifId, reject)
    }

    companion object {
        const val ACTION_ACCEPT = "com.ilxomjon.dur_novvot_dokon.ORDER_ACCEPT"
        const val ACTION_REJECT = "com.ilxomjon.dur_novvot_dokon.ORDER_REJECT"
    }
}
