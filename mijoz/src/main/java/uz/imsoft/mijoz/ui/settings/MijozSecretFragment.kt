package uz.imsoft.mijoz.ui.settings

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.data.OrderCart
import uz.imsoft.mijoz.ui.Fmt
import uz.imsoft.mijoz.ui.MainActivity
import uz.imsoft.mijoz.ui.describe
import uz.imsoft.mijoz.ui.ledger.LedgerHost
import uz.imsoft.mijoz.ui.ledger.SecretFragment
import uz.imsoft.mijoz.ui.ledger.SecretItem
import uz.imsoft.mijoz.ui.ledgerhost.MijozLedgerHost
import uz.imsoft.mijoz.ui.money
import uz.imsoft.shared.Phone
import uz.imsoft.shared.ShopCode
import uz.imsoft.shared.report.Col
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.LedgerMerge
import uz.imsoft.shared.report.Stamp

/** 2-ilova (Savdo Mijoz) sirli menyusi: umumiy bandlar + mijozga xoslari. Faqat o'qiydi (buyurtma savatchasidan tashqari). */
class MijozSecretFragment : SecretFragment() {
    override val ledgerHost: LedgerHost get() = MijozLedgerHost
    override fun close() { parentFragmentManager.popBackStack() }
    override fun usdRate(): Double = Locator.data.state.value.snapshot?.rate ?: 0.0
    override fun workNames() = listOf("auto_refresh")

    override fun products(): List<Pair<String, String>> =
        Locator.data.state.value.snapshot?.products.orEmpty().filter { it.wholesaleSom > 0 || it.wholesaleUsd > 0 }.map { p ->
            p.name to if (p.wholesaleSom > 0) requireContext().money(p.wholesaleSom, false) else requireContext().money(p.wholesaleUsd, true)
        }

    override fun appItems(): List<SecretItem> {
        val l = ArrayList<SecretItem>()
        l += SecretItem("📊", getString(R.string.sx_stats), getString(R.string.sx_stats_sub)) { stats() }
        l += SecretItem("📈", getString(R.string.sx_debt), getString(R.string.sx_debt_sub)) { debtTrend() }
        if (MijozLedgerHost.ordersEnabled()) l += SecretItem("🛒", getString(R.string.sx_last_cart), getString(R.string.sx_last_cart_sub)) { lastToCart() }
        l += SecretItem("⚡", getString(R.string.sx_turbo), getString(R.string.sx_turbo_sub)) { turbo() }
        l += SecretItem("🔑", getString(R.string.sx_link), getString(R.string.sx_link_sub)) { passport() }
        return l
    }

    private fun stats() {
        val s = Locator.data.state.value
        val snap = s.snapshot
        val text = listOf(
            getString(R.string.sx_st_client, snap?.clientName ?: Locator.prefs.link?.clientName.orEmpty().ifEmpty { "—" }),
            getString(R.string.sx_st_data, snap?.products?.size ?: 0, snap?.ledger?.size ?: 0, snap?.coverage?.size ?: 0),
            getString(R.string.sx_st_times, if (s.publishedAt > 0) Fmt.dateTime(s.publishedAt) else "—", if (s.fetchedAt > 0) Fmt.dateTime(s.fetchedAt) else "—"),
            getString(R.string.sx_st_cart, Locator.cart.items.value.size, Locator.prefs.myOrders.split('|').count { it.isNotEmpty() }),
        ).joinToString("\n")
        info(getString(R.string.sx_stats), text)
    }

