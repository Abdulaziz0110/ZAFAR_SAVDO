package com.ilxomjon.dur_novvot_dokon.ui.scan

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.barcode.BarcodeRepository
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentScanBinding
import com.ilxomjon.dur_novvot_dokon.domain.Product
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.money
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import com.ilxomjon.dur_novvot_dokon.ui.onTextChanged
import com.ilxomjon.dur_novvot_dokon.ui.sale.CartFragment
import com.ilxomjon.dur_novvot_dokon.ui.sale.ItemEditSheet
import com.ilxomjon.dur_novvot_dokon.ui.sale.PricingCtx
import com.ilxomjon.dur_novvot_dokon.ui.sale.ProductAdapter
import com.ilxomjon.dur_novvot_dokon.ui.show
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import uz.imsoft.shared.SearchIndex
import uz.imsoft.shared.SearchOptions
import uz.imsoft.shared.barcode.BarcodeResolver

/**
 * Skaner bilan savdo (v2.6). Kamera doim yoniq va TO'XTAMASDAN ketma-ket o'qiydi: har kod darhol savatchaga qo'shiladi
 * (tovush/tebranish, natija qatori yonib o'chadi). Kod topilmasa ham kamera to'xtamaydi — natija qatori "ro'yxatdan
 * tanlang" deydi, ro'yxatdagi tovarni bosish kodni shu tovarga bog'laydi (eslab qolinadi) va qo'shadi. Ro'yxatdan qo'lda
 * qo'shish ham (+/−, uzoq bosish — blok/nol) odatdagidek ishlaydi.
 * [ARG_TEST] — faqat sinov: savatchaga qo'shmaydi, ilova kodni qanday tushunganini ko'rsatadi.
 */
class ScanFragment : Fragment() {
    private var _b: FragmentScanBinding? = null
    private val b get() = _b!!
    private val testMode get() = arguments?.getBoolean(ARG_TEST) == true

