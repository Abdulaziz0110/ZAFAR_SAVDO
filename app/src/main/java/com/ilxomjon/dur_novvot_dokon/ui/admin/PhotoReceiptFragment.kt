package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.Money
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.photo.ReceiptAi
import com.ilxomjon.dur_novvot_dokon.data.photo.ReceiptMatch
import com.ilxomjon.dur_novvot_dokon.data.photo.ReceiptScan
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import com.ilxomjon.dur_novvot_dokon.ui.show
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.imsoft.shared.JObj
import uz.imsoft.shared.Json
import uz.imsoft.shared.android.Ui
import java.io.File

/**
 * v5.3.8 Admin: RASM ORQALI KIRIM. Yuk xati / chek / qo'lda yozilgan ro'yxat rasmi -> sun'iy intellekt o'qiydi ->
 * har qator o'rganilganlar (oldingi kirimlar) va tovar nomlari bo'yicha tovarga moslanadi -> egasi tekshiradi
 * (nom, miqdor, narx; har qatorni bosganda rasmning aynan o'sha qatori ko'rinadi) -> kirim shakliga o'tadi.
 * O'tkazilganda nima tanlangani eslab qolinadi — keyingi safar o'xshash yozuvni o'zi taniydi.
 */
class PhotoReceiptFragment : CodeFragment() {
    private class Photo(val file: File, var rotate: Int)
    private class Line(
        val photo: Int, val text: String, val scanQty: Double, val unit: String, val scanPrice: Double, val scanSum: Double,
        val y0: Double, val y1: Double,
        var pid: String?, var how: String, var score: Double, var alts: List<String>, var qty: Double, var price: Double,
        var factor: Double,
    )

    private val photos = ArrayList<Photo>()
    private val lines = ArrayList<Line>()
    private var supText = ""
    private var supCode = ""
    private var supName = ""
    private var usd = false
    private var docTotal = 0.0
    private var notes = ""
    private var busy = false
    private var cameraFile: File? = null

    private lateinit var tvStatus: TextView
    private lateinit var tvSup: TextView
    private lateinit var tvSum: TextView
    private lateinit var linesBox: LinearLayout
    private lateinit var btnGo: com.google.android.material.button.MaterialButton
    private lateinit var tvSupHist: TextView
    private lateinit var btnSupHist: com.google.android.material.button.MaterialButton
    // v5.3.12: tepada qotirilgan rasm maydoni (kattalashtirish/surish), qator bosilsa — o'sha qatorga
    private lateinit var viewer: android.widget.FrameLayout
    private lateinit var zoom: com.ilxomjon.dur_novvot_dokon.ui.common.ZoomImageView
    private lateinit var tvPh: TextView
    private lateinit var btnShowPh: com.google.android.material.button.MaterialButton
    private var curPhoto = -1
    private var viewerOpen = true