    /** Qarz qoldig'i (1C hisobotidagi "qoldiq" ustuni) — oxirgi 20 kun, kichik grafik. */
    private fun debtTrend() {
        val entries = Locator.data.state.value.snapshot?.ledger.orEmpty().filter { !it.v(Col.BALANCE).isZero }.sortedBy { it.stamp }
        if (entries.isEmpty()) { snack(getString(R.string.lg_sx_no_ledger)); return }
        val byDay = entries.groupBy { Stamp.dayStart(it.stamp) }.map { (d, l) -> d to l.last().v(Col.BALANCE) }.takeLast(20)
        val usd = byDay.all { it.second.som == 0.0 }
        val vals = byDay.map { if (usd) it.second.usd else it.second.som }
        val lo = vals.min(); val hi = vals.max()
        val bars = "▁▂▃▄▅▆▇█"
        val spark = vals.joinToString("") { v -> bars[if (hi == lo) 3 else ((v - lo) / (hi - lo) * 7).toInt().coerceIn(0, 7)].toString() }
        val ctx = requireContext()
        val first = byDay.first(); val last = byDay.last()
        val delta = vals.last() - vals.first()
        val text = spark + "\n\n" +
            Stamp.formatDate(first.first) + ": " + ctx.money(vals.first(), usd) + "\n" +
            Stamp.formatDate(last.first) + ": " + ctx.money(vals.last(), usd) + "\n" +
            getString(if (delta <= 0) R.string.sx_debt_down else R.string.sx_debt_up, ctx.money(kotlin.math.abs(delta), usd)) + "\n\n" +
            getString(R.string.sx_debt_note)
        info(getString(R.string.sx_debt), text)
    }

    private fun lastToCart() {
        val ledger = Locator.data.state.value.snapshot?.ledger.orEmpty()
        val lastDay = ledger.filter { it.kind == EntryKind.SALE && it.product.isNotBlank() }.maxOfOrNull { it.stamp } ?: run { snack(getString(R.string.lg_sx_no_ledger)); return }
        val lines = ledger.filter { it.kind == EntryKind.SALE && it.stamp == lastDay && it.product.isNotBlank() }
        val ids = Locator.data.state.value.snapshot?.products.orEmpty().associate { LedgerMerge.nameKey(it.name) to it.id }
        for (e in lines) {
            val key = LedgerMerge.nameKey(e.product) // "Narxlar" ro'yxatidagi kalit bilan bir xil
            val price = e.v(Col.PRICE)
            Locator.cart.set(OrderCart.Item(key, e.product, ids[LedgerMerge.nameKey(e.product)].orEmpty(), e.qty, if (price.som != 0.0) price.som else price.usd, price.som == 0.0 && price.usd != 0.0))
        }
        snack(getString(R.string.sx_last_cart_done, lines.size, Stamp.formatDate(lastDay)))
        (activity as? MainActivity)?.let { a -> parentFragmentManager.popBackStack(); a.openTab(R.id.nav_prices) }
    }

    private fun turbo() {
        snack(getString(R.string.sx_turbo_run))
        viewLifecycleOwner.lifecycleScope.launch {
            val t0 = System.currentTimeMillis()
            val r = runCatching { Locator.data.refresh(askFresh = true) }
            val ms = System.currentTimeMillis() - t0
            snack(r.fold({ getString(R.string.sx_turbo_ok, ms) + " · " + it.name }, { getString(R.string.sx_turbo_fail, ms) + "\n" + requireContext().describe(it) }))
        }
    }

    private fun passport() {
        val p = Locator.prefs
        val link = p.link
        val text = listOf(
            getString(R.string.sx_ln_shop, link?.dbUrl?.let { ShopCode.fromUrl(it) }?.ifEmpty { null } ?: "—"),
            getString(R.string.sx_ln_client, link?.clientName?.ifEmpty { null } ?: "—"),
            getString(R.string.sx_ln_phone, if (p.loginPhone.isNotEmpty()) Phone.pretty(p.loginPhone) else getString(R.string.sx_ln_code), p.memberName.ifBlank { "—" }),
            getString(R.string.sx_ln_key, link?.readKey?.let { mask(it) } ?: "—"),
            getString(R.string.sx_ln_printer, if (link?.queueKey.isNullOrEmpty()) "—" else mask(link!!.queueKey)),
        ).joinToString("\n")
        info(getString(R.string.sx_link), text)
    }
}
