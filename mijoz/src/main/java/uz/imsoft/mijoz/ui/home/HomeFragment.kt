package uz.imsoft.mijoz.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.data.DataState
import uz.imsoft.mijoz.data.RefreshResult
import uz.imsoft.mijoz.databinding.FragmentHomeBinding
import uz.imsoft.mijoz.ui.Fmt
import uz.imsoft.mijoz.ui.MainActivity
import uz.imsoft.mijoz.ui.describe
import uz.imsoft.mijoz.ui.launchOnStarted
import uz.imsoft.mijoz.ui.money
import uz.imsoft.mijoz.ui.money2
import uz.imsoft.mijoz.ui.show
import uz.imsoft.shared.report.ClientStats
import uz.imsoft.shared.report.Stamp
import java.util.Calendar

class HomeFragment : Fragment() {
    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentHomeBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (uz.imsoft.mijoz.Locator.prefs.link != null) uz.imsoft.mijoz.ui.tour.Tour.show(this, uz.imsoft.mijoz.ui.tour.Tour.HOME)
        b.btnLink.setText(R.string.lgn_login)
        b.btnLink.setOnClickListener { (activity as? uz.imsoft.mijoz.ui.MainActivity)?.showLogin() }
        b.btnRefresh.setOnClickListener { refresh() }
        b.btnPrices.setOnClickListener { (activity as? MainActivity)?.openTab(R.id.nav_prices) }
        // v5.1.1: "Buyurtma berish" asosiy tugmalar qatorida. Buyurtma yopiq bo'lsa — shu tugma "Narxlar" bo'lib ishlaydi.
        b.btnOrder.setOnClickListener {
            (activity as? MainActivity)?.openTab(R.id.nav_prices)
            if (ordersOn()) activity?.let { a ->
                com.google.android.material.snackbar.Snackbar.make(a.findViewById(android.R.id.content), R.string.home_order_hint, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
            }
        }
        b.btnReport.setOnClickListener { (activity as? MainActivity)?.openTab(R.id.nav_report) }
        // v3.2: o'z kassasi va to'lov kiritish
        // v3.9: o'z savdosi (agentdagidek) — savdo ekrani, mijozlar, kassa tarixi/hisoboti; bizga to'lov kiritish
        val main = { activity as? MainActivity }
        b.btnNewSale.setOnClickListener { main()?.openTab(R.id.nav_sale) }
        bindSlot(b.btnSlot3, 3)
        bindSlot(b.btnSlot4, 4)
        addTiles()
        b.toolbar.inflateMenu(R.menu.home_menu)
        b.toolbar.setOnMenuItemClickListener { if (it.itemId == R.id.action_menu) { openMenu(); true } else false }
        launchOnStarted { Locator.data.state.collect { render(it) } }
        launchOnStarted { Locator.pos.state.collect { renderToday() } }
        launchOnStarted {
            // Internet holati: yo'q bo'lsa — oxirgi saqlangan ma'lumot vaqti bilan ogohlantirish (ilova ishlayveradi)
            while (true) { renderOffline(); kotlinx.coroutines.delay(10_000L) }
        }
    }

    /** Buyurtma berish yoqilganmi (1-ilova ruxsat bergan). */
    private fun ordersOn(): Boolean {
        val s = Locator.data.state.value
        return s.linked && s.snapshot?.features?.contains(uz.imsoft.shared.report.ClientSnapshot.FEATURE_ORDERS) == true
    }

    // ---------------- v5.0.3: tepadagi tanlanadigan tugmalar va ikonkali menyu ----------------

    private class Slot(val key: String, val title: String, val icon: Int, val go: () -> Unit)

