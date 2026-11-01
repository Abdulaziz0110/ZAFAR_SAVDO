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
import uz.imsoft.shared.report.Col
import uz.imsoft.shared.report.EntryKind
import uz.imsoft.shared.report.LedgerMerge
import uz.imsoft.shared.report.Stamp

/**
 * 2-ilova (Savdo Mijoz) qo'shimcha sozlamalari (yashirin menyu): faqat ilovani qulay qiladigan sozlamalar va o'z
 * ma'lumotingiz bo'yicha foydali bandlar. Kalitlar, do'kon kodi, ulanish ma'lumotlari va texnik bandlar YO'Q.
 */
class MijozSecretFragment : SecretFragment() {
    override val ledgerHost: LedgerHost get() = MijozLedgerHost
    override fun close() { parentFragmentManager.popBackStack() }
    override fun usdRate(): Double = Locator.data.state.value.snapshot?.rate ?: 0.0
    override val userOnly: Boolean = true
    override fun screenTitle(): String = getString(R.string.sx_title)
    override fun screenHint(n: Int): String = getString(R.string.sx_hint)

    override fun products(): List<Pair<String, String>> {
        val lv = Locator.prefs.priceLevel
        return Locator.data.state.value.snapshot?.products.orEmpty().filter { it.price(lv, false) > 0 || it.price(lv, true) > 0 }.map { p ->
            val som = p.price(lv, false)
            p.name to if (som > 0) requireContext().money(som, false) else requireContext().money(p.price(lv, true), true)
        }
    }

    override fun appItems(): List<SecretItem> {
        val l = ArrayList<SecretItem>()
        val p = Locator.prefs
        l += SecretItem("🏠", getString(R.string.sx_start), getString(R.string.sx_start_sub, startName(p.startTab))) { startTab() }
        l += SecretItem("🔆", getString(R.string.sx_screen), getString(if (p.keepScreenOn) R.string.sx_on else R.string.sx_off) + " · " + getString(R.string.sx_screen_sub)) { keepScreen() }
        l += SecretItem("📊", getString(R.string.sx_stats), getString(R.string.sx_stats_sub)) { stats() }
        l += SecretItem("📈", getString(R.string.sx_debt), getString(R.string.sx_debt_sub)) { debtTrend() }
        if (MijozLedgerHost.ordersEnabled()) l += SecretItem("🛒", getString(R.string.sx_last_cart), getString(R.string.sx_last_cart_sub)) { lastToCart() }
        l += SecretItem("⚡", getString(R.string.sx_turbo), getString(R.string.sx_turbo_sub)) { turbo() }
        return l
    }

    private val tabs = listOf("home", "prices", "report")

    private fun startName(t: String) = getString(
        when (t) { "prices" -> R.string.nav_prices; "report" -> R.string.nav_report; else -> R.string.nav_home },
    )

    private fun startTab() {
        val names = tabs.map { startName(it) }.toTypedArray()
        val cur = tabs.indexOf(Locator.prefs.startTab).coerceAtLeast(0)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.sx_start)
            .setSingleChoiceItems(names, cur) { d, i ->
                Locator.prefs.startTab = tabs[i]
                d.dismiss()
                snack(getString(R.string.sx_start_sub, names[i]))
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun keepScreen() {
        val v = !Locator.prefs.keepScreenOn
        Locator.prefs.keepScreenOn = v
        (activity as? MainActivity)?.applyKeepScreenOn()
        snack(getString(R.string.sx_screen) + ": " + getString(if (v) R.string.sx_on else R.string.sx_off))
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
            val r = runCatching { Locator.data.refresh(askFresh = true) }
            snack(r.fold({ getString(R.string.sx_turbo_ok) }, { getString(R.string.sx_turbo_fail) + "\n" + requireContext().describe(it) }))
        }
    }
}
