package com.ilxomjon.dur_novvot_dokon.ui.labels

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.labels.LabelState
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentLabelsBinding
import com.ilxomjon.dur_novvot_dokon.domain.CartLine
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.PriceType
import com.ilxomjon.dur_novvot_dokon.domain.Product
import com.ilxomjon.dur_novvot_dokon.ui.describeError
import com.ilxomjon.dur_novvot_dokon.ui.formatDateTime
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import com.ilxomjon.dur_novvot_dokon.ui.onTextChanged
import com.ilxomjon.dur_novvot_dokon.ui.sale.PricingCtx
import com.ilxomjon.dur_novvot_dokon.ui.sale.ProductAdapter
import com.ilxomjon.dur_novvot_dokon.ui.scan.ScanFragment
import com.ilxomjon.dur_novvot_dokon.ui.show
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.imsoft.shared.SearchIndex
import uz.imsoft.shared.SearchOptions
import uz.imsoft.shared.barcode.BarcodeEncode
import uz.imsoft.shared.label.LabelKind
import uz.imsoft.shared.label.PluImport

/**
 * Narx yorliqlari: tovarlarni (qidirib, skanerlab) ro'yxatga yig'ish, nechta chiqishini belgilash va ikki tugma —
 * "PLU narx" (tarozi tovarlari) va "Shtrixli narx". Yorliq kompyuterdagi Xprinter'dan chiqadi (Wi-Fi yoki internet orqali).
 */
class LabelsFragment : Fragment() {
    private var _b: FragmentLabelsBinding? = null
    private val b get() = _b!!
    private val repo get() = ServiceLocator.labels
    private lateinit var adapter: ProductAdapter
    private var filter = Filter.ALL
    private var searchJob: Job? = null
    @Volatile private var index: SearchIndex<Product>? = null
    @Volatile private var indexOf: List<Product>? = null
    /** Shtrix kodi chizsa bo'ladigan tovarlar (filtr va izoh uchun). */
    private var withBarcode: Set<String> = emptySet()

    private enum class Filter { ALL, SELECTED, PLU, BARCODE }

