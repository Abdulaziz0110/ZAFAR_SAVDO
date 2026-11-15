package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.view.LayoutInflater
import android.view.View
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.core.Translit
import com.ilxomjon.dur_novvot_dokon.domain.Currency
import com.ilxomjon.dur_novvot_dokon.domain.Product
import com.ilxomjon.dur_novvot_dokon.ui.money
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import uz.imsoft.shared.android.Ui

/**
 * v5.2 (A7): mahsulotlar ro'yxati — telefondagi katalogdan, INTERNETSIZ ishlaydi.
 * Har tovarning ma'lumoti (bio) shu yerda yoziladi va tahrirlanadi; "ma'lumoti bor / yo'q" filtri
 * nima qolganini ko'rsatadi.
 */
class ProductsFragment : CodeFragment() {
    private enum class Filter { ALL, WITH, WITHOUT }

    private var filter = Filter.ALL
    private var query = ""
    private var shown: List<Product> = emptyList()
    private lateinit var lp: Ui.ListPage

    override fun build(inflater: LayoutInflater): View {
        lp = listPage(R.string.pb_title) { i -> shown.getOrNull(i)?.let { openProduct(it) } }
        lp.header.addView(Ui.search(ctx, getString(R.string.pb_search)) { q -> query = q; render() }.first)
        lp.header.addView(
            Ui.toggle(
                ctx,
                listOf(getString(R.string.pb_all), getString(R.string.pb_with), getString(R.string.pb_without)),
                0, 4,
            ) { i -> filter = Filter.entries[i]; render() },
        )
        lp.header.addView(
            Ui.switch(ctx, getString(R.string.bio_auto), ServiceLocator.appPrefs.bioAuto, 4) { on ->
                ServiceLocator.appPrefs.bioAuto = on
            },
        )
        lp.header.addView(Ui.hint(ctx, getString(R.string.bio_auto_sub), 0))
        lp.header.addView(
            Ui.buttonRow(
                ctx,
                Ui.button(ctx, getString(R.string.pb_import), outlined = true, icon = R.drawable.ic_download) {
                    runCatching { pickCsv.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) }
                },
                Ui.button(ctx, getString(R.string.pb_export), outlined = true, icon = R.drawable.ic_backup) {
                    runCatching { saveCsv.launch("mahsulot_malumotlari.csv") }
                },
            ),
        )
        render()
        return lp.root
    }

    override fun onResume() {
        super.onResume()
        if (::lp.isInitialized) render()
    }

    private fun render() {
        val bio = ServiceLocator.bio
        val all = ServiceLocator.catalog.state.value.products
        val k = Translit.key(query.trim())
        shown = all.asSequence()
            .filter { k.isEmpty() || it.searchKey.contains(k) || it.idLower == k }
            .filter {
                when (filter) {
                    Filter.ALL -> true
                    Filter.WITH -> bio.has(it.id)
                    Filter.WITHOUT -> !bio.has(it.id)
                }
            }
            .take(400).toList()
        lp.adapter.submit(
            shown.map { p ->
                val b = bio.of(p.id)
                Ui.Row(
                    p.name,
                    getString(R.string.ad_product_code, p.id) + (if (b != null) " · " + b.text.take(40) else ""),
                    if (p.baseIsSom) ctx.money(p.wholesaleSom, Currency.SOM) else ctx.money(p.wholesaleUsd, Currency.USD),
                    icon = if (b != null) R.drawable.ic_info else R.drawable.ic_shopping_bag,
                    tone = if (b != null) 1 else 0,
                )
            },
        )
        lp.empty.text = getString(if (all.isEmpty()) R.string.pb_no_catalog else R.string.pb_nothing)
        lp.empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        val withBio = all.count { bio.has(it.id) }
        lp.toolbar.subtitle = getString(R.string.pb_count, all.size, withBio)
    }

    private fun openProduct(p: Product) {
        navigator().push(BioFragment.of(p.id, p.name))
    }

    // ---------------- v5.2 (A7): kompyuterda (Excel) yozish uchun CSV ----------------

    private val pickCsv = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importCsv(uri)
    }

    private val saveCsv = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) exportCsv(uri)
    }

    private fun exportCsv(uri: android.net.Uri) {
        val bio = ServiceLocator.bio
        val rows = ServiceLocator.catalog.state.value.products.map { p ->
            val x = bio.of(p.id)
            uz.imsoft.shared.bio.BioBook.CsvRow(p.id, p.name, x?.text.orEmpty(), x?.printText.orEmpty())
        }
        launchUi {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(uz.imsoft.shared.bio.BioBook.toCsv(rows).toByteArray()) }
                }.isSuccess
            }
            snack(getString(if (ok) R.string.pb_exported else R.string.pb_file_bad))
        }
    }

    private fun importCsv(uri: android.net.Uri) {
        launchUi {
            val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } }.getOrNull()
            }
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
}
