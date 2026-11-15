package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.AppRole
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.core.Translit
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.Product
import com.ilxomjon.dur_novvot_dokon.ui.launchOnStarted
import com.ilxomjon.dur_novvot_dokon.ui.money
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.onec.WriteKind
import java.io.File

/**
 * v5.3 MAHSULOTLAR — mahsulotga tegishli HAMMA ish bitta joyda (telefondagi katalogdan, internetsiz ochiladi):
 *
 *   * qidiruv: kodi, nomi yoki narxi ("1042", "pista", "pista 95000");
 *   * qatordagi tez amallar: tahrirlash (narx, nom, PLU) · ma'lumot (bio) · rasm;
 *   * qator bosilsa — shu mahsulotning hamma amali: tahrirlash, ma'lumot, rasm, yorliq, hisobdan chiqarish;
 *   * tepada: yangi tovar va "Boshqa amallar" (ko'p tovarga birdan, fayldan yuklash/chiqarish).
 *
 * Avval bular alohida joylarda edi: "Yangi tovar", "Tovarni o'zgartirish", "Narx belgilash", "Mahsulotlar (bio)",
 * "Ko'p tovarga birdan", "Hisobdan chiqarish".
 */
class ProductsFragment : CodeFragment() {
    private enum class Filter { ALL, NO_INFO, NO_PHOTO }

    private var filter = Filter.ALL
    private var query = ""
    private var shown: List<Product> = emptyList()
    private lateinit var lp: Ui.ListPage
    private var thumbJob: Job? = null
    private var photoFor = ""
    private var cameraUri: Uri? = null

    override fun build(inflater: LayoutInflater): View {
        lp = listPage(R.string.pb_title) { i -> shown.getOrNull(i)?.let { sheet(it) } }
        lp.adapter.onAction = { row, act -> shown.getOrNull(row)?.let { quick(it, act) } }
        lp.header.addView(Ui.search(ctx, getString(R.string.ph_search)) { q -> query = q; render() }.first)
        lp.header.addView(
            Ui.toggle(ctx, listOf(getString(R.string.pb_all), getString(R.string.ph_no_info), getString(R.string.ph_no_photo)), 0, 4) { i ->
                filter = Filter.entries[i]
                render()
            },
        )
        val bMore = Ui.button(ctx, getString(R.string.ph_more), outlined = true, top = 6, icon = R.drawable.ic_tune) { more() }
        if (AppRole.isAdmin) {
            val bNew = Ui.button(ctx, getString(R.string.of_new_goods), top = 6, icon = R.drawable.ic_add_box) { navigator().push(OneCFormFragment.of(WriteKind.NEW_GOODS)) }
            lp.header.addView(Ui.buttonRow(ctx, bNew, bMore, top = 0))
        } else lp.header.addView(bMore)
        render()
        return lp.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        launchOnStarted { ServiceLocator.catalog.state.collect { render() } }
        launchOnStarted { ServiceLocator.images.ids.collect { render() } }
    }

    override fun onResume() {
        super.onResume()
        if (::lp.isInitialized) render()
    }

    private fun price(p: Product): String = if (p.baseIsSom) ctx.money(p.wholesaleSom, Currency.SOM) else ctx.money(p.wholesaleUsd, Currency.USD)

    private fun render() {
        if (!::lp.isInitialized) return
        val bio = ServiceLocator.bio
        val img = ServiceLocator.images
        val all = ServiceLocator.catalog.state.value.products
        val parsed = uz.imsoft.shared.SmartQuery.parse(query)
        val k = Translit.key(parsed.text)
        val byName = if (k.isEmpty()) (if (parsed.numbers.isEmpty()) all else emptyList()) else all.filter { it.searchKey.contains(k) }
        val found = uz.imsoft.shared.SmartQuery.refine(parsed, byName, all, { it.id }, { it.name }, { if (it.baseIsSom) it.wholesaleSom else it.wholesaleUsd })
        shown = found.asSequence().filter {
            when (filter) {
                Filter.ALL -> true
                Filter.NO_INFO -> !bio.has(it.id)
                Filter.NO_PHOTO -> !img.has(it.id)
            }
        }.take(400).toList()
        submit()
        lp.empty.text = getString(if (all.isEmpty()) R.string.pb_no_catalog else R.string.pb_nothing)
        lp.empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        lp.toolbar.subtitle = getString(R.string.ph_count, all.size, all.count { bio.has(it.id) }, img.ids.value.size)
        loadThumbs()
    }

