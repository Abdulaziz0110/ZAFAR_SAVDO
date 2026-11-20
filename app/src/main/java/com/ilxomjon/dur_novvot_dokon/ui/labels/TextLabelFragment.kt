package com.ilxomjon.dur_novvot_dokon.ui.labels

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.ilxomjon.dur_novvot_dokon.R
import com.ilxomjon.dur_novvot_dokon.core.ServiceLocator
import com.ilxomjon.dur_novvot_dokon.data.labels.TextLabel
import com.ilxomjon.dur_novvot_dokon.ui.admin.CodeFragment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.imsoft.shared.android.Ui
import uz.imsoft.shared.label.TextLabels
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v5.3.3: Sozlamalar -> Yorliqlar -> Matnli etiketka.
 * 58x40 etiketkaga Arial 14 bilan erkin matn chiqariladi. Har bir etiketka tarixda saqlanadi:
 * qayta chiqarish, tahrirlash va o'chirish mumkin. Printerga ulanish (LAN/LPD/bulut, Tailscale) o'zgarmagan.
 */
class TextLabelFragment : CodeFragment() {
    private var editId = 0L
    private var previewJob: Job? = null
    private lateinit var scroll: NestedScrollView
    private lateinit var input: TextInputEditText
    private lateinit var copies: TextInputEditText
    private lateinit var preview: ImageView
    private lateinit var banner: TextView
    private lateinit var history: LinearLayout
    private lateinit var printBtn: MaterialButton
    private lateinit var newBtn: MaterialButton

    override fun build(inflater: LayoutInflater): View {
        val p = page(R.string.tl_title)
        scroll = p.scroll
        val c = p.content
        c.addView(Ui.hint(ctx, getString(R.string.tl_hint)))
        banner = Ui.hint(ctx, "", 8)
        banner.visibility = View.GONE
        c.addView(banner)

        val flags = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        val (til, e) = Ui.field(ctx, getString(R.string.tl_text_hint), flags, "", 8)
        e.minLines = 4
        e.maxLines = 10
        e.setHorizontallyScrolling(false)
        e.gravity = Gravity.TOP or Gravity.START
        e.addTextChangedListener(watcher { schedulePreview() })
        input = e
        c.addView(til)

        val (til2, e2) = Ui.field(ctx, getString(R.string.tl_copies_hint), InputType.TYPE_CLASS_NUMBER, "1", 8)
        copies = e2
        c.addView(til2)

        c.addView(Ui.section(ctx, getString(R.string.tl_preview)))
        preview = ImageView(ctx)
        preview.adjustViewBounds = true
        preview.scaleType = ImageView.ScaleType.FIT_CENTER
        preview.setBackgroundColor(android.graphics.Color.WHITE)
        preview.contentDescription = getString(R.string.tl_preview)
        val pl = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        pl.topMargin = Ui.dp(ctx, 4)
        c.addView(preview, pl)

        printBtn = Ui.button(ctx, getString(R.string.tl_print), icon = R.drawable.ic_print) { onPrint() }
        val saveBtn = Ui.button(ctx, getString(R.string.tl_save), outlined = true, icon = R.drawable.ic_check) { onSave() }
        c.addView(Ui.buttonRow(ctx, printBtn, saveBtn, top = 12))
        newBtn = Ui.button(ctx, getString(R.string.tl_new), outlined = true, icon = R.drawable.ic_add) { clearEditor() }
        newBtn.visibility = View.GONE
        c.addView(newBtn)

        c.addView(Ui.section(ctx, getString(R.string.tl_history)))
        history = LinearLayout(ctx)
        history.orientation = LinearLayout.VERTICAL
        c.addView(history)
        return p.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        schedulePreview()
        loadHistory()
    }

    private fun watcher(run: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = run()
    }

    private fun read(): Pair<String, Int> =
        TextLabels.clean(input.text?.toString().orEmpty()) to TextLabels.copies(copies.text?.toString()?.trim()?.toIntOrNull())