    private val openPlu = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) importPlu(uri) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentLabelsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.toolbar.setNavigationOnClickListener { navigator().pop() }
        b.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_printer -> { LabelPrinterSheet.show(childFragmentManager); true }
                R.id.action_import_plu -> { openPlu.launch(arrayOf("*/*")); true }
                R.id.action_codes -> { showCodes(); true }
                R.id.action_clear -> {
                    MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.lb_clear).setMessage(R.string.lb_clear_msg)
                        .setPositiveButton(R.string.ok) { _, _ -> repo.clear() }.setNegativeButton(R.string.cancel, null).show()
                    true
                }
                else -> false
            }
        }
        b.rowPrinter.setOnClickListener { LabelPrinterSheet.show(childFragmentManager) }
        b.togglePrice.check(if (repo.state.value.priceType == PriceType.WHOLESALE) R.id.btnWholesale else R.id.btnCash)
        b.togglePrice.addOnButtonCheckedListener { _, id, checked -> if (checked) repo.setPriceType(if (id == R.id.btnWholesale) PriceType.WHOLESALE else PriceType.CASH) }
        b.btnScan.setOnClickListener { navigator().push(ScanFragment.labels()) }
        b.btnPlu.setOnClickListener { LabelPrintSheet.show(childFragmentManager, LabelKind.PLU) }
        b.btnBarcode.setOnClickListener { LabelPrintSheet.show(childFragmentManager, LabelKind.BARCODE) }
        b.chips.setOnCheckedStateChangeListener { _, ids ->
            filter = when (ids.firstOrNull()) { R.id.chipSelected -> Filter.SELECTED; R.id.chipWeight -> Filter.PLU; R.id.chipBarcode -> Filter.BARCODE; else -> Filter.ALL }
            refreshList()
        }
        setupList()
        launchOnStarted {
            combine(repo.state, ServiceLocator.catalog.state) { s, c -> s to c }.collect { (s, c) -> render(s, c.rate) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            // Kompyuterdan kelgan kodlar (1C: PLU, shtrix) — navbatda bo'lsa olinadi, katalog yangilangan bo'lsa qayta bog'lanadi
            runCatching { repo.relinkIfNeeded() }
            runCatching { ServiceLocator.share.processInbox() }
            loadBarcodeSet()
            runCatching { repo.refreshInfo() }
        }
    }

    private suspend fun loadBarcodeSet() {
        val all = ServiceLocator.catalog.state.value.products
        val bc = ServiceLocator.barcodes
        withBarcode = withContext(Dispatchers.Default) {
            all.filter { p -> bc.barcodesFor(p.id).any { BarcodeEncode.forShopCode(it) != null } }.mapTo(HashSet()) { it.id }
        }
        refreshNotes()
        refreshList()
    }

    private fun setupList() {
        adapter = ProductAdapter(
            onPlus = { p, _, amount -> repo.add(p.id, amount.toInt().coerceAtLeast(1)) },
            onMinus = { p, _ -> repo.setCopies(p.id, repo.copies(p.id) - 1) },
            onEdit = { p, _ -> editItem(p) },
            onZero = { p -> repo.remove(p.id) },
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
    }

    private fun render(s: LabelState, rate: Double) {
        val bb = _b ?: return
        val byId = ServiceLocator.catalog.state.value.productsById
        adapter.setPricing(PricingCtx(s.priceType, Currency.SOM, rate), false)
        adapter.setCart(
            s.items.mapNotNull { i ->
                val p = byId[i.productId] ?: return@mapNotNull null
                i.productId to CartLine(0L, p.id, p.name, 0.0, i.copies.toDouble(), repo.priceOf(p, i))
            }.toMap(),
        )
        bb.tvCount.text = if (s.items.isEmpty()) getString(R.string.lb_count_none) else getString(R.string.lb_count, s.total, s.items.size)
        bb.btnPlu.isEnabled = s.items.isNotEmpty()
        bb.btnBarcode.isEnabled = s.items.isNotEmpty()
        bb.chipSelected.text = getString(R.string.lb_filter_selected_n, s.items.size)
        bb.tvPrinter.text = printerLine(s)
        refreshNotes()
        if (filter == Filter.SELECTED) refreshList()
    }

    private fun printerLine(s: LabelState): String {
        val i = s.info
        return when {
            s.host.isNotBlank() && i != null && s.infoError.isEmpty() -> getString(R.string.lb_printer_lan, i.printer.ifBlank { "?" }, i.host.ifBlank { s.host })
            s.host.isNotBlank() && s.infoError.isNotEmpty() -> getString(R.string.lb_printer_offline, s.host) + if (s.cloud && ServiceLocator.shareStore.isCloudReady) " · " + getString(R.string.lb_printer_cloud_fallback) else ""
            s.host.isBlank() && s.cloud && ServiceLocator.shareStore.isCloudReady -> getString(R.string.lb_printer_cloud, i?.printer?.ifBlank { null } ?: "—")
            else -> getString(R.string.lb_printer_none)
        }
    }

    private fun refreshNotes() {
        if (_b == null) return
        val m = HashMap<String, String>()
        val s = repo.state.value
        val ids = ServiceLocator.catalog.state.value.products.map { it.id }
        for (id in ids) {
            val parts = ArrayList<String>(2)
            if (id in withBarcode) parts += getString(R.string.lb_note_barcode)
            if (parts.isNotEmpty()) m[id] = parts.joinToString(" · ")
        }
        s.items.forEach { if (it.price != null) m[it.productId] = (m[it.productId]?.let { x -> "$x · " } ?: "") + getString(R.string.lb_note_manual_price) }
        adapter.setNotes(m)
    }

    private fun refreshList() {
        searchJob?.cancel()
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            val q = _b?.edtSearch?.text?.toString().orEmpty()
            if (q.isNotBlank()) delay(120)
            val all = ServiceLocator.catalog.state.value.products
            val idx = index.takeIf { indexOf === all } ?: SearchIndex(all, { it.name }, { it.id }).also { index = it; indexOf = all }
            val selected = repo.state.value.items.mapTo(HashSet()) { it.productId }
            val prefs = ServiceLocator.appPrefs
            val f = filter
            val wb = withBarcode
            val list = withContext(Dispatchers.Default) {
                val base = if (q.isBlank()) all.sortedBy { it.searchKey } else idx.search(q, SearchOptions(prefs.fFuzzySearch, prefs.fLayoutFix)).map { it.item }
                when (f) {
                    Filter.ALL -> base
                    Filter.SELECTED -> base.filter { it.id in selected }
                    Filter.PLU -> base.filter { repo.pluFor(it.id) != null }
                    Filter.BARCODE -> base.filter { it.id in wb }
                }
            }
            val bb = _b ?: return@launch
            adapter.setProducts(list)
            bb.tvEmpty.show(list.isEmpty())
            bb.tvEmpty.setText(if (f == Filter.SELECTED) R.string.lb_empty_selected else R.string.sale_empty_products)
        }
    }

    /** Tovar: nechta yorliq, narx (qo'lda) va PLU raqami. */
    private fun editItem(p: Product) {
        val ctx = requireContext()
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 0) }
        fun field(hint: Int, type: Int, value: String, helper: String? = null): TextInputEditText {
            val til = TextInputLayout(ctx).apply { this.hint = getString(hint); helper?.let { helperText = it } }
            val e = TextInputEditText(til.context).apply { inputType = type; setText(value); isSingleLine = true }
            til.addView(e); box.addView(til)
            return e
        }
        val item = repo.state.value.items.firstOrNull { it.productId == p.id }
        val eCopies = field(R.string.lb_copies, InputType.TYPE_CLASS_NUMBER, (item?.copies ?: 1).toString())
        val autoPrice = repo.priceOf(p, item?.copy(price = null))
        val ePrice = field(
            R.string.lb_price_manual, InputType.TYPE_CLASS_NUMBER, item?.price?.let { Money.plain(it) }.orEmpty(),
            getString(R.string.lb_price_auto, uz.imsoft.shared.label.LabelPrice.som(autoPrice)),
        )
        val ePlu = field(R.string.lb_plu, InputType.TYPE_CLASS_NUMBER, repo.pluFor(p.id).orEmpty(), getString(R.string.lb_plu_hint))
        val d = MaterialAlertDialogBuilder(ctx).setTitle(p.name).setView(box)
            .setPositiveButton(R.string.save) { _, _ ->
                val n = eCopies.text?.toString()?.toIntOrNull() ?: 0
                repo.setCopies(p.id, n)
                if (n > 0) repo.setPrice(p.id, ePrice.text?.toString()?.filter { it.isDigit() }?.toDoubleOrNull())
                repo.setPlu(p.id, ePlu.text?.toString())
                refreshNotes()
            }
            .setNegativeButton(R.string.cancel, null)
        if (item != null) d.setNeutralButton(R.string.lb_remove) { _, _ -> repo.remove(p.id) }
        d.show()
    }

    // ---------------- PLU import ----------------

    private fun importPlu(uri: Uri) {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val (bytes, name) = withContext(Dispatchers.IO) {
                    val n = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                        ?: uri.lastPathSegment.orEmpty()
                    (ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw java.io.IOException("open")) to n
                }
                val pv = repo.previewPlu(bytes, name)
                val msg = buildString {
                    append(getString(R.string.lb_plu_pv, pv.rows.size, pv.map.size))
                    if (pv.missing.isNotEmpty()) append("\n\n").append(getString(R.string.lb_plu_pv_missing, pv.missing.size))
                        .append('\n').append(pv.missing.take(6).joinToString("\n") { "• " + it.name + " — " + it.plu })
                }
                MaterialAlertDialogBuilder(ctx).setTitle(R.string.lb_import_plu).setMessage(msg)
                    .setPositiveButton(R.string.save) { _, _ -> repo.commitPlu(pv); refreshNotes(); snack(getString(R.string.lb_plu_saved, pv.map.size)) }
                    .setNegativeButton(R.string.cancel, null).show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: PluImport.NotPlu) {
                MaterialAlertDialogBuilder(ctx).setTitle(R.string.lb_import_plu).setMessage(R.string.lb_plu_not_found).setPositiveButton(R.string.close, null).show()
            } catch (e: Exception) {
                snack(getString(R.string.share_import_fail) + "\n" + (e.message ?: e.javaClass.simpleName).take(120))
            }
        }
    }

    /** Ulanadigan (Bluetooth/USB) skaner: kod topilsa tovar ro'yxatga +1 yorliq. */
    fun onHardwareScan(text: String): Boolean {
        if (_b == null || !com.ilxomjon.dur_novvot_dokon.ui.scan.ScanUi.looksScanned(text)) return false
        viewLifecycleOwner.lifecycleScope.launch {
            when (val s = ServiceLocator.barcodes.resolve(text.trim())) {
                is com.ilxomjon.dur_novvot_dokon.data.barcode.BarcodeRepository.Scan.Found -> {
                    repo.add(s.product.id, 1)
                    com.ilxomjon.dur_novvot_dokon.ui.scan.ScanUi.feedback(requireContext(), true)
                    snack(s.product.name + " · " + getString(R.string.lb_scan_added, repo.copies(s.product.id)))
                }
                else -> {
                    com.ilxomjon.dur_novvot_dokon.ui.scan.ScanUi.feedback(requireContext(), false)
                    snack(getString(R.string.lb_hw_unknown, text.trim()))
                }
            }
        }
        return true
    }

    // ---------------- kompyuterdan tovar kodlari ----------------

    private fun codesText(s: LabelState): String = buildString {
        if (s.codesAt > 0) {
            append(getString(R.string.lb_codes_last, formatDateTime(s.codesAt), s.codesPlu, s.codesBarcodes))
            if (s.codesNote.isNotBlank()) append("\n").append(getString(R.string.lb_codes_src, s.codesNote))
        } else append(getString(R.string.lb_codes_never))
        append("\n\n").append(getString(R.string.lb_codes_how))
    }

    private fun showCodes() {
        val ctx = requireContext()
        val cloud = ServiceLocator.shareStore.isCloudReady
        val d = MaterialAlertDialogBuilder(ctx).setTitle(R.string.lb_codes).setMessage(codesText(repo.state.value))
            .setNegativeButton(R.string.close, null)
        if (cloud) d.setPositiveButton(R.string.lb_codes_now) { _, _ -> askCodes() }
        d.show()
    }

    /** Kompyuterga so'rov yoziladi; javob kelguncha (5 daqiqagacha) navbat tekshirib turiladi. */
    private fun askCodes() {
        val before = repo.state.value.codesAt
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                repo.requestCodes()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: com.ilxomjon.dur_novvot_dokon.data.labels.LabelPrinterClient.PrintError) {
                snack(getString(R.string.lb_codes_no_cloud))
                return@launch
            } catch (e: Exception) {
                snack(getString(R.string.lb_codes_fail, requireContext().describeError(e)))
                return@launch
            }
            snack(getString(R.string.lb_codes_wait))
            val end = System.currentTimeMillis() + 5 * 60_000L
            while (System.currentTimeMillis() < end && repo.state.value.codesAt == before) {
                delay(15_000)
                runCatching { ServiceLocator.share.processInbox() }
            }
            val s = repo.state.value
            if (s.codesAt != before) {
                loadBarcodeSet()
                refreshNotes()
                refreshList()
                snack(getString(R.string.lb_codes_done, s.codesPlu, s.codesBarcodes))
            } else snack(getString(R.string.lb_codes_timeout))
        }
    }

    private fun snack(t: String) { _b?.let { Snackbar.make(it.root, t, Snackbar.LENGTH_LONG).show() } }

    override fun onResume() {
        super.onResume()
        if (_b != null) viewLifecycleOwner.lifecycleScope.launch { loadBarcodeSet() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