    private val ai get() = ServiceLocator.receiptAi
    private val products get() = ServiceLocator.catalog.state.value.products
    private val stateFile: File get() = File(ai.dir, "state.json")

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) scan(uri) }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) scan(Uri.fromFile(f))
    }
    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) openCamera() else snack(getString(R.string.ph_camera_denied))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(KEY_CAM)?.let { cameraFile = File(it) }
        if (photos.isEmpty()) restore()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        cameraFile?.let { outState.putString(KEY_CAM, it.path) }
    }

    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.pr_title)
        val c = p.content
        buildViewer(p.root)
        c.addView(Ui.hint(ctx, getString(R.string.pr_hint), 4))
        c.addView(
            Ui.buttonRow(
                ctx,
                Ui.button(ctx, getString(R.string.ph_camera), icon = R.drawable.ic_camera) { camera() },
                Ui.button(ctx, getString(R.string.ph_gallery), outlined = true, icon = R.drawable.ic_image) { runCatching { pickImage.launch("image/*") } },
            ),
        )
        tvStatus = Ui.hint(ctx, "", 8).also { c.addView(it) }
        btnShowPh = Ui.button(ctx, getString(R.string.pr_show_photo), outlined = true, icon = R.drawable.ic_image) { showPhotoAt(curPhoto.coerceAtLeast(0)) }
        c.addView(btnShowPh)
        btnShowPh.show(false)
        c.addView(Ui.section(ctx, getString(R.string.wf_supplier)))
        tvSup = Ui.title(ctx, "", 4).also { c.addView(it) }
        tvSup.setOnClickListener { pickSupplier() }
        tvSupHist = Ui.hint(ctx, "", 4).also { c.addView(it) }
        btnSupHist = Ui.button(ctx, getString(R.string.wf_history), outlined = true, icon = R.drawable.ic_history) { supplierHistory() }
        c.addView(Ui.buttonRow(ctx, Ui.button(ctx, getString(R.string.wf_pick), outlined = true, icon = R.drawable.ic_person) { pickSupplier() }, btnSupHist))
        c.addView(Ui.section(ctx, getString(R.string.wf_goods)))
        linesBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        c.addView(linesBox)
        c.addView(Ui.button(ctx, getString(R.string.wf_add), outlined = true, icon = R.drawable.ic_add) { addManual() })
        tvSum = Ui.title(ctx, "", 12).also { c.addView(it) }
        btnGo = Ui.button(ctx, getString(R.string.pr_go), top = 12, icon = R.drawable.ic_check) { transfer() }
        c.addView(btnGo)
        c.addView(
            Ui.buttonRow(
                ctx,
                Ui.button(ctx, getString(R.string.pr_new), outlined = true, icon = R.drawable.ic_delete) { reset() },
                Ui.button(ctx, getString(R.string.pr_key), outlined = true, icon = R.drawable.ic_settings) { keyDialog() },
            ),
        )
        c.addView(Ui.hint(ctx, getString(R.string.pr_learned, ai.memory().aliases.size), 8))
        return p.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launchUi { ServiceLocator.onec.load() }
        render()
        if (ServiceLocator.secure.aiKey.isBlank()) keyDialog()
    }

    // ---------------- rasm maydoni ----------------

    private fun buildViewer(root: LinearLayout) {
        val h = (resources.displayMetrics.heightPixels * 0.32f).toInt()
        viewer = android.widget.FrameLayout(ctx).apply { setBackgroundColor(0xFF202124.toInt()) }
        zoom = com.ilxomjon.dur_novvot_dokon.ui.common.ZoomImageView(ctx)
        viewer.addView(zoom, android.widget.FrameLayout.LayoutParams(-1, -1))
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(0x99000000.toInt())
        }
        fun tb(t: String, f: () -> Unit) = TextView(ctx).apply {
            text = t
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            val pd = Ui.dp(ctx, 10)
            setPadding(pd, Ui.dp(ctx, 6), pd, Ui.dp(ctx, 6))
            setOnClickListener { f() }
        }
        bar.addView(tb("◀") { showPhotoAt(curPhoto - 1) })
        tvPh = tb("") { zoom.reset() }
        bar.addView(tvPh)
        bar.addView(tb("▶") { showPhotoAt(curPhoto + 1) })
        bar.addView(tb(getString(R.string.pr_rotate)) {
            photos.getOrNull(curPhoto)?.let { it.rotate = (it.rotate + 90) % 360; save(); showPhotoAt(curPhoto) }
        })
        bar.addView(tb(getString(R.string.pr_hide)) { viewerOpen = false; viewer.show(false); btnShowPh.show(photos.isNotEmpty()) })
        viewer.addView(bar, android.widget.FrameLayout.LayoutParams(-2, -2, android.view.Gravity.TOP or android.view.Gravity.END))
        root.addView(viewer, 1, LinearLayout.LayoutParams(-1, h))
        viewer.show(false)
    }

    private fun showPhotoAt(i: Int) {
        if (photos.isEmpty()) { viewer.show(false); return }
        val k = i.coerceIn(0, photos.size - 1)
        val ph = photos[k]
        zoom.show(ai.upright(ph.file, ph.rotate))
        curPhoto = k
        tvPh.text = "${k + 1}/${photos.size}"
        viewerOpen = true
        viewer.show(true)
        btnShowPh.show(false)
    }

    private val viewerVisible: Boolean get() = viewer.visibility == View.VISIBLE

    /** Qator bosilganda: rasm maydonida aynan shu qator (kattalashtirilgan, o'rtada). */
    private fun focusRow(i: Int) {
        val l = lines.getOrNull(i) ?: return
        if (l.photo < 0) return
        if (l.photo != curPhoto || !viewerVisible) showPhotoAt(l.photo)
        if (l.y1 > l.y0) zoom.focusBand(l.y0, l.y1)
    }

    // ---------------- tarix (1C'dan, kompyuter yuborgan) ----------------

    private fun supplierNow(): uz.imsoft.shared.onec.Supplier? {
        val list = ServiceLocator.onec.state.value.suppliers?.list.orEmpty()
        return list.firstOrNull { supCode.isNotEmpty() && it.code == supCode } ?: list.firstOrNull { supName.isNotEmpty() && it.name == supName }
    }

    private fun docName(kind: String): String = getString(
        when (kind) {
            uz.imsoft.shared.onec.OneCDoc.RECEIPT -> R.string.wf_d_receipt
            uz.imsoft.shared.onec.OneCDoc.SUPPLIER_PAY -> R.string.wf_d_pay
            uz.imsoft.shared.onec.OneCDoc.SUPPLIER_RETURN -> R.string.wf_d_return
            else -> R.string.wf_d_other
        },
    )

    private fun supplierHistory() {
        val sup = supplierNow() ?: return
        val docs = sup.docs.sortedByDescending { it.stamp }.take(60)
        if (docs.isEmpty()) { snack(getString(R.string.wf_no_history)); return }
        val rows = docs.map { d ->
            Ui.Row(docName(d.kind) + "  \u2116" + d.number, uz.imsoft.shared.report.Stamp.formatDateTime(d.stamp) +
                (if (d.lines.isNotEmpty()) " \u00B7 " + getString(R.string.pr_n_goods, d.lines.size) else ""), money2(d.som, d.usd),
                icon = if (d.kind == uz.imsoft.shared.onec.OneCDoc.SUPPLIER_PAY) R.drawable.ic_payment else R.drawable.ic_receipt)
        }
        Ui.pickSearch(ctx, sup.name, rows, getString(R.string.wf_history_search), getString(R.string.close), keys = docs.map { it.haystack }) { i -> docs.getOrNull(i)?.let { showDoc(it) } }
    }

    /** Tovar kodi -> oxirgi kirimlar (eng yangisi birinchi): (hujjat, qator, yuk beruvchi). */
    private var lastIn: Map<String, List<Triple<uz.imsoft.shared.onec.OneCDoc, uz.imsoft.shared.onec.OneCLine, String>>> = emptyMap()

    private fun indexReceipts() {
        val out = HashMap<String, ArrayList<Triple<uz.imsoft.shared.onec.OneCDoc, uz.imsoft.shared.onec.OneCLine, String>>>()
        for (s in ServiceLocator.onec.state.value.suppliers?.list.orEmpty()) {
            for (d in s.docs) {
                if (d.kind != uz.imsoft.shared.onec.OneCDoc.RECEIPT) continue
                for (ln in d.lines) {
                    val k = ln.code.trim().ifEmpty { "n:" + ln.name.trim().lowercase() }
                    out.getOrPut(k) { ArrayList() } += Triple(d, ln, s.name)
                }
            }
        }
        out.values.forEach { l -> l.sortByDescending { it.first.stamp } }
        lastIn = out
    }

    private fun lastText(pid: String, name: String): String {
        val l = (lastIn[pid.trim()] ?: lastIn["n:" + name.trim().lowercase()])?.firstOrNull() ?: return getString(R.string.pr_no_last)
        val (d, ln, sup) = l
        val price = if (ln.priceUsd != 0.0 && ln.priceSom == 0.0) usd(ln.priceUsd) else som(ln.priceSom)
        return getString(R.string.pr_last_in, uz.imsoft.shared.report.Stamp.formatDate(d.stamp), fmtQty(ln.qty), price, sup)
    }

    // ---------------- rasm ----------------

    private fun camera() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
        else askCamera.launch(Manifest.permission.CAMERA)
    }

    private fun openCamera() {
        runCatching {
            val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
            val f = File(dir, "receipt.jpg")
            if (f.exists()) f.delete()
            cameraFile = f
            takePhoto.launch(FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f))
        }.onFailure { snack(getString(R.string.ph_camera_fail)) }
    }

    private fun scan(uri: Uri) {
        if (busy) return
        if (ServiceLocator.secure.aiKey.isBlank()) { keyDialog(); return }
        busy = true
        tvStatus.text = getString(R.string.pr_reading)
        launchUi {
            try {
                val f = ai.load(uri) ?: throw IllegalStateException(getString(R.string.ph_photo_bad))
                val r = try {
                    ai.read(f)
                } catch (e: IllegalStateException) {
                    if (e.message == ReceiptAi.NO_KEY) { keyDialog(); return@launchUi }
                    throw e
                }
                photos += Photo(f, r.rotate)
                apply(photos.size - 1, r)
                showPhotoAt(photos.size - 1)
                save()
                tvStatus.text = getString(R.string.pr_read_ok, r.rows.count { !it.crossed }) + (if (r.note.isNotEmpty()) "\n" + r.note else "")
                render()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                tvStatus.text = getString(R.string.pr_read_fail, e.message ?: e.javaClass.simpleName)
            } finally {
                busy = false
            }
        }
    }

    /** O'qilgan rasm -> qatorlar: o'rganilganlar va katalog bo'yicha tovar, o'rganilgan ko'paytiruvchi. */
    private suspend fun apply(photo: Int, r: ReceiptScan.Result) {
        val mem = ai.memory()
        if (supName.isEmpty() && r.supplier.isNotEmpty()) {
            supText = r.supplier
            val list = ServiceLocator.onec.state.value.suppliers?.list.orEmpty().map { it.code to it.name }
            ReceiptMatch.supplier(mem, r.supplier, list)?.let { (code, name) -> supCode = code; supName = name }
        }
        if (photos.size == 1) usd = r.usd
        docTotal += r.total
        if (r.note.isNotEmpty()) notes = (notes + "\n" + r.note).trim()
        val cands = withContext(Dispatchers.Default) { products.map { ReceiptMatch.Cand(it.id, it.name) } }
        val sup = supCode.ifEmpty { supName }
        val out = withContext(Dispatchers.Default) {
            r.rows.filter { !it.crossed }.map { row ->
                val m = ReceiptMatch.match(mem, row.text, sup, cands)
                val f = if (m.factor > 1.0) m.factor else 1.0
                Line(photo, row.text, row.qty, row.unit, row.price, row.sum, row.y0, row.y1, m.pid, m.how, m.score, m.alts,
                    Money.round2(row.qty * f), if (f > 1.0) Money.round2(row.price / f) else row.price, f)
            }
        }
        lines += out
    }

    // ---------------- ko'rinish ----------------

    private fun render() {
        if (!::linesBox.isInitialized) return
        tvSup.text = when {
            supName.isNotEmpty() -> supName + (if (supText.isNotEmpty() && ReceiptMatch.norm(supText) != ReceiptMatch.norm(supName)) "\n" + getString(R.string.pr_on_photo, supText) else "")
            supText.isNotEmpty() -> getString(R.string.pr_sup_unknown, supText)
            else -> getString(R.string.wf_not_chosen)
        }
        val sp = supplierNow()
        if (sp != null) {
            val hist = sp.docs.sortedByDescending { it.stamp }.take(6).joinToString("\n") { d ->
                uz.imsoft.shared.report.Stamp.formatDate(d.stamp) + " \u00B7 " + docName(d.kind) + " \u00B7 " + money2(d.som, d.usd)
            }
            val owe = when {
                sp.oweSom > 0.5 -> getString(R.string.wf_we_owe, som(sp.oweSom))
                sp.oweSom < -0.5 -> getString(R.string.wf_they_owe, som(-sp.oweSom))
                else -> getString(R.string.wf_no_debt)
            }
            tvSupHist.text = owe + (if (hist.isNotEmpty()) "\n" + hist else "")
        } else tvSupHist.text = ""
        tvSupHist.show(tvSupHist.text.isNotEmpty())
        btnSupHist.show(sp != null && sp.docs.isNotEmpty())
        if (photos.isNotEmpty() && viewerOpen && !viewerVisible) showPhotoAt(curPhoto.coerceAtLeast(0))
        btnShowPh.show(photos.isNotEmpty() && !viewerVisible)
        indexReceipts()
        linesBox.removeAllViews()
        val byId = products.associateBy { it.id }
        val mem = ai.memory()
        val sup = supCode.ifEmpty { supName }
        lines.forEachIndexed { i, l ->
            val (card, box) = Ui.card(ctx, 6)
            val p = l.pid?.let { byId[it] }
            val name = Ui.title(ctx, "${i + 1}) " + (p?.name ?: getString(R.string.pr_no_goods)), 0)
            if (p == null) name.setTextColor(ContextCompat.getColor(ctx, R.color.brand_error))
            name.setOnClickListener { focusRow(i); editGoods(i) }
            box.addView(name)
            val how = when (l.how) {
                "mem" -> getString(R.string.pr_how_mem)
                "fuzzy" -> getString(R.string.pr_how_fuzzy, (l.score * 100).toInt())
                "catalog" -> getString(R.string.pr_how_guess, (l.score * 100).toInt())
                "user" -> getString(R.string.pr_how_user)
                else -> ""
            }
            box.addView(Ui.hint(ctx, getString(R.string.pr_on_photo, l.text) + (if (how.isNotEmpty()) " · $how" else ""), 2).apply { setOnClickListener { focusRow(i) } })
            val price = if (usd) usd(l.price) else som(l.price)
            val sum = if (usd) usd(l.qty * l.price) else som(Money.round2(l.qty * l.price))
            val v = Ui.text(ctx, getString(R.string.oc_line, fmtQty(l.qty), price, sum), top = 4)
            v.setOnClickListener { focusRow(i); editQtyPrice(i) }
            box.addView(v)
            if (p != null) box.addView(Ui.hint(ctx, lastText(p.id, p.name), 2))
            val warn = ArrayList<String>(3)
            if (l.factor > 1.0) warn += getString(R.string.pr_factor, fmtQty(l.factor))
            if (l.scanSum > 0 && Math.abs(l.qty * l.price - l.scanSum) > Math.max(1.0, l.scanSum * 0.01)) {
                warn += getString(R.string.pr_sum_diff, if (usd) usd(l.scanSum) else som(l.scanSum))
            }
            if (p != null) ReceiptMatch.priceJump(mem, sup, p.id, l.price)?.let { warn += getString(R.string.pr_price_jump, if (usd) usd(it) else som(it)) }
            if (warn.isNotEmpty()) box.addView(Ui.hint(ctx, warn.joinToString("\n"), 2).apply { setTextColor(ContextCompat.getColor(ctx, R.color.brand_error)) })
            // v5.3.12: aniq tugmalar — tovarni almashtirish, soni/narx, o'chirish
            box.addView(
                Ui.buttonRow(
                    ctx,
                    Ui.button(ctx, getString(R.string.pr_b_goods), outlined = true, top = 0) { focusRow(i); editGoods(i) },
                    Ui.button(ctx, getString(R.string.pr_b_qty), outlined = true, top = 0) { focusRow(i); editQtyPrice(i) },
                    Ui.button(ctx, getString(R.string.delete), outlined = true, top = 0) { removeLine(i) },
                    top = 4,
                ),
            )
            card.setOnClickListener { focusRow(i) }
            card.setOnLongClickListener { removeLine(i); true }
            linesBox.addView(card)
        }
        if (lines.isEmpty()) linesBox.addView(Ui.hint(ctx, getString(R.string.pr_empty)))
        val total = lines.sumOf { it.qty * it.price }
        val sb = StringBuilder(getString(R.string.pr_total, lines.size, if (usd) usd(total) else som(Money.round2(total))))
        if (docTotal > 0 && Math.abs(docTotal - total) > Math.max(1.0, docTotal * 0.005)) {
            sb.append('\n').append(getString(R.string.pr_doc_total, if (usd) usd(docTotal) else som(docTotal)))
        }
        tvSum.text = sb.toString()
        tvSum.show(lines.isNotEmpty())
        btnGo.isEnabled = lines.isNotEmpty()
    }

    private fun cropView(i: Int): ImageView? {
        val l = lines.getOrNull(i) ?: return null
        val ph = photos.getOrNull(l.photo) ?: return null
        if (l.y1 <= l.y0) return null
        val bmp: Bitmap = ai.crop(ph.file, ph.rotate, l.y0, l.y1) ?: return null
        return ImageView(ctx).apply {
            setImageBitmap(bmp)
            adjustViewBounds = true
            val pad = Ui.dp(ctx, 12)
            setPadding(pad, pad, pad, 0)
            setOnClickListener { showPhotoAt(l.photo) }
        }
    }

    private fun scanLine(l: Line): String = getString(
        R.string.pr_scan_line, fmtQty(l.scanQty) + (if (l.unit.isNotEmpty()) " " + l.unit else ""),
        if (usd) usd(l.scanPrice) else som(l.scanPrice), if (usd) usd(l.scanSum) else som(l.scanSum),
    )

    /** Tovarni almashtirish: rasm qatori, taxminlar (o'xshashlari), boshqa tovar. */
    private fun editGoods(i: Int) {
        val l = lines.getOrNull(i) ?: return
        val byId = products.associateBy { it.id }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        cropView(i)?.let { col.addView(it) }
        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), 0) }
        col.addView(list)
        var dlg: androidx.appcompat.app.AlertDialog? = null
        l.alts.mapNotNull { byId[it] }.forEach { p ->
            list.addView(Ui.item(ctx, p.name, getString(R.string.ad_product_code, p.id) + "\n" + lastText(p.id, p.name), icon = R.drawable.ic_shopping_bag) {
                dlg?.dismiss()
                setGoods(i, p.id)
            })
        }
        list.addView(Ui.button(ctx, getString(R.string.pr_other_goods), outlined = true, icon = R.drawable.ic_search) {
            dlg?.dismiss()
            Pickers.product(ctx) { p -> setGoods(i, p.id) }
        })
        dlg = MaterialAlertDialogBuilder(ctx).setTitle(getString(R.string.pr_on_photo, l.text))
            .setView(android.widget.ScrollView(ctx).apply { addView(col) })
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun setGoods(i: Int, pid: String) {
        val l = lines.getOrNull(i) ?: return
        l.pid = pid
        l.how = "user"
        l.score = 1.0
        save()
        render()
    }

    private fun editQtyPrice(i: Int) {
        val l = lines.getOrNull(i) ?: return
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        cropView(i)?.let { col.addView(it) }
        val f = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 4), Ui.dp(ctx, 20), 0) }
        col.addView(f)
        f.addView(Ui.hint(ctx, scanLine(l), 4))
        val (tq, eq) = Ui.field(ctx, getString(R.string.ad_qty), Ui.NUMBER, if (l.qty > 0) Money.plain(l.qty) else "")
        val (tp, ep) = Ui.field(ctx, getString(if (usd) R.string.ad_price_usd else R.string.ad_price_som), Ui.NUMBER,
            if (l.price > 0) Money.plain(l.price) else "")
        f.addView(tq)
        f.addView(tp)
        MaterialAlertDialogBuilder(ctx).setTitle(products.firstOrNull { it.id == l.pid }?.name ?: l.text)
            .setView(android.widget.ScrollView(ctx).apply { addView(col) })
            .setPositiveButton(R.string.ok) { _, _ ->
                val q = Money.parseUser(eq.text?.toString()) ?: 0.0
                val pr = Money.parseUser(ep.text?.toString()) ?: 0.0
                if (q > 0) l.qty = q
                if (pr >= 0) l.price = pr
                save()
                render()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun removeLine(i: Int) {
        val l = lines.getOrNull(i) ?: return
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.wf_remove).setMessage(l.text)
            .setPositiveButton(R.string.wf_remove) { _, _ -> lines.removeAt(i); save(); render() }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun addManual() {
        Pickers.product(ctx) { p ->
            Pickers.qtyPrice(ctx, p.name, 0.0, p.costSom, p.costUsd, usd) { q, s, u ->
                lines += Line(-1, p.name, q, "", if (usd) u else s, 0.0, 0.0, 0.0, p.id, "user", 1.0, emptyList(), q, if (usd) u else s, 1.0)
                save()
                render()
            }
        }
    }

    private fun pickSupplier() {
        val list = ServiceLocator.onec.state.value.suppliers?.list.orEmpty().sortedByDescending { s -> s.docs.maxOfOrNull { it.stamp } ?: 0L }
        if (list.isEmpty()) { snack(getString(R.string.pr_no_suppliers)); return }
        val rows = list.map { s -> Ui.Row(s.name, getString(R.string.ad_product_code, s.code), "", icon = R.drawable.ic_person) }
        Ui.pickSearch(ctx, getString(R.string.wf_supplier), rows, getString(R.string.wf_supplier_search), getString(R.string.cancel),
            keys = list.map { it.name + " " + it.code + " " + it.phone }) { i ->
            supName = list[i].name
            supCode = list[i].code
            save()
            render()
        }
    }

    private fun keyDialog() {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(ctx, 20), 0, Ui.dp(ctx, 20), 0) }
        col.addView(Ui.hint(ctx, getString(R.string.pr_key_hint), 4))
        val (tk, ek) = Ui.field(ctx, getString(R.string.pr_key_field), android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val (tm, em) = Ui.field(ctx, getString(R.string.pr_model_field), value = ServiceLocator.secure.aiModel)
        col.addView(tk)
        col.addView(tm)
        if (ServiceLocator.secure.aiKey.isNotBlank()) col.addView(Ui.hint(ctx, getString(R.string.pr_key_saved), 4))
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.pr_key).setView(col)
            .setPositiveButton(R.string.ok) { _, _ ->
                val k = ek.text?.toString()?.trim().orEmpty()
                if (k.isNotEmpty()) ServiceLocator.secure.aiKey = k
                ServiceLocator.secure.aiModel = em.text?.toString()?.trim().orEmpty()
                if (ServiceLocator.secure.aiKey.isNotBlank()) snack(getString(R.string.pr_key_ok))
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun reset() {
        MaterialAlertDialogBuilder(ctx).setTitle(R.string.pr_new).setMessage(R.string.pr_new_q)
            .setPositiveButton(R.string.ok) { _, _ ->
                photos.forEach { it.file.delete() }
                photos.clear(); lines.clear()
                supText = ""; supCode = ""; supName = ""; usd = false; docTotal = 0.0; notes = ""
                stateFile.delete()
                tvStatus.text = ""
                curPhoto = -1
                zoom.show(null)
                viewer.show(false)
                btnShowPh.show(false)
                render()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    /** Kirim shakliga: tekshirish, o'rganish (keyingi safar uchun), natijani berish. */
    private fun transfer() {
        val bad = lines.indexOfFirst { it.pid == null || it.qty <= 0 || it.price <= 0 }
        if (bad >= 0) {
            snack(getString(R.string.pr_fix_row, bad + 1))
            if (lines[bad].pid == null) editGoods(bad) else editQtyPrice(bad)
            return
        }
        val byId = products.associateBy { it.id }
        val now = System.currentTimeMillis()
        val mem = ai.memory()
        val sup = supCode.ifEmpty { supName }
        lines.forEach { l ->
            val pid = l.pid ?: return@forEach
            if (l.photo >= 0) ReceiptMatch.learn(mem, l.text, sup, pid, l.scanQty, l.qty, l.price, now)
            else ReceiptMatch.learn(mem, l.text, sup, pid, 0.0, l.qty, l.price, now)
        }
        if (supText.isNotEmpty() && supName.isNotEmpty()) ReceiptMatch.learnSupplier(mem, supText, supCode, supName)
        ai.save(mem)
        val ok = lines.filter { it.pid != null }
        val b = Bundle().apply {
            putString(RES_PARTY, supName)
            putString(RES_CODE, supCode)
            putStringArray(RES_IDS, ok.map { it.pid.orEmpty() }.toTypedArray())
            putStringArray(RES_NAMES, ok.map { byId[it.pid]?.name ?: it.text }.toTypedArray())
            putDoubleArray(RES_QTY, ok.map { it.qty }.toDoubleArray())
            putDoubleArray(RES_SOM, ok.map { if (usd) 0.0 else it.price }.toDoubleArray())
            putDoubleArray(RES_USD, ok.map { if (usd) it.price else 0.0 }.toDoubleArray())
            putString(RES_NOTE, getString(R.string.pr_note))
        }
        parentFragmentManager.setFragmentResult(REQ, b)
        photos.forEach { it.file.delete() }
        stateFile.delete()
        navigator().pop()
    }

    // ---------------- saqlash (kamera ilovani yopib yuborsa ham yo'qolmasin) ----------------

    private fun save() {
        runCatching {
            val o = mapOf(
                "at" to System.currentTimeMillis(), "st" to supText, "sc" to supCode, "sn" to supName, "usd" to usd, "dt" to docTotal,
                "ph" to photos.map { listOf(it.file.path, it.rotate) },
                "l" to lines.map {
                    mapOf("p" to it.photo, "t" to it.text, "sq" to it.scanQty, "u" to it.unit, "sp" to it.scanPrice, "ss" to it.scanSum,
                        "y0" to it.y0, "y1" to it.y1, "id" to it.pid, "h" to it.how, "s" to it.score, "a" to it.alts, "q" to it.qty,
                        "pr" to it.price, "f" to it.factor)
                },
            )
            stateFile.writeText(Json.write(o))
        }
    }

    private fun restore() {
        val o = runCatching { JObj.parse(stateFile.readText()) }.getOrNull() ?: return
        if (System.currentTimeMillis() - o.long("at") > 24 * 3600_000L) { stateFile.delete(); return }
        supText = o.str("st"); supCode = o.str("sc"); supName = o.str("sn"); usd = o.bool("usd"); docTotal = o.num("dt")
        for (x in o.arr("ph")) {
            val l = x as? List<*> ?: continue
            val f = File(l.getOrNull(0)?.toString().orEmpty())
            photos += Photo(f, (l.getOrNull(1) as? Number)?.toInt() ?: 0)
        }
        for (r in o.objs("l")) {
            lines += Line(r.num("p").toInt(), r.str("t"), r.num("sq"), r.str("u"), r.num("sp"), r.num("ss"), r.num("y0"), r.num("y1"),
                r.str("id").ifEmpty { null }, r.str("h"), r.num("s"), r.arr("a").map { it.toString() }, r.num("q"), r.num("pr"),
                r.num("f", 1.0))
        }
    }

    companion object {
        const val REQ = "photo_receipt"
        const val RES_PARTY = "party"
        const val RES_CODE = "code"
        const val RES_IDS = "ids"
        const val RES_NAMES = "names"
        const val RES_QTY = "qty"
        const val RES_SOM = "som"
        const val RES_USD = "usd"
        const val RES_NOTE = "note"
        private const val KEY_CAM = "cam"
    }
}