    private var lastText = ""
    private var lastAt = 0L
    private var scans = 0
    private var paused = false
    private var torch = false
    private var lastAdded: ScanUi.Added? = null
    /** Topilmagan kodlar navbati (birinchisi ekranda; ro'yxatdan tovar bosilsa — unga bog'lanadi). */
    private val pending = ArrayDeque<BarcodeRepository.Scan.Unknown>()
    private var flash: ValueAnimator? = null
    private var searchJob: Job? = null
    private var inCartOnly = false
    private lateinit var adapter: ProductAdapter
    @Volatile private var index: SearchIndex<Product>? = null
    @Volatile private var indexOf: List<Product>? = null

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startCamera() else _b?.tvNoCamera?.show(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentScanBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.btnBack.setOnClickListener { navigator().pop() }
        b.btnTorch.setOnClickListener {
            torch = !torch
            runCatching { if (torch) b.scanner.setTorchOn() else b.scanner.setTorchOff() }
            b.btnTorch.isChecked = torch
        }
        b.btnPause.setOnClickListener { setPaused(!paused) }
        b.btnResult.setOnClickListener { onResultButton() }
        b.btnCart.setOnClickListener {
            val nav = navigator()
            nav.pop()
            if (!testMode) nav.push(CartFragment())
        }
        b.scanner.setStatusText("")
        // Kutubxona Java'da: getter/setter aniq chaqiriladi
        b.scanner.getBarcodeView().setDecoderFactory(
            DefaultDecoderFactory(listOf(BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.ITF)),
        )
        setupList()
        if (testMode) {
            b.searchRow.show(false); b.recycler.show(false); b.bottomBar.show(false)
            b.tvResult.setText(R.string.bc_test_scan_hint)
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else askCamera.launch(Manifest.permission.CAMERA)
        viewLifecycleOwner.lifecycleScope.launch { ServiceLocator.barcodes.load() }
    }

    // ---------------- ro'yxat (qo'lda qo'shish va topilmagan kodni bog'lash) ----------------

    private fun setupList() {
        adapter = ProductAdapter(
            onPlus = { p, price, amount -> if (!assignPending(p)) { ServiceLocator.kpi.tap(); ServiceLocator.draft.adjust(p, amount, price) } },
            onMinus = { p, price -> ServiceLocator.draft.adjust(p, -1.0, price) },
            onEdit = { p, focusPrice -> if (!assignPending(p)) ItemEditSheet.show(childFragmentManager, p.id, focusPrice, false) },
            onZero = { p -> ServiceLocator.draft.state.value.lineFor(p.id)?.let { l -> ServiceLocator.draft.adjust(p, -l.qty, l.price) } },
        )
        adapter.haptics = ServiceLocator.appPrefs.fHaptics
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter
        b.recycler.addItemDecoration(
            com.google.android.material.divider.MaterialDividerItemDecoration(requireContext(), androidx.recyclerview.widget.RecyclerView.VERTICAL).apply {
                dividerInsetStart = (16 * resources.displayMetrics.density).toInt()
                isLastItemDecorated = false
            },
        )
        b.edtSearch.onTextChanged { refreshList() }
        b.chipInCart.setOnCheckedChangeListener { _, on -> inCartOnly = on; refreshList() }
        launchOnStarted {
            combine(ServiceLocator.draft.state, ServiceLocator.catalog.state) { d, c -> d to c }.collect { (d, c) ->
                val bb = _b ?: return@collect
                adapter.setPricing(PricingCtx(d.priceType, d.currency, c.rate), false)
                adapter.setCart(d.lines.associateBy { it.productId })
                bb.tvCartCount.text = getString(R.string.sale_items_count, d.itemCount)
                bb.tvTotal.text = requireContext().money(d.total, d.currency)
                bb.btnCart.isEnabled = d.itemCount > 0
                bb.chipInCart.text = getString(R.string.scan_in_cart_chip_n, d.lines.size)
                if (inCartOnly || indexOf !== c.products) refreshList()
            }
        }
    }

    private fun refreshList() {
        searchJob?.cancel()
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            val q = _b?.edtSearch?.text?.toString().orEmpty()
            if (q.isNotBlank()) delay(120)
            val all = ServiceLocator.catalog.state.value.products
            val idx = index.takeIf { indexOf === all } ?: SearchIndex(all, { it.name }, { it.id }).also { index = it; indexOf = all }
            val inCart = ServiceLocator.draft.state.value.lines.mapTo(HashSet()) { it.productId }
            val prefs = ServiceLocator.appPrefs
            val list = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val base = if (q.isBlank()) all.sortedBy { it.searchKey } else idx.search(q, SearchOptions(prefs.fFuzzySearch, prefs.fLayoutFix)).map { it.item }
                if (inCartOnly) base.filter { it.id in inCart } else base
            }
            val bb = _b ?: return@launch
            adapter.setProducts(list)
            bb.tvEmpty.show(list.isEmpty())
        }
    }

    // ---------------- kamera ----------------

    private val callback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult) {
            val text = result.getText()?.trim().orEmpty()
            if (text.isEmpty()) return
            val now = System.currentTimeMillis()
            // Bir kod kamera oldida turganda qayta-qayta qo'shilmasin; boshqa kod — deyarli darhol
            if (text == lastText && now - lastAt < SAME_CODE_MS) { lastAt = now; return }
            if (now - lastAt < ANY_CODE_MS) return
            lastText = text
            lastAt = now
            onScanned(text)
        }

        override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>) = Unit
    }

    private fun startCamera() {
        val bb = _b ?: return
        bb.tvNoCamera.show(false)
        bb.scanner.decodeContinuous(callback)
        if (isResumed && !paused) bb.scanner.resume()
    }

    private fun setPaused(p: Boolean) {
        paused = p
        val bb = _b ?: return
        if (p) bb.scanner.pause() else if (hasCamera()) bb.scanner.resume()
        bb.btnPause.setIconResource(if (p) R.drawable.ic_scan else R.drawable.ic_pause)
        bb.tvLive.setText(if (p) R.string.scan_paused else R.string.scan_live)
    }

    private fun hasCamera() = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun onScanned(text: String) {
        val bb = _b ?: return
        scans++
        bb.tvCount.show(true)
        bb.tvCount.text = scans.toString()
        if (testMode) {
            viewLifecycleOwner.lifecycleScope.launch {
                val t = BarcodeTestUi.explain(requireContext(), text)
                ScanUi.feedback(requireContext(), true)
                showResult(Mode.INFO, text, t, null)
            }
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            when (val s = ServiceLocator.barcodes.resolve(text)) {
                is BarcodeRepository.Scan.Found -> {
                    ScanUi.feedback(requireContext(), true)
                    added(ScanUi.add(s.product, s.qty))
                }
                is BarcodeRepository.Scan.Missing -> {
                    ScanUi.feedback(requireContext(), false)
                    enqueue(BarcodeRepository.Scan.Unknown(BarcodeResolver.Result.Unknown(s.raw, null, null), null))
                }
                is BarcodeRepository.Scan.Unknown -> {
                    ScanUi.feedback(requireContext(), false)
                    enqueue(s)
                }
            }
        }
    }

    private fun added(a: ScanUi.Added) {
        lastAdded = a
        showResult(Mode.OK, a.product.name, ScanUi.describe(requireContext(), a), getString(R.string.scan_undo))
        if (pending.isNotEmpty()) mainHandlerPostShowPending()
    }

    private fun enqueue(u: BarcodeRepository.Scan.Unknown) {
        if (pending.any { it.result.raw == u.result.raw }) return
        pending.addLast(u)
        showPending()
    }

    /** Topilmagan kodni ekranga chiqaradi: ro'yxatdan tovar bosilsa — shunga bog'lanadi. */
    private fun showPending() {
        val u = pending.firstOrNull() ?: return
        val r = u.result
        val more = if (pending.size > 1) " (+" + (pending.size - 1) + ")" else ""
        val weight = r.qty?.let { " · " + getString(R.string.scan_weight, Money.qtyDisplay(it)) }.orEmpty()
        val sub = if (u.suggestion != null) getString(R.string.scan_pending_suggest, u.suggestion.name) else getString(R.string.scan_pending_pick)
        showResult(Mode.UNKNOWN, getString(R.string.scan_pending_title, r.raw) + more, sub + weight, getString(if (u.suggestion != null) R.string.scan_yes else R.string.scan_skip))
        // Ro'yxat tepasiga — tanlash oson bo'lsin
        _b?.recycler?.scrollToPosition(0)
    }

    private fun mainHandlerPostShowPending() {
        _b?.root?.postDelayed({ if (pending.isNotEmpty() && _b != null) showPending() }, 1600)
    }

    /** Ro'yxatdan bosilgan tovar topilmagan kodga bog'lanadi. @return true — bog'landi (oddiy qo'shish bajarilmaydi). */
    private fun assignPending(p: Product): Boolean {
        val u = pending.removeFirstOrNull() ?: return false
        viewLifecycleOwner.lifecycleScope.launch {
            ServiceLocator.barcodes.learn(u.result, p.id)
            ScanUi.feedback(requireContext(), true)
            added(ScanUi.add(p, u.result.qty))
            _b?.edtSearch?.setText("")
            if (pending.isNotEmpty()) mainHandlerPostShowPending()
        }
        return true
    }

    private fun onResultButton() {
        val u = pending.firstOrNull()
        if (u != null) {
            if (u.suggestion != null) assignPending(u.suggestion)
            else { pending.removeFirst(); if (pending.isNotEmpty()) showPending() else showResult(Mode.IDLE, getString(R.string.scan_hint), null, null) }
            return
        }
        lastAdded?.let { ScanUi.undo(it) }
        lastAdded = null
        showResult(Mode.IDLE, getString(R.string.scan_undone), null, null)
    }

    // ---------------- natija qatori ----------------

    private enum class Mode { IDLE, OK, UNKNOWN, INFO }

    private fun showResult(mode: Mode, title: String, sub: String?, button: String?) {
        val bb = _b ?: return
        bb.tvResult.text = title
        bb.tvResultSub.text = sub.orEmpty()
        bb.tvResultSub.show(!sub.isNullOrEmpty())
        bb.btnResult.text = button.orEmpty()
        bb.btnResult.show(button != null)
        bb.tvIcon.text = when (mode) { Mode.OK -> "✓"; Mode.UNKNOWN -> "?"; Mode.INFO -> "ℹ"; Mode.IDLE -> "▦" }
        val base = MaterialColors.getColor(bb.resultCard, com.google.android.material.R.attr.colorSurfaceContainerHigh)
        val target = when (mode) {
            Mode.OK -> MaterialColors.getColor(bb.resultCard, com.google.android.material.R.attr.colorPrimaryContainer)
            Mode.UNKNOWN -> MaterialColors.getColor(bb.resultCard, com.google.android.material.R.attr.colorErrorContainer)
            else -> base
        }
        val fg = when (mode) {
            Mode.OK -> MaterialColors.getColor(bb.resultCard, com.google.android.material.R.attr.colorOnPrimaryContainer)
            Mode.UNKNOWN -> MaterialColors.getColor(bb.resultCard, com.google.android.material.R.attr.colorOnErrorContainer)
            else -> MaterialColors.getColor(bb.resultCard, com.google.android.material.R.attr.colorOnSurface)
        }
        bb.tvResult.setTextColor(fg); bb.tvIcon.setTextColor(fg)
        flash?.cancel()
        if (mode == Mode.OK) {
            // Qo'shildi: yashil yonib, sekin odatiy rangga qaytadi — keyingi skanerni ko'rish oson
            flash = ValueAnimator.ofObject(ArgbEvaluator(), target, base).apply {
                duration = 900
                startDelay = 350
                addUpdateListener { v -> _b?.resultCard?.setCardBackgroundColor(v.animatedValue as Int) }
                start()
            }
            bb.resultCard.setCardBackgroundColor(target)
        } else bb.resultCard.setCardBackgroundColor(target)
    }

    // ---------------- hayot sikli ----------------

    override fun onResume() {
        super.onResume()
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (!paused && hasCamera()) _b?.scanner?.resume()
        refreshList()
    }

    override fun onPause() {
        super.onPause()
        _b?.scanner?.pause()
        if (!ServiceLocator.appPrefs.keepScreenOn) requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroyView() {
        flash?.cancel()
        super.onDestroyView()
        _b = null
    }

    companion object {
        private const val ARG_TEST = "test"
        /** Bir kod shuncha vaqt ichida yana ko'rinsa — o'sha dona (qayta qo'shilmaydi). */
        private const val SAME_CODE_MS = 1200L
        /** Har qanday ikki o'qish orasidagi eng kam vaqt. */
        private const val ANY_CODE_MS = 250L
        fun test() = ScanFragment().apply { arguments = Bundle().apply { putBoolean(ARG_TEST, true) } }
    }
}
