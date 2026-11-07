package com.ilxomjon.dur_novvot_dokon.ui.share

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.EditText
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.databinding.FragmentReceiptStyleBinding
import com.ilxomjon.dur_novvot_dokon.ui.ledger.ReceiptActions
import com.ilxomjon.dur_novvot_dokon.ui.ledgerhost.DokonLedgerHost
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import com.ilxomjon.dur_novvot_dokon.ui.onTextChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.imsoft.shared.android.LogoCache
import uz.imsoft.shared.android.ReceiptBitmap
import uz.imsoft.shared.receipt.ReceiptStyle

/**
 * Chek ko'rinishi: tepada jonli namuna, pastda sozlamalar. Har o'zgarish darhol saqlanadi va
 * 2-ilovalarga keyingi yuborishda boradi (ular ham shu ko'rinishda chop etadi).
 */
class ReceiptStyleFragment : Fragment() {
    private var _b: FragmentReceiptStyleBinding? = null
    private val b get() = _b!!
    private val repo get() = ServiceLocator.share
    private var style = ReceiptStyle.STANDARD
    /** Ekranni to'ldirayotganda tinglovchilar ishlamasin. */
    private var filling = false
    private var renderJob: Job? = null
    private var preview: Bitmap? = null

    private val pickLogo = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) loadLogo(uri) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentReceiptStyleBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.toolbar.setNavigationOnClickListener { navigator().pop() }
        b.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_reset) {
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.rs_reset).setMessage(R.string.rs_reset_msg)
                    .setPositiveButton(R.string.ok) { _, _ -> apply(ReceiptStyle.STANDARD) }
                    .setNegativeButton(R.string.cancel, null).show()
                true
            } else false
        }
        style = repo.receiptStyle
        val st = ServiceLocator.shareStore
        b.edtStaff.setText(st.receiptStaff)
        b.edtFooter.setText(st.receiptFooter)
        fill()

        b.tgPaper.addOnButtonCheckedListener { _, id, checked -> if (checked && !filling) change { it.copy(paper = if (id == R.id.btnPaper58) 58 else 80) } }
        sw(b.swLogo) { s, v -> s.copy(logo = v) }
        slider(b.sLogoWidth) { s, v -> s.copy(logoWidth = v) }
        slider(b.sDark) { s, v -> s.copy(dark = v) }
        slider(b.sFeed) { s, v -> s.copy(feed = v) }
        b.edtStaff.onTextChanged { if (!filling) schedule(saveTexts = true) }
        b.edtFooter.onTextChanged { if (!filling) schedule(saveTexts = true) }

        b.btnLogoPick.setOnClickListener { pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        b.btnLogoRemove.setOnClickListener {
            repo.setReceiptLogo(null)
            showLogo()
            schedule()
        }
        b.btnTestPrint.setOnClickListener { testPrint() }
        b.btnShareSample.setOnClickListener {
            saveTexts()
            val r = repo.mirror.sampleReceipt()
            viewLifecycleOwner.lifecycleScope.launch { ReceiptActions.shareImage(requireContext(), r, preview, DokonLedgerHost) }
        }
        showLogo()
        schedule(0)
    }

    /** Uslubni ekranga yozish (tinglovchilarsiz). */
    private fun fill() {
        filling = true
        val s = style
        b.tgPaper.check(if (s.paper == 58) R.id.btnPaper58 else R.id.btnPaper80)
        b.swLogo.isChecked = s.logo
        b.sLogoWidth.value = (s.logoWidth / 5 * 5).toFloat()
        b.sDark.value = (s.dark / 10 * 10).toFloat().coerceIn(100f, 220f)
        b.sFeed.value = (s.feed / 2 * 2).toFloat()
        labels()
        filling = false
    }

    private fun labels() {
        val s = style
        b.tvLogoWidth.text = getString(R.string.rs_logo_width, s.logoWidth)
        b.tvDark.text = getString(R.string.rs_dark, (s.dark - 100) / 10 + 1)
        b.tvFeed.text = getString(R.string.rs_feed, s.feed)
    }

    private fun apply(s: ReceiptStyle) {
        style = s.normalized()
        fill()
        schedule()
    }

    private fun change(f: (ReceiptStyle) -> ReceiptStyle) {
        style = f(style).normalized()
        labels()
        schedule()
    }

    private fun sw(v: CompoundButton, f: (ReceiptStyle, Boolean) -> ReceiptStyle) =
        v.setOnCheckedChangeListener { _, on -> if (!filling) change { f(it, on) } }

    private fun slider(v: Slider, f: (ReceiptStyle, Int) -> ReceiptStyle) =
        v.addOnChangeListener { _, value, fromUser -> if (fromUser && !filling) change { f(it, value.toInt()) } }

    private fun text(v: EditText, f: (ReceiptStyle, String) -> ReceiptStyle) =
        v.onTextChanged { if (!filling) { style = f(style, v.text?.toString().orEmpty()); schedule() } }

    private fun saveTexts() {
        val bb = _b ?: return
        repo.setReceiptStaff(bb.edtStaff.text?.toString().orEmpty())
        repo.setReceiptFooter(bb.edtFooter.text?.toString().orEmpty())
    }

    /** Saqlash va namunani qayta chizish (tez-tez o'zgarganda bir marta). */
    private fun schedule(wait: Long = 180, saveTexts: Boolean = false) {
        renderJob?.cancel()
        renderJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(wait)
            if (saveTexts) saveTexts()
            repo.setReceiptStyle(style)
            val s = style
            val logo = LogoCache.get(ServiceLocator.shareStore.receiptLogo)
            val r = repo.mirror.sampleReceipt()
            val bmp = withContext(Dispatchers.Default) { ReceiptBitmap.render(r, s, logo) }
            val bb = _b ?: return@launch
            preview = bmp
            bb.ivPreview.setImageBitmap(bmp)
        }
    }

    private fun showLogo() {
        val bmp = LogoCache.get(ServiceLocator.shareStore.receiptLogo)
        b.ivLogo.setImageBitmap(bmp)
        b.btnLogoRemove.isEnabled = bmp != null
        b.swLogo.isEnabled = bmp != null
        b.sLogoWidth.isEnabled = bmp != null
    }

    private fun loadLogo(uri: Uri) {
        val ctx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val png = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                    ?.takeIf { it.size <= 30_000_000 }?.let { ReceiptBitmap.normalizeLogo(it) }
            }
            val bb = _b ?: return@launch
            if (png == null) {
                Snackbar.make(bb.root, R.string.rs_logo_bad, Snackbar.LENGTH_LONG).show()
                return@launch
            }
            repo.setReceiptLogo(png)
            if (!style.logo) { style = style.copy(logo = true); fill() }
            showLogo()
            schedule(0)
        }
    }

    private fun testPrint() {
        saveTexts()
        val ctx = requireContext()
        val targets = DokonLedgerHost.printTargets(ctx)
        val root = b.root
        if (targets.isEmpty()) {
            Snackbar.make(root, R.string.rs_no_printer, Snackbar.LENGTH_LONG).show()
            return
        }
        val s = style
        val act = requireActivity()
        act.lifecycleScope.launch {
            val msg = runCatching {
                val bytes = withContext(Dispatchers.Default) {
                    ReceiptBitmap.toEscPos(ReceiptBitmap.render(repo.mirror.sampleReceipt(), s, LogoCache.get(ServiceLocator.shareStore.receiptLogo)), s.dark)
                }
                targets[0].send(bytes)
            }.getOrElse { e -> act.getString(R.string.lg_error, e.message ?: e.javaClass.simpleName) }
            Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        saveTexts()
        repo.setReceiptStyle(style)
        super.onPause()
    }

    override fun onDestroyView() {
        renderJob?.cancel()
        _b = null
        super.onDestroyView()
    }
}