    private fun slots(): List<Slot> {
        val main = { activity as? MainActivity }
        return listOf(
            Slot("stats", getString(R.string.ms_tile_stats), R.drawable.ic_trending) { main()?.push(uz.imsoft.mijoz.ui.extra.StatsFragment()) },
            Slot("history", getString(R.string.ms_tile_history), R.drawable.ic_history) {
                main()?.push(uz.imsoft.mijoz.ui.extra.PosFragment.open(uz.imsoft.mijoz.ui.extra.PosFragment.TAB_HISTORY))
            },
            Slot("report", getString(R.string.ms_tile_report), R.drawable.ic_report) {
                main()?.push(uz.imsoft.mijoz.ui.extra.PosFragment.open(uz.imsoft.mijoz.ui.extra.PosFragment.TAB_REPORT))
            },
            Slot("pay", getString(R.string.ms_tile_pay), R.drawable.ic_wallet) { main()?.push(uz.imsoft.mijoz.ui.extra.RequestsFragment()) },
            Slot("mysales", getString(R.string.nav_report), R.drawable.ic_person) { main()?.openTab(R.id.nav_report) },
            Slot("settings", getString(R.string.nav_settings), R.drawable.ic_settings) { main()?.openSettings() },
        )
    }

    /** Bosilsa ishlaydi, uzoq bosilsa boshqa amal tanlanadi. */
    private fun bindSlot(btn: com.google.android.material.button.MaterialButton, no: Int) {
        val prefs = Locator.prefs
        val list = slots()
        val key = if (no == 3) prefs.homeSlot3 else prefs.homeSlot4
        val slot = list.firstOrNull { it.key == key } ?: list.first()
        btn.text = slot.title
        btn.setIconResource(slot.icon)
        btn.setOnClickListener { slot.go() }
        btn.setOnLongClickListener {
            uz.imsoft.shared.android.Ui.menuSheet(
                requireContext(), getString(R.string.home_pick_action),
                list.map { sl ->
                    uz.imsoft.shared.android.Ui.MenuRow(sl.title, "", sl.icon, if (sl.key == key) 1 else 0) {
                        if (no == 3) prefs.homeSlot3 = sl.key else prefs.homeSlot4 = sl.key
                        if (view != null) bindSlot(btn, no)
                    }
                },
            )
            true
        }
    }

    /**
     * v5.4: yangi ko'rinish — ikki tanlanadigan tugma o'rniga HAMMA amallar yirik ikonkali plitkalar to'rida (3 ustun).
     * Eski tugmalar qatori va "uzoq bosing" izohi yashiriladi.
     */
    private fun addTiles() {
        val row = b.btnSlot3.parent as? ViewGroup ?: return
        val parent = row.parent as? ViewGroup ?: return
        if (parent.findViewWithTag<View>("home_tiles") != null) return
        row.visibility = View.GONE
        b.tvSlotHint.visibility = View.GONE
        val grid = uz.imsoft.shared.android.Ui.tiles(
            requireContext(),
            slots().map { sl -> uz.imsoft.shared.android.Ui.Tile(sl.title, sl.icon) { sl.go() } },
            columns = 3,
        )
        grid.tag = "home_tiles"
        parent.addView(grid, parent.indexOfChild(row) + 1)
    }

    private fun openMenu() {
        uz.imsoft.shared.android.Ui.menuSheet(
            requireContext(), "",
            slots().map { sl -> uz.imsoft.shared.android.Ui.MenuRow(sl.title, "", sl.icon) { sl.go() } },
        )
    }

    private var pollJob: kotlinx.coroutines.Job? = null

