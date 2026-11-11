package com.ilxomjon.dur_novvot_dokon.ui.admin

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.ui.navigator
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.bio.Bio

/**
 * v5.2 (A7): bitta tovarning ma'lumoti (bio) — yozish, boshqa tovarga ko'chirish,
 * bir nechta tovarga ulash va Xprinterdan chiqarish.
 *
 * Matn 278 belgidan oshmaydi: 58x40 etiketkaga Arial 10 bilan shuncha sig'adi.
 * Shuning uchun qisqa yoziladi: "kunda 2max ochq 200g suvga 1ch.q" kabi.
 */
class BioFragment : CodeFragment() {
    private val productId: String get() = arguments?.getString(ARG_ID).orEmpty()
    private val productName: String get() = arguments?.getString(ARG_NAME).orEmpty()
    private var edt: TextInputEditText? = null
    private var counter: android.widget.TextView? = null
    private var bioId: String? = null

    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.bio_title)
        val c = p.content
        val cur: Bio? = ServiceLocator.bio.of(productId)
        bioId = cur?.id
        c.addView(Ui.title(ctx, productName, 0))
        c.addView(Ui.hint(ctx, getString(R.string.ad_product_code, productId)))
        c.addView(Ui.hint(ctx, getString(R.string.bio_rules), 10))

        val (til, e) = Ui.field(ctx, getString(R.string.bio_text), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, cur?.text.orEmpty())
        e.minLines = 5
        e.setHorizontallyScrolling(false)
        edt = e
        c.addView(til)
        counter = Ui.hint(ctx, "", 2).also { c.addView(it) }
        e.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = updateCounter()
            override fun beforeTextChanged(t: CharSequence?, a: Int, b: Int, cc: Int) = Unit
            override fun onTextChanged(t: CharSequence?, a: Int, b: Int, cc: Int) = Unit
        })
        updateCounter()

        c.addView(
            Ui.buttonRow(
                ctx,
                Ui.button(ctx, getString(R.string.bio_save), icon = R.drawable.ic_check) { save() },
                Ui.button(ctx, getString(R.string.bio_print), outlined = true, icon = R.drawable.ic_print) { printBio() },
            ),
        )
        if (cur != null) {
            c.addView(Ui.section(ctx, getString(R.string.bio_links_sec)))
            c.addView(Ui.hint(ctx, getString(R.string.bio_links, cur.products.size), 0))
            c.addView(
                Ui.item(ctx, getString(R.string.bio_attach), getString(R.string.bio_attach_sub), icon = R.drawable.ic_add) { attachMore() },
            )
            c.addView(
                Ui.item(ctx, getString(R.string.bio_move), getString(R.string.bio_move_sub), icon = R.drawable.ic_sync_alt) { moveTo() },
            )
            c.addView(
                Ui.item(ctx, getString(R.string.bio_unlink), getString(R.string.bio_unlink_sub), icon = R.drawable.ic_close) { unlink() },
            )
        }
        c.addView(Ui.section(ctx, getString(R.string.bio_copy_sec)))
        c.addView(Ui.item(ctx, getString(R.string.bio_from_other), getString(R.string.bio_from_other_sub), icon = R.drawable.ic_shopping_bag) { copyFrom() })
        return p.root
    }

    private fun text(): String = edt?.text?.toString()?.trim().orEmpty()

    private fun updateCounter() {
        val n = text().length
        counter?.text = getString(R.string.bio_count, n, Bio.MAX)
        counter?.setTextColor(
            com.google.android.material.color.MaterialColors.getColor(
                ctx,
                if (n > Bio.MAX) com.google.android.material.R.attr.colorError else com.google.android.material.R.attr.colorOnSurfaceVariant, 0,
            ),
        )
    }

    private fun save() {
        val t = text()
        if (t.isEmpty()) { snack(getString(R.string.bio_empty)); return }
        if (t.length > Bio.MAX) { snack(getString(R.string.bio_too_long, t.length - Bio.MAX)); return }
        launchUi {
            bioId = ServiceLocator.bio.save(bioId, t, listOf(productId))
            publish()
            snack(getString(R.string.bio_saved))
        }
    }

    /** Admin yozgan ma'lumot agentlarga ham borsin. */
    private suspend fun publish() {
        runCatching { ServiceLocator.bio.publish(ServiceLocator.secure.relayConfig, ServiceLocator.shareStore.secret) }
    }

    private fun attachMore() {
        val id = bioId ?: return
        Pickers.product(ctx) { p ->
            launchUi {
                ServiceLocator.bio.attach(id, listOf(p.id))
                publish()
                snack(getString(R.string.bio_attached, p.name))
                navigator().pop()
            }
        }
    }

    private fun moveTo() {
        Pickers.product(ctx) { p ->
            MaterialAlertDialogBuilder(ctx).setTitle(R.string.bio_move)
                .setMessage(getString(R.string.bio_move_ask, productName, p.name))
                .setPositiveButton(R.string.ok) { _, _ ->
                    launchUi {
                        ServiceLocator.bio.move(productId, p.id)
                        publish()
                        snack(getString(R.string.bio_moved, p.name))
                        navigator().pop()
                    }
                }
                .setNegativeButton(R.string.cancel, null).show()
        }
    }

    private fun unlink() {
        launchUi {
            ServiceLocator.bio.detach(productId)
            publish()
            snack(getString(R.string.bio_unlinked))
            navigator().pop()
        }
    }

    /** Boshqa tovarning ma'lumotini shu tovarga ham ulash (nusxa ko'paymaydi). */
    private fun copyFrom() {
        Pickers.product(ctx) { p ->
            val src = ServiceLocator.bio.of(p.id)
            if (src == null) { snack(getString(R.string.bio_src_none, p.name)); return@product }
            launchUi {
                ServiceLocator.bio.attach(src.id, listOf(productId))
                publish()
                snack(getString(R.string.bio_attached, productName))
                navigator().pop()
            }
        }
    }

    private fun printBio() {
        val t = text()
        if (t.isEmpty()) { snack(getString(R.string.bio_empty)); return }
        launchUi {
            val msg = runCatching { ServiceLocator.labels.printBio(productName, t) }
                .getOrElse { e -> getString(R.string.lg_error, e.message ?: e.javaClass.simpleName) }
            snack(msg)
        }
    }

    companion object {
        private const val ARG_ID = "id"
        private const val ARG_NAME = "name"

        fun of(id: String, name: String) = BioFragment().apply {
            arguments = Bundle().apply { putString(ARG_ID, id); putString(ARG_NAME, name) }
        }
    }
}
