package uz.imsoft.mijoz.ui.login

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.imsoft.mijoz.Locator
import uz.imsoft.mijoz.R
import uz.imsoft.mijoz.ui.MainActivity
import uz.imsoft.shared.DeviceAssignment
import uz.imsoft.shared.android.Ui

/**
 * v4.0 Mijoz (kalitsiz): telefon o'zi Admin'ning "Qurilmalar" ro'yxatiga tushadi. Admin "Dadaxon aka" deb belgilashi
 * bilan ilova ochiladi — kod, telefon raqam, parol so'ralmaydi. Bloklangan telefonda — faqat shu ekran.
 */
class DeviceWaitFragment : Fragment() {
    private var tvStatus: TextView? = null
    private var tvTitle: TextView? = null
    private var done = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val p = Ui.dp(ctx, 24)
            setPadding(p, Ui.dp(ctx, 72), p, p)
            setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface))
            isClickable = true
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        root.addView(Ui.badge(ctx, R.drawable.ic_storefront, 1, 88).apply { (layoutParams as LinearLayout.LayoutParams).marginEnd = 0 })
        tvTitle = Ui.text(ctx, getString(R.string.dw_title), com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall, 20).apply { gravity = Gravity.CENTER }
        root.addView(tvTitle)
        root.addView(Ui.text(ctx, getString(R.string.dw_text), top = 12).apply { gravity = Gravity.CENTER })
        val code = Locator.device?.id?.take(6)?.uppercase().orEmpty()
        root.addView(Ui.text(ctx, getString(R.string.dw_phone, android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL, code), com.google.android.material.R.style.TextAppearance_Material3_TitleMedium, 20).apply { gravity = Gravity.CENTER })
        tvStatus = Ui.hint(ctx, getString(R.string.dw_wait), 16).apply { gravity = Gravity.CENTER }
        root.addView(tvStatus)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val d = Locator.device ?: return@repeatOnLifecycle
                d.cached()?.let { apply(it) }
                while (!done) {
                    val a = runCatching { d.sync() }
                    if (a.isFailure) tvStatus?.setText(R.string.dw_offline) else apply(a.getOrNull())
                    delay(4_000)
                }
            }
        }
    }

    private suspend fun apply(a: DeviceAssignment?) {
        when {
            a == null -> { tvTitle?.setText(R.string.dw_title); tvStatus?.setText(R.string.dw_wait) }
            a.blocked -> { tvTitle?.setText(R.string.dw_blocked); tvStatus?.setText(R.string.dw_blocked_sub) }
            else -> if (!done && Locator.applyDevice(a)) {
                done = true
                (activity as? MainActivity)?.let { act ->
                    act.updatePrinterTab()
                    if (Locator.prefs.printerSection && Locator.prefs.printerOn && !uz.imsoft.mijoz.printer.PrinterHub.state.value.running) {
                        runCatching { uz.imsoft.mijoz.printer.PrintService.start(act) }
                    }
                    act.closeLogin()
                }
            } else tvStatus?.setText(R.string.dw_wait)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tvStatus = null
        tvTitle = null
    }
}
