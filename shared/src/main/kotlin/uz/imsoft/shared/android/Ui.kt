package uz.imsoft.shared.android

import android.content.Context
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.core.widget.TextViewCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Kod bilan ekran yig'ish (v3.2 yangi ekranlar uchun). Matnlar chaqiruvchidan keladi (strings.xml), bu yerda matn yo'q.
 * Ko'rinish ilovaning Material3 mavzusidan olinadi — eski ekranlar bilan bir xil.
 */
object Ui {
    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    private fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, top: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { topMargin = top }

    class Page(val root: LinearLayout, val toolbar: MaterialToolbar, val content: LinearLayout, val scroll: NestedScrollView)

    /** Yuqorida orqaga tugmali sarlavha, pastda aylantiriladigan ustun. */
    fun page(ctx: Context, title: String, backIcon: Int, onBack: () -> Unit): Page {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val tb = MaterialToolbar(ctx).apply {
            this.title = title
            // v5.0: backIcon = 0 — ildiz bo'lim (pastki menyudan ochilgan), orqaga tugmasi kerak emas
            if (backIcon != 0) {
                setNavigationIcon(backIcon)
                setNavigationOnClickListener { onBack() }
            }
        }
        root.addView(tb, lp())
        val scroll = NestedScrollView(ctx).apply { isFillViewport = true }
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(ctx, 16)
            setPadding(p, dp(ctx, 4), p, dp(ctx, 24))
        }
        scroll.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return Page(root, tb, content, scroll)
    }

    fun text(ctx: Context, s: CharSequence, style: Int = com.google.android.material.R.style.TextAppearance_Material3_BodyMedium, top: Int = 0): TextView =
        TextView(ctx).apply {
            TextViewCompat.setTextAppearance(this, style)
            text = s
            layoutParams = lp(top = dp(ctx, top))
        }

    fun title(ctx: Context, s: CharSequence, top: Int = 12): TextView = text(ctx, s, com.google.android.material.R.style.TextAppearance_Material3_TitleMedium, top)

    fun hint(ctx: Context, s: CharSequence, top: Int = 4): TextView = text(ctx, s, com.google.android.material.R.style.TextAppearance_Material3_BodySmall, top).apply {
        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
    }

    fun section(ctx: Context, s: CharSequence): TextView = text(ctx, s, com.google.android.material.R.style.TextAppearance_Material3_LabelLarge, 20).apply {
        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary))
    }

    /** Ichida vertikal ustun bo'lgan karta. */
    fun card(ctx: Context, top: Int = 8): Pair<MaterialCardView, LinearLayout> {
        val card = MaterialCardView(ctx, null, com.google.android.material.R.attr.materialCardViewOutlinedStyle)
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(ctx, 14)
            setPadding(p, p, p, p)
        }
        card.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        card.layoutParams = lp(top = dp(ctx, top))
        return card to col
    }

    /**
     * v5.0: sozlamalar plitkasi (2 ustunli to'r). [help] — uzoq bosilganda chiqadigan to'liq tushuntirish.
     * Nomi ikki qatorgacha, uzun bo'lsa qisqaradi.
     */
    class Tile(val title: CharSequence, val icon: Int, val help: CharSequence = "", val tone: Int = 0, val onClick: () -> Unit)

    fun tiles(ctx: Context, items: List<Tile>, columns: Int = 2, top: Int = 8, onHelp: ((Tile) -> Unit)? = null): LinearLayout {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = lp(top = dp(ctx, top))
        }
        val gap = dp(ctx, 8)
        for (row in items.chunked(columns)) {
            val r = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for ((i, t) in row.withIndex()) {
                r.addView(tileView(ctx, t, onHelp), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i > 0) marginStart = gap
                    bottomMargin = gap
                })
            }
            repeat(columns - row.size) { i ->
                r.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = gap })
            }
            box.addView(r)
        }
        return box
    }

    private fun tileView(ctx: Context, t: Tile, onHelp: ((Tile) -> Unit)?): View {
        val card = MaterialCardView(ctx, null, com.google.android.material.R.attr.materialCardViewOutlinedStyle).apply {
            radius = dp(ctx, 16).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { t.onClick() }
            if (t.help.isNotEmpty() && onHelp != null) setOnLongClickListener { onHelp(t); true }
        }
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(ctx, 12)
            setPadding(p, p, p, p)
            minimumHeight = dp(ctx, 92)
        }
        if (t.icon != 0) col.addView(badge(ctx, t.icon, t.tone, 36))
        col.addView(
            text(ctx, t.title, com.google.android.material.R.style.TextAppearance_Material3_TitleSmall, 8).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            },
        )
        card.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        return card
    }

    fun button(ctx: Context, s: CharSequence, outlined: Boolean = false, top: Int = 8, icon: Int = 0, onClick: (View) -> Unit): MaterialButton {
        val b = if (outlined) MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle) else MaterialButton(ctx)
        b.text = s
        b.maxLines = 2
        if (icon != 0) b.setIconResource(icon)
        b.setOnClickListener { onClick(it) }
        b.layoutParams = lp(top = dp(ctx, top))
        return b
    }

    /** Bir qatorda bir nechta tugma (teng enli). */
    fun buttonRow(ctx: Context, vararg buttons: MaterialButton, top: Int = 8): LinearLayout {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = lp(top = dp(ctx, top)) }
        buttons.forEachIndexed { i, b ->
            row.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = dp(ctx, 8) })
        }
        return row
    }

    fun field(ctx: Context, hint: CharSequence, type: Int = InputType.TYPE_CLASS_TEXT, value: String = "", top: Int = 8): Pair<TextInputLayout, TextInputEditText> {
        val til = TextInputLayout(ctx, null, com.google.android.material.R.attr.textInputOutlinedStyle)
        til.hint = hint
        val e = TextInputEditText(til.context)
        e.inputType = type
        e.setText(value)
        // v5.0: pul va miqdor maydonlarida minglik ajraladi ("12 000"); PLU/shtrix kod/telefon — tegilmaydi
        if (type and InputType.TYPE_NUMBER_FLAG_DECIMAL != 0) NumInput.attach(e)
        if (type and InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0) e.maxLines = 1
        til.addView(e, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        til.layoutParams = lp(top = dp(ctx, top))
        return til to e
    }

    const val NUMBER = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    const val PHONE = InputType.TYPE_CLASS_PHONE

    fun switch(ctx: Context, s: CharSequence, checked: Boolean, top: Int = 8, onChange: (Boolean) -> Unit): MaterialSwitch =
        MaterialSwitch(ctx).apply {
            text = s
            isChecked = checked
            setOnCheckedChangeListener { _, v -> onChange(v) }
            layoutParams = lp(top = dp(ctx, top))
        }

    /**
     * v3.12: ro'yxat qatori boshidagi ikonka — rangli doira ichida (bir qarashda tushunarli bo'lsin).
     * [tone]: 0 — asosiy (secondary), 1 — primary, 2 — tertiary, 3 — xato (qizil).
     */
    fun badge(ctx: Context, icon: Int, tone: Int = 0, size: Int = 40): FrameLayout {
        val (bgAttr, fgAttr) = when (tone) {
            1 -> com.google.android.material.R.attr.colorPrimaryContainer to com.google.android.material.R.attr.colorOnPrimaryContainer
            2 -> com.google.android.material.R.attr.colorTertiaryContainer to com.google.android.material.R.attr.colorOnTertiaryContainer
            3 -> com.google.android.material.R.attr.colorErrorContainer to com.google.android.material.R.attr.colorOnErrorContainer
            else -> com.google.android.material.R.attr.colorSecondaryContainer to com.google.android.material.R.attr.colorOnSecondaryContainer
        }
        val box = FrameLayout(ctx)
        box.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(MaterialColors.getColor(box, bgAttr))
        }
        val iv = android.widget.ImageView(ctx).apply {
            setImageResource(icon)
            imageTintList = android.content.res.ColorStateList.valueOf(MaterialColors.getColor(box, fgAttr))
        }
        val s = dp(ctx, size * 11 / 20)
        box.addView(iv, FrameLayout.LayoutParams(s, s, Gravity.CENTER))
        box.layoutParams = LinearLayout.LayoutParams(dp(ctx, size), dp(ctx, size)).apply { marginEnd = dp(ctx, 14) }
        return box
    }

    /** Ro'yxat qatori: (ikonka), sarlavha, izoh va o'ngda qiymat (ixtiyoriy). Bosiladi. */
    fun item(ctx: Context, title: CharSequence, sub: CharSequence = "", value: CharSequence = "", icon: Int = 0, tone: Int = 0, onClick: ((View) -> Unit)? = null): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = dp(ctx, 10)
            setPadding(dp(ctx, 4), p, dp(ctx, 4), p)
            layoutParams = lp()
            minimumHeight = dp(ctx, 48)
            if (onClick != null) {
                isClickable = true
                isFocusable = true
                val tv = TypedValue()
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                setBackgroundResource(tv.resourceId)
                setOnClickListener { onClick(it) }
            }
        }
        if (icon != 0) row.addView(badge(ctx, icon, tone))
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text(ctx, title, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge))
        if (sub.isNotEmpty()) col.addView(hint(ctx, sub, 2))
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (value.isNotEmpty()) {
            val v = text(ctx, value, com.google.android.material.R.style.TextAppearance_Material3_TitleSmall).apply {
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(ctx, 8) }
            }
            row.addView(v)
        }
        return row
    }

    fun divider(ctx: Context): View = View(ctx).apply {
        setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxOf(1, dp(ctx, 1) / 2))
    }

    fun progress(ctx: Context): com.google.android.material.progressindicator.LinearProgressIndicator =
        com.google.android.material.progressindicator.LinearProgressIndicator(ctx).apply {
            isIndeterminate = true
            visibility = View.GONE
            layoutParams = lp(top = dp(ctx, 4))
        }

    class ListPage(val root: LinearLayout, val toolbar: MaterialToolbar, val header: LinearLayout, val list: androidx.recyclerview.widget.RecyclerView, val adapter: RowAdapter, val empty: TextView)

    /** Sarlavha, yuqorida ustun (qidiruv, filtrlar) va pastda uzun ro'yxat (RecyclerView). */
    fun listPage(ctx: Context, title: String, backIcon: Int, onBack: () -> Unit, onRow: (Int) -> Unit): ListPage {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val tb = MaterialToolbar(ctx).apply {
            this.title = title
            setNavigationIcon(backIcon)
            setNavigationOnClickListener { onBack() }
        }
        root.addView(tb, lp())
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 16), 0, dp(ctx, 16), dp(ctx, 4))
        }
        root.addView(header, lp())
        val frame = FrameLayout(ctx)
        val rv = androidx.recyclerview.widget.RecyclerView(ctx).apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(ctx)
            clipToPadding = false
            setPadding(dp(ctx, 12), 0, dp(ctx, 12), dp(ctx, 24))
        }
        val adapter = RowAdapter(onRow)
        rv.adapter = adapter
        frame.addView(rv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val empty = hint(ctx, "", 0).apply {
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 32), dp(ctx, 32), dp(ctx, 32), dp(ctx, 32))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            visibility = View.GONE
        }
        frame.addView(empty)
        root.addView(frame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return ListPage(root, tb, header, rv, adapter, empty)
    }

    data class Row(val title: CharSequence, val sub: CharSequence = "", val value: CharSequence = "", val value2: CharSequence = "", val icon: Int = 0, val tone: Int = 0)

    class RowAdapter(private val onRow: (Int) -> Unit) : androidx.recyclerview.widget.RecyclerView.Adapter<RowAdapter.VH>() {
        private var rows: List<Row> = emptyList()

        @android.annotation.SuppressLint("NotifyDataSetChanged")
        fun submit(list: List<Row>) { rows = list; notifyDataSetChanged() }

        override fun getItemCount(): Int = rows.size

        class VH(val root: LinearLayout, val title: TextView, val sub: TextView, val value: TextView, val value2: TextView) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(root) {
            var badge: FrameLayout? = null
            var badgeKey = 0L
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val root = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(ctx, 56)
                setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8))
                layoutParams = androidx.recyclerview.widget.RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                isClickable = true
                isFocusable = true
                val tv = TypedValue()
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                setBackgroundResource(tv.resourceId)
            }
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            val t = text(ctx, "", com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            val s = hint(ctx, "", 2)
            col.addView(t)
            col.addView(s)
            root.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val right = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
            val v = text(ctx, "", com.google.android.material.R.style.TextAppearance_Material3_TitleSmall).apply {
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val v2 = hint(ctx, "", 2).apply {
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            right.addView(v)
            right.addView(v2)
            root.addView(right, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(ctx, 8) })
            val vh = VH(root, t, s, v, v2)
            root.setOnClickListener { val p = vh.bindingAdapterPosition; if (p != androidx.recyclerview.widget.RecyclerView.NO_POSITION) onRow(p) }
            return vh
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = rows[position]
            holder.title.text = r.title
            holder.sub.text = r.sub
            holder.sub.visibility = if (r.sub.isEmpty()) View.GONE else View.VISIBLE
            holder.value.text = r.value
            holder.value2.text = r.value2
            holder.value2.visibility = if (r.value2.isEmpty()) View.GONE else View.VISIBLE
            // v3.12: ikonka (doira) — faqat o'zgarganda qayta chiziladi
            val key = (r.icon.toLong() shl 8) or r.tone.toLong()
            if (r.icon == 0) holder.badge?.visibility = View.GONE
            else if (holder.badge == null || holder.badgeKey != key) {
                holder.badge?.let { holder.root.removeView(it) }
                val b = badge(holder.root.context, r.icon, r.tone)
                holder.root.addView(b, 0)
                holder.badge = b
                holder.badgeKey = key
            } else holder.badge?.visibility = View.VISIBLE
        }
    }

    /** Qidiruv maydoni (har harfda [onChange]). */
    fun search(ctx: Context, hint: CharSequence, onChange: (String) -> Unit): Pair<TextInputLayout, TextInputEditText> {
        val (til, e) = field(ctx, hint, top = 4)
        e.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) { onChange(s?.toString().orEmpty()) }
        })
        return til to e
    }

    /** Bitta tanlanadigan tugmalar guruhi; [onPick] — tanlangan indeks. */
    fun toggle(ctx: Context, labels: List<CharSequence>, selected: Int, top: Int = 8, onPick: (Int) -> Unit): com.google.android.material.button.MaterialButtonToggleGroup {
        val g = com.google.android.material.button.MaterialButtonToggleGroup(ctx).apply {
            isSingleSelection = true
            isSelectionRequired = true
            layoutParams = lp(top = dp(ctx, top))
        }
        val ids = ArrayList<Int>()
        labels.forEach { l ->
            val b = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                id = View.generateViewId()
                text = l
                maxLines = 2
                insetTop = 0
                insetBottom = 0
                minWidth = 0
                minimumWidth = 0
                iconPadding = 0
                setPadding(dp(ctx, 2), paddingTop, dp(ctx, 2), paddingBottom)
            }
            ids += b.id
            g.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        if (selected in ids.indices) g.check(ids[selected])
        g.addOnButtonCheckedListener { _, id, checked -> if (checked) ids.indexOf(id).takeIf { it >= 0 }?.let(onPick) }
        return g
    }
}