    private fun refresh() {
        // v3.4: "Yangilash" — 1-ilova (Admin) javob berguncha (odatda 1 daqiqa) har 15 soniyada o'zi tekshiradi, 3 daqiqagacha
        pollJob?.cancel()
        pollJob = Locator.scope.launch {
            repeat(12) {
                kotlinx.coroutines.delay(15_000L)
                if (!Locator.data.state.value.requestPending) return@launch
                runCatching { Locator.data.refresh(askFresh = false) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val msg = try {
                when (Locator.data.refresh(askFresh = true)) {
                    RefreshResult.UPDATED -> getString(R.string.home_updated_at, Fmt.dateTime(Locator.data.state.value.publishedAt))
                    RefreshResult.SAME -> getString(R.string.home_same_at, Fmt.dateTime(Locator.data.state.value.publishedAt))
                    RefreshResult.NO_DATA -> getString(R.string.home_no_data)
                    RefreshResult.NOT_LINKED -> getString(R.string.home_link_title)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                requireContext().describe(e)
            }
            _b?.let { Snackbar.make(it.root, msg, Snackbar.LENGTH_LONG).show() }
        }
    }

    private fun render(s: DataState) {
        val bb = _b ?: return
        val ctx = requireContext()
        bb.progress.show(s.loading)
        bb.btnRefresh.isEnabled = !s.loading && s.linked
        bb.cardLink.show(s.loaded && !s.linked)
        val canOrder = s.linked && s.snapshot?.features?.contains(uz.imsoft.shared.report.ClientSnapshot.FEATURE_ORDERS) == true
        bb.btnOrder.setText(if (canOrder) R.string.home_order else R.string.nav_prices)
        bb.btnOrder.setIconResource(if (canOrder) R.drawable.ic_order else R.drawable.ic_price)
        renderOffline()
        val snap = s.snapshot
        bb.toolbar.title = snap?.clientName?.ifEmpty { null } ?: Locator.prefs.link?.clientName?.ifEmpty { null } ?: getString(R.string.app_name)
        // Qarz: faqat server (1-ilova) ma'lumoti; vaqti bilan.
        // Qarz: server qiymati (1-ilova olgan, vaqti bilan); bo'lmasa — 1C hisobotidagi rasmiy yakuniy qoldiq.
        val debt = snap?.debt
        val rb = snap?.reportBalance
        fun amount(som: Double, usd: Double): String {
            val parts = ArrayList<String>()
            if (som != 0.0 || usd == 0.0) parts += ctx.money(som, false)
            if (usd != 0.0) parts += ctx.money(usd, true)
            return parts.joinToString("\n")
        }
        // v3.1: qaysi biri YANGIROQ bo'lsa katta raqamda o'sha: serverdan olingan qarz yoki 1C hisoboti (import vaqti bo'yicha)
        // v3.4: 1C kartochkasi (server yoki kompyuterdan) qarz bilan deyarli bir vaqtda olingan bo'lsa — asos shu kartochka
        // qoldig'i: undagi hujjatlar va 1-ilova yozuvlari bir paytdagi holat (ikki marta hisoblanmaydi).
        val reportNewer = rb != null && (snap?.lastImportAt ?: 0L) >= (debt?.at ?: 0L) - 15 * 60_000L
        // v3.4: asosiy qarz + undan KEYIN 1-ilovada qilingan savdo/to'lovlar (server hali hisoblamagan) — darhol to'g'ri qarz
        fun withPending(baseStamp: Long, som: Double, usd: Double, lines: ArrayList<String>): Pair<Double, Double> {
            val (eff, n) = uz.imsoft.shared.report.AppSale.pendingAfter(snap?.ledger.orEmpty(), baseStamp)
            if (n == 0) return som to usd
            lines.add(0, getString(R.string.home_pending_deals, n, (if (eff.som >= 0) "+" else "") + ctx.money2(eff)))
            return (som + eff.som) to (usd + eff.usd)
        }
        when {
            debt != null && !reportNewer -> {
                val lines = arrayListOf(getString(R.string.home_debt_at, if (debt.at > 0) Fmt.dateTime(debt.at) else "—") +
                    ": " + amount(debt.som, debt.usd).replace("\n", " + "))
                val (s0, u0) = withPending(uz.imsoft.shared.pos.Pos.stampOf(debt.at), debt.som, debt.usd, lines)
                bb.tvDebt.text = amount(s0, u0)
                if (rb != null) lines += getString(R.string.home_report_balance, Stamp.formatDate(rb.first), ctx.money2(rb.second))
                bb.tvDebtAt.text = lines.joinToString("\n")
            }
            rb != null -> {
                val lines = arrayListOf(getString(R.string.home_debt_report_at, Fmt.dateTime(snap?.lastImportAt ?: 0L)))
                // Kartochkada yo'q 1-ilova yozuvlari (1-ilova solishtirib qo'ygan) — hammasi qo'shiladi
                val (s0, u0) = withPending(0L, rb.second.som, rb.second.usd, lines)
                bb.tvDebt.text = amount(s0, u0)
                if (debt != null) lines += getString(R.string.home_debt_at, if (debt.at > 0) Fmt.dateTime(debt.at) else "—") + ": " + amount(debt.som, debt.usd).replace("\n", " + ")
                bb.tvDebtAt.text = lines.joinToString("\n")
            }
            else -> {
                bb.tvDebt.text = "—"
                bb.tvDebtAt.text = getString(if (snap == null) R.string.home_no_data_short else R.string.home_debt_unknown)
            }
        }
        bb.tvSync.text = when {
            !s.linked -> getString(R.string.home_not_linked)
            snap == null -> getString(R.string.home_no_data)
            else -> getString(R.string.home_sync, Fmt.dateTime(s.publishedAt.takeIf { it > 0 } ?: snap.createdAt), if (s.fetchedAt > 0) Fmt.dateTime(s.fetchedAt) else "—")
        }
        // "Narx/qarzni yangilash so'raldi" — 1-ilova server ko'ringanda bajaradi (Excel ma'lumoti baribir darhol keladi).
        bb.tvRequest.show(s.requestPending && s.snapshot != null)
        if (s.requestPending) bb.tvRequest.text = getString(R.string.home_request_pending, Fmt.time(s.requestAt))
        bb.tvError.show(s.error != null)
        bb.tvError.text = ctx.describe(s.error)
        if (snap == null) {
            bb.tvMonth.text = "—"; bb.tvLast.text = "—"; bb.tvCoverage.text = ""
            return
        }
        val c = Calendar.getInstance()
        val today = Stamp.of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        val (mf, mt) = ClientStats.Period.thisMonth(today)
        val t = ClientStats.totals(snap.ledger, mf, mt)
        bb.tvMonth.text = listOf(
            getString(R.string.sum_bought, ctx.money2(t.purchased), t.purchaseCount),
            getString(R.string.sum_paid, ctx.money2(t.paid), t.paymentCount),
            getString(R.string.sum_returned, ctx.money2(t.returned)),
        ).joinToString("\n")
        val lastBuy = snap.ledger.lastOrNull { !ClientStats.purchaseOf(it).isZero }
        val lastPay = snap.ledger.lastOrNull { !ClientStats.paymentOf(it).isZero }
        bb.tvLast.text = listOf(
            getString(R.string.last_buy, lastBuy?.let { Stamp.formatDateTime(it.stamp) + " · " + it.product + " · " + ctx.money2(ClientStats.purchaseOf(it)) } ?: "—"),
            getString(R.string.last_pay, lastPay?.let { Stamp.formatDateTime(it.stamp) + " · " + ctx.money2(ClientStats.paymentOf(it)) } ?: "—"),
        ).joinToString("\n")
        bb.tvCoverage.text = if (snap.coverage.isEmpty()) getString(R.string.home_no_reports)
        else getString(
            R.string.home_coverage2, snap.coverage.joinToString(", ") { Stamp.formatDate(it.from) + "–" + Stamp.formatDate(it.to) },
            if (snap.lastImportAt > 0) Fmt.dateTime(snap.lastImportAt) else "—",
        )
    }

    /** v3.9: bugungi o'z savdosi (Kassa bazasidan, internetsiz). */
    private fun renderToday() {
        val bb = _b ?: return
        val ctx = requireContext()
        val d = Locator.pos.state.value
        val now = uz.imsoft.shared.pos.Pos.stampOf(System.currentTimeMillis())
        val (from, to) = uz.imsoft.shared.pos.Pos.range(now, 1)
        val r = uz.imsoft.shared.pos.Pos.report(from, to, d.sales, d.payments)
        bb.tvToday.text = listOf(
            getString(R.string.ms_today_sales, r.sales, ctx.money(r.total, false)),
            getString(R.string.ms_today_cash, ctx.money(r.cashIn, false)),
            getString(R.string.ms_today_credit, ctx.money(r.onCredit, false)),
        ).joinToString("\n")
    }

    private fun online(): Boolean {
        val cm = requireContext().getSystemService(android.net.ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun renderOffline() {
        val bb = _b ?: return
        val off = runCatching { !online() }.getOrDefault(false)
        bb.tvOffline.show(off)
        if (off) {
            val at = Locator.prefs.fetchedAt
            bb.tvOffline.text = getString(R.string.ms_offline, if (at > 0) Fmt.dateTime(at) else "—")
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) { if (uz.imsoft.mijoz.Locator.prefs.link != null) uz.imsoft.mijoz.ui.tour.Tour.show(this, uz.imsoft.mijoz.ui.tour.Tour.HOME) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