    private fun submit() {
        val bio = ServiceLocator.bio
        val img = ServiceLocator.images
        val admin = AppRole.isAdmin
        val acts = if (admin) listOf(R.drawable.ic_edit, R.drawable.ic_info, R.drawable.ic_image) else listOf(R.drawable.ic_info, R.drawable.ic_image)
        lp.adapter.submit(
            shown.map { p ->
                val b = bio.of(p.id)
                Ui.Row(
                    p.name,
                    getString(R.string.ad_product_code, p.id) + (if (b != null) " · " + b.text.take(40) else ""),
                    price(p),
                    icon = if (b != null) R.drawable.ic_info else R.drawable.ic_shopping_bag,
                    tone = if (b != null) 1 else 0,
                    actions = acts,
                    image = img.thumbCached(p.id),
                )
            },
        )
    }

    /** Ko'rinayotgan qatorlar uchun kichik rasmlarni fonda o'qiydi (ro'yxat qotib qolmaydi). */
    private fun loadThumbs() {
        val img = ServiceLocator.images
        val need = shown.filter { img.has(it.id) && img.thumbCached(it.id) == null }.map { it.id }
        if (need.isEmpty()) return
        thumbJob?.cancel()
        thumbJob = viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) { for (id in need.take(120)) img.thumb(id) }
            if (::lp.isInitialized && view != null) submit()
        }
    }

    // ---------------- amallar ----------------

    private fun quick(p: Product, act: Int) {
        val admin = AppRole.isAdmin
        when (if (admin) act else act + 1) {
            0 -> edit(p)
            1 -> navigator().push(BioFragment.of(p.id, p.name))
            else -> photoMenu(p)
        }
    }

    private fun edit(p: Product) = navigator().push(OneCFormFragment.of(WriteKind.GOODS_EDIT, p.id))

    /** Mahsulot bosilganda — shu mahsulotning hamma amali. */
    private fun sheet(p: Product) {
        val admin = AppRole.isAdmin
        val has = ServiceLocator.images.has(p.id)
        val rows = ArrayList<Ui.MenuRow>()
        if (admin) rows += Ui.MenuRow(getString(R.string.ph_edit), getString(R.string.ph_edit_sub), R.drawable.ic_edit, 1) { edit(p) }
        rows += Ui.MenuRow(getString(R.string.ph_info), ServiceLocator.bio.of(p.id)?.text?.take(60) ?: getString(R.string.ph_info_none), R.drawable.ic_info) {
            navigator().push(BioFragment.of(p.id, p.name))
        }
        rows += Ui.MenuRow(getString(if (has) R.string.ph_photo_change else R.string.ph_photo_add), getString(R.string.ph_photo_sub), R.drawable.ic_image) { photoMenu(p) }
        if (admin) {
            rows += Ui.MenuRow(getString(R.string.ph_label), getString(R.string.ph_label_sub, ServiceLocator.labels.copies(p.id)), R.drawable.ic_label) {
                ServiceLocator.labels.add(p.id)
                snack(getString(R.string.ph_label_added, p.name))
            }
            rows += Ui.MenuRow(getString(R.string.wo_title), getString(R.string.ph_writeoff_sub), R.drawable.ic_delete, 3) {
                navigator().push(WriteFormFragment.of(WriteKind.WRITE_OFF, "", ""))
            }
        }
        Ui.menuSheet(ctx, p.name + " · " + price(p), rows)
    }

    private fun more() {
        val rows = ArrayList<Ui.MenuRow>()
        if (AppRole.isAdmin) {
            rows += Ui.MenuRow(getString(R.string.bk_title), getString(R.string.bk_sub), R.drawable.ic_tune, 1) { navigator().push(BulkFragment()) }
            rows += Ui.MenuRow(getString(R.string.wo_title), getString(R.string.wo_sub), R.drawable.ic_delete, 3) { navigator().push(WriteFormFragment.of(WriteKind.WRITE_OFF, "", "")) }
            rows += Ui.MenuRow(getString(R.string.pb_import), getString(R.string.ph_import_sub), R.drawable.ic_download) {
                runCatching { pickCsv.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) }
            }
            rows += Ui.MenuRow(getString(R.string.pb_export), getString(R.string.ph_export_sub), R.drawable.ic_backup) {
                runCatching { saveCsv.launch("mahsulot_malumotlari.csv") }
            }
        }
        val auto = ServiceLocator.appPrefs.bioAuto
        rows += Ui.MenuRow(getString(R.string.bio_auto), getString(if (auto) R.string.ph_on else R.string.ph_off), R.drawable.ic_info, if (auto) 1 else 0) {
            ServiceLocator.appPrefs.bioAuto = !auto
            snack(getString(R.string.bio_auto) + ": " + getString(if (!auto) R.string.ph_on else R.string.ph_off))
        }
        rows += Ui.MenuRow(getString(R.string.sp_refresh), getString(R.string.ph_refresh_sub), R.drawable.ic_sync) {
            launchUi { ServiceLocator.catalog.sync(); snack(getString(R.string.sync_ok)) }
        }
        Ui.menuSheet(ctx, getString(R.string.ph_more), rows)
    }

    // ---------------- rasm ----------------

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) savePhoto(uri) }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let { savePhoto(it) } }
    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) openCamera() else snack(getString(R.string.ph_camera_denied)) }

    private fun photoMenu(p: Product) {
        photoFor = p.id
        val rows = arrayListOf(
            Ui.MenuRow(getString(R.string.ph_camera), "", R.drawable.ic_camera, 1) { camera() },
            Ui.MenuRow(getString(R.string.ph_gallery), "", R.drawable.ic_image) { runCatching { pickImage.launch("image/*") } },
        )
        if (ServiceLocator.images.has(p.id)) {
            rows += Ui.MenuRow(getString(R.string.ph_photo_view), "", R.drawable.ic_search) { viewPhoto(p) }
            rows += Ui.MenuRow(getString(R.string.ph_photo_remove), "", R.drawable.ic_delete, 3) {
                launchUi { ServiceLocator.images.remove(p.id); snack(getString(R.string.ph_photo_removed)) }
            }
        }
        Ui.menuSheet(ctx, p.name, rows)
    }

    private fun camera() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
        else askCamera.launch(Manifest.permission.CAMERA)
    }

    private fun openCamera() {
        runCatching {
            val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
            val f = File(dir, "photo.jpg")
            if (f.exists()) f.delete()
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            cameraUri = uri
            takePhoto.launch(uri)
        }.onFailure { snack(getString(R.string.ph_camera_fail)) }
    }

    private fun savePhoto(uri: Uri) {
        val id = photoFor
        if (id.isEmpty()) return
        launchUi {
            val ok = ServiceLocator.images.save(id, uri)
            snack(getString(if (ok) R.string.ph_photo_saved else R.string.ph_photo_bad))
        }
    }

    private fun viewPhoto(p: Product) {
        launchUi {
            val bmp = ServiceLocator.images.full(p.id) ?: return@launchUi
            val iv = android.widget.ImageView(ctx).apply {
                setImageBitmap(bmp)
                adjustViewBounds = true
                val pad = Ui.dp(ctx, 16)
                setPadding(pad, pad, pad, 0)
            }
            MaterialAlertDialogBuilder(ctx).setTitle(p.name).setView(iv).setPositiveButton(R.string.close, null).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PHOTO, photoFor)
        outState.putString(KEY_URI, cameraUri?.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoFor = savedInstanceState?.getString(KEY_PHOTO).orEmpty()
        cameraUri = savedInstanceState?.getString(KEY_URI)?.let(Uri::parse)
    }

    // ---------------- kompyuterda (Excel) yozish uchun CSV ----------------

    private val pickCsv = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) importCsv(uri) }
    private val saveCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if (uri != null) exportCsv(uri) }

    private fun exportCsv(uri: Uri) {
        val bio = ServiceLocator.bio
        val rows = ServiceLocator.catalog.state.value.products.map { p ->
            val x = bio.of(p.id)
            uz.imsoft.shared.bio.BioBook.CsvRow(p.id, p.name, x?.text.orEmpty(), x?.printText.orEmpty())
        }
        launchUi {
            val ok = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(uz.imsoft.shared.bio.BioBook.toCsv(rows).toByteArray()) } }.isSuccess
            }
            snack(getString(if (ok) R.string.pb_exported else R.string.pb_file_bad))
        }
    }

    private fun importCsv(uri: Uri) {
        launchUi {
            val text = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } }.getOrNull() }
            if (text == null) { snack(getString(R.string.pb_file_bad)); return@launchUi }
            val rows = uz.imsoft.shared.bio.BioBook.fromCsv(text)
            val known = ServiceLocator.catalog.state.value.products.map { it.id }.toSet()
            var done = 0
            var skipped = 0
            for (r in rows) {
                val code = r.code
                if (code !in known) { skipped++; continue }
                if (r.text.isEmpty()) ServiceLocator.bio.detach(code)
                else ServiceLocator.bio.save(ServiceLocator.bio.of(code)?.id, r.text, r.printText, listOf(code))
                done++
            }
            runCatching { ServiceLocator.bio.publish(ServiceLocator.cloudConfig, ServiceLocator.shareStore.secret) }
            render()
            snack(getString(R.string.pb_imported, done, skipped))
        }
    }

    private companion object {
        const val KEY_PHOTO = "ph_for"
        const val KEY_URI = "ph_uri"
    }
}