    /** Matn o'zgargach 0,25 soniyadan keyin etiketka ko'rinishini qayta chizadi (chop etilmaydi). */
    private fun schedulePreview() {
        previewJob?.cancel()
        previewJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(250)
            val text = TextLabels.clean(input.text?.toString().orEmpty())
            val bmp = try {
                withContext(Dispatchers.Default) { ServiceLocator.labels.previewText(text) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (bmp != null) preview.setImageBitmap(bmp)
        }
    }

    private fun setEditing(id: Long) {
        editId = id
        val on = id > 0
        banner.visibility = if (on) View.VISIBLE else View.GONE
        if (on) banner.text = getString(R.string.tl_editing, id)
        newBtn.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun clearEditor() {
        setEditing(0L)
        input.setText("")
        copies.setText("1")
    }

    private fun onSave() {
        val (text, n) = read()
        if (text.isEmpty()) {
            snack(getString(R.string.tl_empty_text))
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val id = withContext(Dispatchers.IO) { ServiceLocator.textLabels.save(editId, text, n) }
                setEditing(id)
                loadHistory()
                snack(getString(R.string.tl_saved))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isAdded) snack(getString(R.string.ad_error, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    /** Saqlaydi va chop etadi. Muvaffaqiyatli bo'lsa, maydon tozalanadi (keyingi etiketka yangi yozuv bo'ladi). */
    private fun onPrint() {
        val (text, n) = read()
        if (text.isEmpty()) {
            snack(getString(R.string.tl_empty_text))
            return
        }
        printBtn.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val id = withContext(Dispatchers.IO) { ServiceLocator.textLabels.save(editId, text, n) }
                setEditing(id)
                if (printSaved(id, text, n)) clearEditor()
                loadHistory()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isAdded) snack(getString(R.string.ad_error, e.message ?: e.javaClass.simpleName))
            } finally {
                printBtn.isEnabled = true
            }
        }
    }

    /** Saqlangan etiketkani chop etadi; muvaffaqiyatli bo'lsa, "necha marta chiqarilgan" hisoblagichini oshiradi. */
    private suspend fun printSaved(id: Long, text: String, n: Int): Boolean = try {
        val msg = ServiceLocator.labels.printText(text, n)
        withContext(Dispatchers.IO) { ServiceLocator.textLabels.markPrinted(id) }
        if (isAdded) snack(msg)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (isAdded) snack(getString(R.string.tl_print_failed, ctx.labelErrorText(e)))
        false
    }

    private fun loadHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            val items = try {
                withContext(Dispatchers.IO) { ServiceLocator.textLabels.all() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            history.removeAllViews()
            if (items.isEmpty()) {
                history.addView(Ui.hint(ctx, getString(R.string.tl_empty), 8))
                return@launch
            }
            val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            items.forEach { t ->
                val sub = if (t.prints > 0) {
                    getString(R.string.tl_item_sub, fmt.format(Date(if (t.printedAt > 0) t.printedAt else t.created)), t.copies, t.prints)
                } else {
                    getString(R.string.tl_item_unprinted, fmt.format(Date(t.created)), t.copies)
                }
                history.addView(Ui.item(ctx, TextLabels.summary(t.text, 70), sub, icon = R.drawable.ic_label) { showActions(t) })
            }
        }
    }

    private fun showActions(t: TextLabel) {
        MaterialAlertDialogBuilder(ctx)
            .setTitle(TextLabels.summary(t.text, 40))
            .setItems(
                arrayOf(getString(R.string.tl_act_reprint), getString(R.string.tl_act_edit), getString(R.string.tl_act_delete)),
            ) { _, which ->
                when (which) {
                    0 -> reprint(t)
                    1 -> edit(t)
                    else -> confirmDelete(t)
                }
            }
            .show()
    }

    private fun reprint(t: TextLabel) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                printSaved(t.id, t.text, t.copies)
                loadHistory()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isAdded) snack(getString(R.string.ad_error, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun edit(t: TextLabel) {
        setEditing(t.id)
        input.setText(t.text)
        copies.setText(t.copies.toString())
        input.setSelection(input.text?.length ?: 0)
        scroll.smoothScrollTo(0, 0)
    }

    private fun confirmDelete(t: TextLabel) {
        MaterialAlertDialogBuilder(ctx)
            .setMessage(getString(R.string.tl_delete_q))
            .setNegativeButton(R.string.tl_cancel, null)
            .setPositiveButton(R.string.tl_act_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) { ServiceLocator.textLabels.delete(t.id) }
                        if (editId == t.id) clearEditor()
                        loadHistory()
                        if (isAdded) snack(getString(R.string.tl_deleted))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (isAdded) snack(getString(R.string.ad_error, e.message ?: e.javaClass.simpleName))
                    }
                }
            }
            .show()
    }
}
