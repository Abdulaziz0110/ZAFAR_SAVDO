package com.ilxomjon.dur_novvot_dokon.ui.login

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
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.local.RawAgent
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.imsoft.shared.DeviceAssignment
import uz.imsoft.shared.android.Ui

/**
 * v4.0 Agent (kalitsiz): telefon o'zi ro'yxatga tushadi, Admin "Qurilmalar"da kimligini belgilaydi — shu zahoti ilova
 * ochiladi. Parol, kod, sozlama yo'q. Bloklangan telefonda — faqat shu ekran.
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
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        root.addView(Ui.badge(ctx, R.drawable.ic_person, 1, 88).apply { (layoutParams as LinearLayout.LayoutParams).marginEnd = 0 })
        tvTitle = Ui.text(ctx, getString(R.string.dw_title), com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall, 20).apply { gravity = Gravity.CENTER }
        root.addView(tvTitle)
        root.addView(Ui.text(ctx, getString(R.string.dw_text), top = 12).apply { gravity = Gravity.CENTER })
        val d = ServiceLocator.device()
        val code = d?.id?.take(6)?.uppercase().orEmpty()
        root.addView(Ui.text(ctx, getString(R.string.dw_phone, android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL, code), com.google.android.material.R.style.TextAppearance_Material3_TitleMedium, 20).apply { gravity = Gravity.CENTER })
        tvStatus = Ui.hint(ctx, getString(R.string.dw_wait), 16).apply { gravity = Gravity.CENTER }
        root.addView(tvStatus)
        root.addView(Ui.button(ctx, getString(R.string.dw_other), outlined = true, top = 40) { parentFragmentManager.beginTransaction().replace(id, LoginFragment()).commit() }.apply { visibility = if (ServiceLocator.deviceMode) View.GONE else View.VISIBLE })
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val d = ServiceLocator.device() ?: return@repeatOnLifecycle
                // Internetsiz: oxirgi ma'lum biriktirish bilan darhol
                d.cached()?.let { apply(it) }
                while (!done) {
                    val a = runCatching { d.sync() }
                    if (a.isFailure) tvStatus?.setText(R.string.dw_offline)
                    else apply(a.getOrNull())
                    delay(4_000)
                }
            }
        }
    }

    private suspend fun apply(a: DeviceAssignment?) {
        when {
            a == null -> { tvStatus?.setText(R.string.dw_wait); tvTitle?.setText(R.string.dw_title) }
            a.blocked -> { tvStatus?.setText(R.string.dw_blocked_sub); tvTitle?.setText(R.string.dw_blocked) }
            a.agent.size >= 2 && a.agentId.isNotEmpty() -> {
                if (done) return
                done = true
                val ag = a.agent
                ServiceLocator.auth.loginAs(RawAgent(ag[0], ag.getOrNull(1).orEmpty(), ag.getOrNull(2).orEmpty(), ag.getOrNull(3).orEmpty()))
                if (isAdded) navigator().onLoggedIn()
            }
            else -> tvStatus?.setText(R.string.dw_wait)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tvStatus = null
        tvTitle = null
    }
}
