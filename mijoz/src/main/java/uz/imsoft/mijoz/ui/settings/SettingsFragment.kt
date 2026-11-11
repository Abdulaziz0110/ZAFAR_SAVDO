package uz.imsoft.mijoz.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.databinding.FragmentSettingsBinding
import uz.imsoft.mijoz.printer.PrintService
import uz.imsoft.mijoz.ui.MainActivity
import uz.imsoft.mijoz.ui.launchOnStarted
import uz.imsoft.mijoz.ui.describe
import uz.imsoft.mijoz.ui.show

class SettingsFragment : Fragment() {
    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!
    private var rendering = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        uz.imsoft.mijoz.ui.tour.Tour.show(this, uz.imsoft.mijoz.ui.tour.Tour.SETTINGS)
        val p = Locator.prefs
        render()
        b.btnPaste.setText(R.string.lgn_login_other)
        b.btnPaste.setOnClickListener { (activity as? uz.imsoft.mijoz.ui.MainActivity)?.showLogin() }
        b.btnUnlink.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.link_remove).setMessage(R.string.link_remove_msg)
                .setPositiveButton(R.string.ok) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        PrintService.stop(requireContext())
                        Locator.data.unlink()
                        Locator.login.logout()
                        render()
                        (activity as? uz.imsoft.mijoz.ui.MainActivity)?.showLogin()
                    }
                }
                .setNegativeButton(R.string.cancel, null).show()
        }
        onToggle(b.tgLang) { id -> p.language = if (id == R.id.langCyr) "cyr" else "lat"; Locator.applyUi() }
        onToggle(b.tgTheme) { id -> p.theme = when (id) { R.id.themeLight -> "light"; R.id.themeDark -> "dark"; else -> "system" }; Locator.applyUi() }
        onToggle(b.tgScale) { id ->
            p.uiScale = when (id) { R.id.scale90 -> 0.9f; R.id.scale110 -> 1.1f; R.id.scale120 -> 1.2f; else -> 1.0f }
            requireActivity().recreate()
        }
        onToggle(b.tgAuto) { id ->
            p.autoMinutes = when (id) { R.id.auto60 -> 60; R.id.auto120 -> 120; else -> 0 }
            uz.imsoft.mijoz.data.MijozAuto.apply(requireContext().applicationContext)
        }
        b.swNotify.setOnCheckedChangeListener { _, on -> if (!rendering) { p.notifyNew = on; if (on) askNotify() } }
        onToggle(b.tgFont) { id ->
            p.fontScale = when (id) { R.id.font90 -> 0.9f; R.id.font115 -> 1.15f; else -> 1.0f }
            requireActivity().recreate()
        }
        b.swAutoRefresh.setOnCheckedChangeListener { _, on -> if (!rendering) p.autoRefresh = on }
        b.swFuzzy.setOnCheckedChangeListener { _, on -> if (!rendering) p.fuzzy = on }
        b.swPrinter.setOnCheckedChangeListener { _, on ->
            if (rendering) return@setOnCheckedChangeListener
            p.printerSection = on
            if (!on) PrintService.stop(requireContext())
            (activity as? MainActivity)?.updatePrinterTab()
        }
        b.swSaleReceipts.setOnCheckedChangeListener { sw, on ->
            if (rendering) return@setOnCheckedChangeListener
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    uz.imsoft.mijoz.data.SaleReceipts.set(on)
                    if (on && !uz.imsoft.mijoz.printer.PrinterHub.state.value.running) {
                        MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.sr_need_service).setPositiveButton(R.string.ok, null).show()
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    rendering = true; sw.isChecked = !on; rendering = false
                    MaterialAlertDialogBuilder(requireContext()).setMessage(getString(R.string.sr_failed) + "\n" + requireContext().describe(e)).setPositiveButton(R.string.ok, null).show()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            uz.imsoft.mijoz.data.SaleReceipts.fetch()?.let { v -> _b?.let { rendering = true; it.swSaleReceipts.isChecked = v; rendering = false } }
        }
        onToggle(b.tgSkip) { id -> p.skipOlderDays = when (id) { R.id.skip1 -> 1; R.id.skip3 -> 3; R.id.skip7 -> 7; else -> 0 } }
        val version = runCatching { requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName }.getOrNull().orEmpty()
        b.tvAbout.text = getString(R.string.st_about_text, version)
        addV32()
        // Sirli menyu: versiya yozuvini uzoq bosish
        b.tvAbout.setOnLongClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            (activity as? uz.imsoft.mijoz.ui.MainActivity)?.push(MijozSecretFragment())
            true
        }
        launchOnStarted { Locator.data.state.collect { render() } }
    }

    private val notifPerm = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    private fun askNotify() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun onToggle(g: MaterialButtonToggleGroup, block: (Int) -> Unit) {
        g.addOnButtonCheckedListener { _, id, checked -> if (checked && !rendering) block(id) }
    }

    /**
     * v3.2: "Avto-yangilash va zaxira", "Chek qo'shimchalari", "Kassa" bandlari; kam ishlatiladiganlar (o'lcham, shrift,
     * aqlli qidiruv, eski yozuvlar) "Ko'proq sozlamalar" ostida.
     */
    private fun addV32() {
        val column = b.tvAbout.parent as? ViewGroup ?: return
        if (column.findViewWithTag<View>("v32") != null) return
        val ctx = requireContext()
        fun top(v: View): View? {
            var cur: View = v
            while (true) {
                val p = cur.parent as? View ?: return null
                if (p === column) return cur
                cur = p
            }
        }
        val (card, col) = uz.imsoft.shared.android.Ui.card(ctx, 12)
        card.tag = "v32"
        col.addView(uz.imsoft.shared.android.Ui.item(ctx, getString(R.string.au_title), getString(R.string.au_sub), icon = R.drawable.ic_download) {
            (activity as? MainActivity)?.push(uz.imsoft.mijoz.ui.extra.AutoFragment())
        })
        col.addView(uz.imsoft.shared.android.Ui.divider(ctx))
        col.addView(uz.imsoft.shared.android.Ui.item(ctx, getString(R.string.rx_title), getString(R.string.rx_sub_title), icon = R.drawable.ic_receipt) {
            (activity as? MainActivity)?.push(uz.imsoft.mijoz.ui.extra.ReceiptExtrasFragment())
        })
        col.addView(uz.imsoft.shared.android.Ui.divider(ctx))
        // v5.2 (A7): savdo paytida mahsulot ma'lumoti o'zi ochilsinmi
        col.addView(
            uz.imsoft.shared.android.Ui.switch(ctx, getString(R.string.bio_auto), Locator.prefs.bioAuto, 4) { on ->
                Locator.prefs.bioAuto = on
            },
        )
        col.addView(uz.imsoft.shared.android.Ui.hint(ctx, getString(R.string.bio_auto_sub), 0))
        col.addView(uz.imsoft.shared.android.Ui.divider(ctx))
        // v5.0.4: bosh ekrandagi vidjetning 6 ta tugmasi
        col.addView(uz.imsoft.shared.android.Ui.item(ctx, getString(R.string.wgs_title), getString(R.string.wgs_sub), icon = R.drawable.ic_widgets) {
            (activity as? MainActivity)?.push(uz.imsoft.mijoz.ui.extra.WidgetFragment())
        })
        col.addView(uz.imsoft.shared.android.Ui.divider(ctx))
        val first = top(b.tgAuto)
        column.addView(card, if (first != null) column.indexOfChild(first) else column.indexOfChild(b.tvAbout))
        val advanced = listOfNotNull(top(b.tgScale), top(b.tgFont), top(b.swFuzzy), top(b.tgSkip)).distinct()
            .filter { it !== top(b.tgAuto) && it !== top(b.swPrinter) && it !== top(b.tgLang) }
        if (advanced.isEmpty()) return
        var open = false
        advanced.forEach { it.show(false) }
        val more = uz.imsoft.shared.android.Ui.button(ctx, getString(R.string.st_more), outlined = true, top = 12, icon = R.drawable.ic_expand_more) { v ->
            open = !open
            advanced.forEach { it.show(open) }
            (v as? com.google.android.material.button.MaterialButton)?.text = getString(if (open) R.string.st_less else R.string.st_more)
        }
        column.addView(more, column.indexOfChild(b.tvAbout))
    }

    private fun render() {
        val bb = _b ?: return
        val p = Locator.prefs
        rendering = true
        val link = p.link
        bb.tvLink.text = if (link == null) getString(R.string.link_none)
        else getString(R.string.link_info, link.clientName.ifEmpty { "—" }, if (link.queueKey.isNotEmpty()) getString(R.string.link_with_printer) else "") +
            if (p.loginPhone.isNotEmpty()) "\n" + getString(R.string.lgn_as, uz.imsoft.shared.Phone.pretty(p.loginPhone), p.memberName.ifBlank { "—" }) else 
        bb.btnUnlink.isEnabled = link != null
        bb.tgLang.check(if (p.language == "cyr") R.id.langCyr else R.id.langLat)
        bb.tgTheme.check(when (p.theme) { "light" -> R.id.themeLight; "dark" -> R.id.themeDark; else -> R.id.themeSystem })
        bb.tgScale.check(when (p.uiScale) { 0.9f -> R.id.scale90; 1.1f -> R.id.scale110; 1.2f -> R.id.scale120; else -> R.id.scale100 })
        bb.tgAuto.check(when (p.autoMinutes) { 60 -> R.id.auto60; 120 -> R.id.auto120; 0 -> R.id.auto0; else -> R.id.auto60 })
        bb.swNotify.isChecked = p.notifyNew
        bb.tgFont.check(when (p.fontScale) { 0.9f -> R.id.font90; 1.15f -> R.id.font115; else -> R.id.font100 })
        bb.swAutoRefresh.isChecked = p.autoRefresh
        bb.swFuzzy.isChecked = p.fuzzy
        bb.swPrinter.isChecked = p.printerSection
        bb.swSaleReceipts.isChecked = p.saleReceipts
        bb.swSaleReceipts.isEnabled = uz.imsoft.mijoz.data.SaleReceipts.available
        bb.tvSaleReceipts.setText(if (uz.imsoft.mijoz.data.SaleReceipts.available) R.string.sr_hint else R.string.sr_no_queue)
        bb.tgSkip.check(when (p.skipOlderDays) { 1 -> R.id.skip1; 3 -> R.id.skip3; 7 -> R.id.skip7; else -> R.id.skip0 })
        rendering = false
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) { uz.imsoft.mijoz.ui.tour.Tour.show(this, uz.imsoft.mijoz.ui.tour.Tour.SETTINGS) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
