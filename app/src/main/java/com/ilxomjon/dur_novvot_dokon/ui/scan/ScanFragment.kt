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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    /** Kod yozish rejimi: skanerlangan kod ro'yxatdan tanlangan tovarga bog'lanadi (savatchaga qo'shilmaydi). */
    private val linkMode get() = arguments?.getBoolean(ARG_LINK) == true || batchMode
    /** v3.12 "Shtrix kod → 1C": kod + tanlangan tovar ro'yxatga (1C'ga bitta tugma bilan, BarcodeBatchFragment). */
    private val batchMode get() = arguments?.getBoolean(ARG_BATCH) == true
    /** Narx yorlig'i rejimi: skanerlangan tovar yorliqlar ro'yxatiga qo'shiladi (savatchaga emas). */
    private val labelMode get() = arguments?.getBoolean(ARG_LABEL) == true
    private var lastLabel: String? = null
    private var linkedNow = 0

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
            if (batchMode) { navigator().pop(); return@setOnClickListener }
            if (linkMode) { exportCsv(); return@setOnClickListener }
            if (labelMode) { navigator().pop(); return@setOnClickListener }
            val nav = navigator()
            nav.pop()
            if (!testMode) nav.push(CartFragment())
        }
        b.scanner.setStatusText("")
        // Kutubxona Java'da: getter/setter aniq chaqiriladi
        b.scanner.getBarcodeView().setDecoderFactory(
            DefaultDecoderFactory(listOf(BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.ITF)),
        )
        tuneCamera()
        setupLevel()
        setupList()
        if (testMode) {
            b.searchRow.show(false); b.recycler.show(false); b.bottomBar.show(false)
            b.tvResult.setText(R.string.bc_test_scan_hint)
        }
        if (linkMode) {
            b.chipInCart.show(false)
            b.tvLive.setText(R.string.link_live)
            b.tvResult.setText(R.string.link_hint)
            b.btnCart.setText(R.string.link_export)
            b.btnCart.setIconResource(R.drawable.ic_share)
            launchOnStarted { ServiceLocator.barcodes.state.collect { renderLinkBar() } }
        }
        if (batchMode) {
            b.tvLive.setText(R.string.bb_live)
            b.tvResult.setText(R.string.bb_scan_hint)
            b.btnCart.setText(R.string.bb_to_list)
            b.btnCart.setIconResource(R.drawable.ic_list)
            launchOnStarted { ServiceLocator.barcodeBatch.items.collect { renderLinkBar() } }
            viewLifecycleOwner.lifecycleScope.launch { ServiceLocator.barcodeBatch.load() }
        }
        if (labelMode) {
            b.tvLive.setText(R.string.lb_scan_live)
            b.tvResult.setText(R.string.lb_scan_hint)
            b.btnCart.setText(R.string.lb_scan_done)
            b.btnCart.setIconResource(R.drawable.ic_label)
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else askCamera.launch(Manifest.permission.CAMERA)
        viewLifecycleOwner.lifecycleScope.launch { ServiceLocator.barcodes.load() }
    }

    // ---------------- ro'yxat (qo'lda qo'shish va topilmagan kodni bog'lash) ----------------

    private fun setupList() {
        adapter = ProductAdapter(
            onPlus = { p, price, amount ->
                if (!assignPending(p)) {
                    if (linkMode) showResult(Mode.INFO, getString(R.string.link_scan_first), null, null)
                    else if (labelMode) ServiceLocator.labels.add(p.id, 1)
                    else { ServiceLocator.kpi.tap(); ServiceLocator.draft.adjust(p, amount, price) }
                }
            },
            onMinus = { p, price ->
                if (labelMode) ServiceLocator.labels.setCopies(p.id, ServiceLocator.labels.copies(p.id) - 1)
                else if (!linkMode) ServiceLocator.draft.adjust(p, -1.0, price)
            },
            onEdit = { p, focusPrice ->
                if (!assignPending(p)) {
                    if (linkMode) showResult(Mode.INFO, getString(R.string.link_scan_first), null, null)
                    else if (labelMode) ServiceLocator.labels.add(p.id, 1)
                    else ItemEditSheet.show(childFragmentManager, p.id, focusPrice, false)
                }
            },
            onZero = { p ->
                if (labelMode) ServiceLocator.labels.remove(p.id)
                else if (!linkMode) ServiceLocator.draft.state.value.lineFor(p.id)?.let { l -> ServiceLocator.draft.adjust(p, -l.qty, l.price) }
            },
        )
        adapter.haptics = ServiceLocator.appPrefs.fHaptics
        if (labelMode) adapter.priceOverride = { p -> ServiceLocator.labels.let { r -> if (r.state.value.price1c.isNotEmpty()) r.priceOf(p, null) else null } }
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
        if (labelMode) {
            launchOnStarted {
                combine(ServiceLocator.labels.state, ServiceLocator.catalog.state) { l, c -> l to c }.collect { (l, c) ->
                    val bb = _b ?: return@collect
                    val repo = ServiceLocator.labels
                    adapter.setPricing(PricingCtx(l.priceType, com.ilxomjon.dur_novvot_dokon.domain.Currency.SOM, c.rate), false)
                    adapter.setCart(
                        l.items.mapNotNull { i ->
                            val p = c.productsById[i.productId] ?: return@mapNotNull null
                            i.productId to com.ilxomjon.dur_novvot_dokon.domain.CartLine(0L, p.id, p.name, 0.0, i.copies.toDouble(), repo.priceOf(p, i))
                        }.toMap(),
                    )
                    bb.tvCartCount.text = getString(R.string.lb_scan_count, l.total)
                    bb.tvTotal.text = getString(R.string.lb_scan_products, l.items.size)
                    bb.btnCart.isEnabled = true
                    bb.chipInCart.text = getString(R.string.lb_filter_selected_n, l.items.size)
                    if (inCartOnly || indexOf !== c.products) refreshList()
                }
            }
            return
        }
        launchOnStarted {
            combine(ServiceLocator.draft.state, ServiceLocator.catalog.state) { d, c -> d to c }.collect { (d, c) ->
                val bb = _b ?: return@collect
                adapter.setPricing(PricingCtx(d.priceType, d.currency, c.rate, d.level), false)
                if (linkMode) return@collect
                renderLevel(d)
                adapter.setCart(d.lines.associateBy { it.productId })
                bb.tvCartCount.text = getString(R.string.sale_items_count, d.itemCount)
                bb.tvTotal.text = requireContext().money(d.total, d.currency)
                bb.btnCart.isEnabled = d.itemCount > 0
                bb.chipInCart.text = getString(R.string.scan_in_cart_chip_n, d.lines.size)
                if (inCartOnly || indexOf !== c.products) refreshList()
            }
        }
    }

    // ---------------- narx (1..4) — oddiy savdodagidek ----------------

    private var levelRendering = false

    private fun setupLevel() {
        val show = !testMode && !linkMode && !labelMode
        b.tgLevel.show(show)
        if (!show) return
        b.tgLevel.addOnButtonCheckedListener { _, id, checked ->
            if (!checked || levelRendering) return@addOnButtonCheckedListener
            val level = when (id) { R.id.lvl1 -> 1; R.id.lvl2 -> 2; R.id.lvl3 -> 3; else -> 4 }
            val d = ServiceLocator.draft.state.value
            if (level == d.level) return@addOnButtonCheckedListener
            if (d.lines.isEmpty()) ServiceLocator.draft.setPriceLevel(level)
            else MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.reprice_title).setMessage(R.string.reprice_message)
                .setPositiveButton(R.string.ok) { _, _ -> ServiceLocator.draft.setPriceLevel(level) }
                .setNegativeButton(R.string.cancel) { _, _ -> renderLevel(ServiceLocator.draft.state.value) }
                .setOnCancelListener { renderLevel(ServiceLocator.draft.state.value) }
                .show()
        }
    }

    private fun renderLevel(d: com.ilxomjon.dur_novvot_dokon.domain.DraftState) {
        val bb = _b ?: return
        if (testMode || linkMode || labelMode) return
        levelRendering = true
        bb.tgLevel.check(when (d.level) { 1 -> R.id.lvl1; 2 -> R.id.lvl2; 3 -> R.id.lvl3; else -> R.id.lvl4 })
        // 2-filial mijozlariga narx doim 4
        val st = d.store
        val fixed4 = st != null && ServiceLocator.shareStore.clients().any { it.storeId.isNotEmpty() && it.storeId == st.id }
        for (i in 0 until bb.tgLevel.childCount) bb.tgLevel.getChildAt(i).isEnabled = !fixed4
        levelRendering = false
        if (fixed4 && d.level != 4) bb.root.post { ServiceLocator.draft.setPriceLevel(4) }
    }

    private fun refreshList() {
        searchJob?.cancel()
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            val q = _b?.edtSearch?.text?.toString().orEmpty()
            if (q.isNotBlank()) delay(120)
            val all = ServiceLocator.catalog.state.value.products
            val idx = index.takeIf { indexOf === all } ?: SearchIndex(all, { it.name }, { it.id }).also { index = it; indexOf = all }
            val inCart = if (labelMode) ServiceLocator.labels.state.value.items.mapTo(HashSet()) { it.productId }
            else ServiceLocator.draft.state.value.lines.mapTo(HashSet()) { it.productId }
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

    /**
     * Fokus: kutubxonaning standart rejimi (AUTO) har 2 soniyada qayta fokuslaydi — rasm ko'p vaqt xira turadi.
     * Uzluksiz fokus (CONTINUOUS) + markazga o'lchash: tovar yaqinlashganda kamera o'zi va silliq tiniqlashadi.
     */
    private fun tuneCamera() {
        val bv = b.scanner.getBarcodeView()
        val st = bv.getCameraSettings()
        st.setAutoFocusEnabled(true)
        st.setContinuousFocusEnabled(true)
        st.setFocusMode(com.journeyapps.barcodescanner.camera.CameraSettings.FocusMode.CONTINUOUS)
        st.setMeteringEnabled(true)
        bv.setCameraSettings(st)
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
                    if (batchMode) {
                        rebind = null
                        showResult(Mode.INFO, getString(R.string.link_known, s.product.name), s.raw, null)
                    } else if (linkMode) {
                        // Allaqachon bog'langan: ko'rsatamiz; oddiy kod bo'lsa — "O'zgartirish" bilan boshqa tovarga bog'lash mumkin
                        rebind = if (ServiceLocator.barcodes.scale.parse(text) == null) s.raw else null
                        showResult(Mode.INFO, getString(R.string.link_known, s.product.name), s.raw, if (rebind != null) getString(R.string.link_change) else null)
                    } else if (labelMode) labelAdded(s.product)
                    else added(ScanUi.add(s.product, s.qty))
                }
                is BarcodeRepository.Scan.Missing -> {
                    ScanUi.feedback(requireContext(), false)
                    enqueue(BarcodeRepository.Scan.Unknown(BarcodeResolver.Result.Unknown(s.raw, null, null), null))
                }
                is BarcodeRepository.Scan.Unknown -> {
                    val inList = if (batchMode) ServiceLocator.barcodeBatch.items.value.firstOrNull { it.code == com.ilxomjon.dur_novvot_dokon.data.barcode.BarcodeBatch.clean(s.result.raw) } else null
                    if (inList != null) {
                        ScanUi.feedback(requireContext(), true)
                        showResult(Mode.INFO, getString(R.string.bb_already, inList.name), s.result.raw, null)
                    } else {
                        ScanUi.feedback(requireContext(), false)
                        enqueue(s)
                    }
                }
            }
        }
    }

    private fun added(a: ScanUi.Added) {
        lastAdded = a
        showResult(Mode.OK, a.product.name, ScanUi.describe(requireContext(), a), getString(R.string.scan_undo))
        if (pending.isNotEmpty()) mainHandlerPostShowPending()
    }

    /** Yorliq rejimi: tovar ro'yxatga +1 yorliq. */
    private fun labelAdded(p: Product) {
        val repo = ServiceLocator.labels
        repo.add(p.id, 1)
        lastLabel = p.id
        val plu = repo.pluFor(p.id)
        val sub = getString(R.string.lb_scan_added, repo.copies(p.id)) + (plu?.let { " · " + getString(R.string.lb_note_plu, it) }.orEmpty())
        showResult(Mode.OK, p.name, sub, getString(R.string.scan_undo))
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
        if (batchMode) {
            viewLifecycleOwner.lifecycleScope.launch {
                ServiceLocator.barcodeBatch.put(u.result.raw, p.id, p.name)
                ScanUi.feedback(requireContext(), true)
                showResult(Mode.OK, getString(R.string.bb_added, p.name), u.result.raw, null)
                _b?.edtSearch?.setText("")
                if (pending.isNotEmpty()) mainHandlerPostShowPending()
            }
            return true
        }
        viewLifecycleOwner.lifecycleScope.launch {
            ServiceLocator.barcodes.learn(u.result, p.id)
            ScanUi.feedback(requireContext(), true)
            if (linkMode) {
                linkedNow++
                showResult(Mode.OK, getString(R.string.link_done, p.name), u.result.raw, null)
                _b?.edtSearch?.setText("")
                renderLinkBar()
                if (pending.isNotEmpty()) mainHandlerPostShowPending()
                return@launch
            }
            if (labelMode) labelAdded(p) else added(ScanUi.add(p, u.result.qty))
            _b?.edtSearch?.setText("")
            if (pending.isNotEmpty()) mainHandlerPostShowPending()
        }
        return true
    }

    private fun onResultButton() {
        val rb = rebind
        if (linkMode && pending.isEmpty() && rb != null) {
            rebind = null
            enqueue(BarcodeRepository.Scan.Unknown(BarcodeResolver.Result.Unknown(rb, null, null), null))
            return
        }
        val u = pending.firstOrNull()
        if (u != null) {
            if (u.suggestion != null) assignPending(u.suggestion)
            else { pending.removeAt(0); if (pending.isNotEmpty()) showPending() else showResult(Mode.IDLE, getString(R.string.scan_hint), null, null) }
            return
        }
        if (labelMode) {
            lastLabel?.let { id -> ServiceLocator.labels.setCopies(id, ServiceLocator.labels.copies(id) - 1) }
            lastLabel = null
        }
        lastAdded?.let { ScanUi.undo(it) }
        lastAdded = null
        showResult(Mode.IDLE, getString(R.string.scan_undone), null, null)
    }

    // ---------------- kod yozish rejimi ----------------

    private var rebind: String? = null

    private fun renderLinkBar() {
        val bb = _b ?: return
        if (batchMode) {
            val n = ServiceLocator.barcodeBatch.items.value.size
            bb.tvCartCount.text = getString(R.string.bb_count, n)
            bb.tvTotal.text = getString(R.string.bb_bar_hint)
            bb.btnCart.isEnabled = true
            return
        }
        val st = ServiceLocator.barcodes.state.value
        bb.tvCartCount.text = getString(R.string.link_session, linkedNow)
        bb.tvTotal.text = getString(R.string.link_total, st.learned + st.scaleLearned)
        bb.btnCart.isEnabled = st.learned + st.scaleLearned > 0
    }

    /** Telefonda yozilgan kodlarni Excel (CSV) qilib ulashish — 1C'ga kiritish uchun. */
    private fun exportCsv() {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val f = ServiceLocator.barcodes.exportLearnedCsv(java.io.File(ctx.cacheDir, "share"))
            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/csv"; putExtra(android.content.Intent.EXTRA_STREAM, uri); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, getString(R.string.link_export)))
        }
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
        private const val ARG_LINK = "link"
        private const val ARG_LABEL = "label"
        /** Narx yorliqlari uchun tovar yig'ish (skanerlab). */
        fun labels() = ScanFragment().apply { arguments = Bundle().apply { putBoolean(ARG_LABEL, true) } }
        fun test() = ScanFragment().apply { arguments = Bundle().apply { putBoolean(ARG_TEST, true) } }
        /** Do'kon bo'ylab yurib, bazada yo'q shtrix kodlarni tovarlarga bog'lash. */
        fun link() = ScanFragment().apply { arguments = Bundle().apply { putBoolean(ARG_LINK, true) } }
        private const val ARG_BATCH = "batch"
        fun batch() = ScanFragment().apply { arguments = Bundle().apply { putBoolean(ARG_BATCH, true) } }
    }
}
