package com.textpic.studio.ime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.textpic.core.RenderMeta
import com.textpic.core.StylePresets
import com.textpic.studio.R
import com.textpic.studio.data.prefs.AppSettings
import com.textpic.studio.data.db.TemplateEntity
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

class TextPicKeyboardView(context: Context) : LinearLayout(context) {
    interface Listener {
        fun onChar(text: String)
        fun onBackspace()
        fun onEnter()
        fun onRender()
        fun onInsert()
        fun onRenderAndInsert()
        fun onDiscard()
        fun onOpenSettings()
        fun onStyleSelected(styleKey: String)
        fun onTemplateSelected(template: TemplateEntity)
    }

    var listener: Listener? = null

    private enum class Page {
        LETTERS,
        SYMBOLS,
        EMOJI,
    }

    private val handler = Handler(Looper.getMainLooper())
    private val inter = ResourcesCompat.getFont(context, R.font.inter)
    private val colorNav = color(R.color.kb_background)
    private val colorCard = color(R.color.kb_key)
    private val colorInput = color(R.color.kb_key)
    private val colorSelected = color(R.color.kb_key_pressed)
    private val colorKey = color(R.color.kb_key)
    private val colorKeySpecial = color(R.color.kb_key_special)
    private val colorKeySpecialPressed = color(R.color.kb_key_special_pressed)
    private val colorBorder = color(R.color.kb_divider)
    private val colorBorderStrong = color(R.color.kb_divider)
    private val colorText = color(R.color.kb_text)
    private val colorSecondary = color(R.color.kb_text_muted)
    private val colorMuted = color(R.color.kb_text_hint)
    private val colorAccent = color(R.color.kb_accent)
    private val colorAccentPressed = color(R.color.kb_accent_pressed)
    private val colorAccentSoft = color(R.color.kb_accent_soft)

    private val lettersLayout by lazy { ImeLayouts.load(context, "layouts/en_qwerty.json") }
    private val symbolsLayout by lazy { ImeLayouts.load(context, "layouts/en_symbols.json") }
    private val emojiLayout by lazy { ImeLayouts.load(context, "layouts/en_emoji.json") }

    private val toolbar: LinearLayout
    private val quickTemplatesRow: LinearLayout
    private val styleChip: TextView
    private val templateStrip: HorizontalScrollView
    private val templateStripRow: LinearLayout
    private val previewContainer: LinearLayout
    private val previewImage: CheckerboardImageView
    private val previewMeta: TextView
    private val previewInsert: TextView
    private val previewDiscard: TextView
    private val keysContainer: LinearLayout

    private var page = Page.LETTERS
    private var shifted = false
    private var capsLock = false
    private var lastShiftTap = 0L
    private var hapticsEnabled = true
    private var busy = false
    private var baseHeightDp = 300
    private var templates: List<TemplateEntity> = emptyList()
    private var keyPreview: PopupWindow? = null
    private var currentPreview: Bitmap? = null
    private var rendering = false
    private var bottomInsetPx = 0

    private val repeatBackspace = object : Runnable {
        override fun run() {
            listener?.onBackspace()
            handler.postDelayed(this, 48)
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(colorNav)
        // The IME window draws under the navigation bar, so reserve that strip.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            applyBottomInset(insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            insets
        }
        // The IME window is a FrameLayout, which casts child params to MarginLayoutParams.
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(baseHeightDp),
        )

        toolbar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(6), 0)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(44))
        }

        val logo = ImageView(context).apply {
            setImageResource(R.drawable.ic_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(8), dp(8), dp(8), dp(8))
            contentDescription = "Templates"
            setOnClickListener { toggleTemplateStrip() }
            layoutParams = LayoutParams(dp(40), dp(40))
        }
        toolbar.addView(logo)

        val templatesScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        }
        quickTemplatesRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }
        templatesScroll.addView(
            quickTemplatesRow,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        toolbar.addView(templatesScroll)

        styleChip = TextView(context).apply {
            typeface = inter
            setTextColor(colorSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            maxWidth = dp(96)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = rounded(colorCard, colorBorder, 999f)
            setOnClickListener { showStyleMenu(this) }
            contentDescription = "Style preset"
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dp(30)).apply {
                marginEnd = dp(4)
            }
        }
        toolbar.addView(styleChip)

        val gear = TextView(context).apply {
            text = "⚙"
            typeface = inter
            setTextColor(colorSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            gravity = Gravity.CENTER
            contentDescription = "TextPic settings"
            setOnClickListener { listener?.onOpenSettings() }
            layoutParams = LayoutParams(dp(40), dp(40))
        }
        toolbar.addView(gear)
        addView(toolbar)

        addView(
            View(context).apply {
                setBackgroundColor(colorBorder)
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 1)
            },
        )

        templateStrip = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            visibility = GONE
            setBackgroundColor(colorCard)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(44))
        }
        templateStripRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        templateStrip.addView(
            templateStripRow,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        addView(templateStrip)

        previewContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = GONE
            setBackgroundColor(colorNav)
            setPadding(dp(6), dp(5), dp(6), dp(3))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(116))
        }
        val previewRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(94))
        }
        previewImage = CheckerboardImageView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        }
        previewRow.addView(previewImage)
        val previewActions = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), 0, 0, 0)
            layoutParams = LayoutParams(dp(94), LayoutParams.MATCH_PARENT)
        }
        previewInsert = actionButton("Insert", colorAccent, Color.WHITE)
        previewInsert.setOnClickListener { listener?.onInsert() }
        previewDiscard = actionButton("Discard", colorKeySpecial, colorSecondary)
        previewDiscard.setOnClickListener { listener?.onDiscard() }
        previewActions.addView(previewInsert, LayoutParams(LayoutParams.MATCH_PARENT, dp(42)))
        previewActions.addView(
            previewDiscard,
            LayoutParams(LayoutParams.MATCH_PARENT, dp(36)).apply { topMargin = dp(4) },
        )
        previewRow.addView(previewActions)
        previewContainer.addView(previewRow)
        previewMeta = TextView(context).apply {
            typeface = inter
            setTextColor(colorMuted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), 0, 0)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(16))
        }
        previewContainer.addView(previewMeta)
        addView(previewContainer)

        keysContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(0, dp(2), 0, dp(6))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        addView(keysContainer)

        buildKeys()
    }

    fun applySettings(settings: AppSettings) {
        hapticsEnabled = settings.haptics
        styleChip.text = StylePresets.get(settings.render.styleKey).displayName
        val nextHeight = when (settings.keyboardHeight) {
            "small" -> 264
            "large" -> 340
            else -> 300
        }
        if (nextHeight != baseHeightDp) {
            baseHeightDp = nextHeight
            updateHeight()
        }
    }

    fun setTemplates(list: List<TemplateEntity>) {
        templates = list
        rebuildTemplateQuickChips()
        rebuildTemplateStrip()
    }

    fun resetForNewInput() {
        page = Page.LETTERS
        shifted = false
        capsLock = false
        lastShiftTap = 0L
        busy = false
        templateStrip.visibility = GONE
        clearPreview()
        buildKeys()
        updateHeight()
    }

    fun setBusy(value: Boolean) {
        busy = value
        previewInsert.isEnabled = !value
        previewDiscard.isEnabled = !value
        buildKeys()
    }

    fun showPreview(bitmap: Bitmap, meta: RenderMeta) {
        currentPreview = bitmap
        previewImage.bitmap = bitmap
        previewMeta.text = "${meta.widthPx} × ${meta.heightPx} px · ${meta.finalFontPx} px text"
        previewContainer.visibility = VISIBLE
        updateHeight()
    }

    fun clearPreview() {
        currentPreview = null
        previewImage.bitmap = null
        previewMeta.text = ""
        previewContainer.visibility = GONE
        updateHeight()
    }

    fun showInserted() {
        clearPreview()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }

    private fun applyBottomInset(insetPx: Int) {
        if (insetPx == bottomInsetPx) return
        bottomInsetPx = insetPx
        setPadding(0, 0, 0, insetPx)
        updateHeight()
    }

    private fun updateHeight() {
        val height = baseHeightDp +
            (if (previewContainer.visibility == VISIBLE) 116 else 0) +
            (if (templateStrip.visibility == VISIBLE) 44 else 0)
        layoutParams = FrameLayout.LayoutParams(
            LayoutParams.MATCH_PARENT,
            dp(height) + bottomInsetPx,
        )
        requestLayout()
        post { requestLayout() }
    }

    private fun toggleTemplateStrip() {
        if (templateStrip.visibility == VISIBLE) {
            templateStrip.visibility = GONE
        } else {
            rebuildTemplateStrip()
            templateStrip.visibility = VISIBLE
        }
        updateHeight()
    }

    private fun rebuildTemplateQuickChips() {
        quickTemplatesRow.removeAllViews()
        if (templates.isEmpty()) {
            quickTemplatesRow.addView(chip("No templates", colorMuted) { toggleTemplateStrip() })
            return
        }
        templates.take(3).forEach { template ->
            quickTemplatesRow.addView(
                chip(template.title, colorSecondary) {
                    listener?.onTemplateSelected(template)
                },
            )
        }
    }

    private fun rebuildTemplateStrip() {
        templateStripRow.removeAllViews()
        if (templates.isEmpty()) {
            templateStripRow.addView(chip("No templates yet", colorMuted))
            return
        }
        templateStripRow.addView(
            TextView(context).apply {
                text = "TEMPLATES"
                typeface = inter
                setTextColor(colorMuted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                letterSpacing = 0.08f
                setPadding(dp(6), 0, dp(8), 0)
            },
        )
        templates.forEach { template ->
            templateStripRow.addView(
                chip(template.title, colorSecondary) {
                    listener?.onTemplateSelected(template)
                },
            )
        }
    }

    private fun chip(label: String, textColor: Int, onClick: (() -> Unit)? = null): TextView =
        TextView(context).apply {
            typeface = inter
            text = label
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = dp(140)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = rounded(colorInput, colorBorder, 999f)
            if (onClick != null) {
                isClickable = true
                setOnClickListener { onClick() }
            }
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dp(30)).apply {
                marginEnd = dp(6)
            }
        }

    private fun buildKeys() {
        dismissKeyPreview()
        handler.removeCallbacks(repeatBackspace)
        keysContainer.removeAllViews()
        val rows = when (page) {
            Page.LETTERS -> lettersLayout.rows
            Page.SYMBOLS -> symbolsLayout.rows
            Page.EMOJI -> emojiRows()
        }
        rows.forEach { rowKeys ->
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            }
            var weightSum = 0f
            rowKeys.forEach { weightSum += it.weight }
            row.weightSum = weightSum
            rowKeys.forEach { key ->
                row.addView(buildKeyView(key), keyLayoutParams(key))
            }
            keysContainer.addView(row)
        }
    }

    private fun emojiRows(): List<List<ImeKey>> {
        val rows = emojiLayout.rows.toMutableList()
        val recents = recentEmojis()
        if (recents.isEmpty() || rows.isEmpty()) return rows
        val first = rows.first().toMutableList()
        recents.take(8).forEachIndexed { index, emoji ->
            if (index < first.size) {
                first[index] = ImeKey(emoji, emoji, ImeKeyType.CHAR, first[index].weight)
            }
        }
        rows[0] = first
        return rows
    }

    private fun keyLayoutParams(key: ImeKey): LayoutParams =
        LayoutParams(0, LayoutParams.MATCH_PARENT, key.weight).apply {
            topMargin = dp(3)
            bottomMargin = dp(3)
            leftMargin = dp(3)
            rightMargin = dp(3)
        }

    private fun buildKeyView(key: ImeKey): TextView {
        val label = displayLabel(key)
        val isRender = key.type == ImeKeyType.RENDER
        val isInsert = key.type == ImeKeyType.INSERT
        val isShiftActive = key.type == ImeKeyType.SHIFT && (shifted || capsLock)
        val textSize = when (key.type) {
            ImeKeyType.CHAR -> if (page == Page.EMOJI) 22f else 18f
            ImeKeyType.SHIFT, ImeKeyType.BACKSPACE, ImeKeyType.ENTER -> 19f
            ImeKeyType.EMOJI -> 19f
            ImeKeyType.SYMBOLS, ImeKeyType.LETTERS -> 12f
            ImeKeyType.SPACE -> 11f
            ImeKeyType.RENDER, ImeKeyType.INSERT -> 12f
        }
        val background = when {
            isRender -> keyBackground(colorAccent, colorAccentPressed)
            isInsert -> keyBackground(colorKeySpecial, colorKeySpecialPressed)
            isShiftActive -> keyBackground(colorAccentSoft, colorKeySpecial)
            key.type in setOf(
                ImeKeyType.SHIFT,
                ImeKeyType.BACKSPACE,
                ImeKeyType.SYMBOLS,
                ImeKeyType.LETTERS,
                ImeKeyType.EMOJI,
                ImeKeyType.ENTER,
            ) -> keyBackground(colorKeySpecial, colorKeySpecialPressed)
            else -> keyBackground(colorKey, colorSelected)
        }
        val textColor = when {
            isRender -> Color.WHITE
            isInsert -> colorAccent
            isShiftActive -> colorAccent
            else -> colorText
        }
        return TextView(context).apply {
            typeface = if (isRender || isInsert) {
                Typeface.create(inter, Typeface.BOLD)
            } else {
                inter
            }
            text = label
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
            gravity = Gravity.CENTER
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(1), 0, dp(1), 0)
            this.background = background
            contentDescription = label.ifEmpty { "space" }
            isClickable = true
            setOnClickListener { handleKey(key) }
            if (key.type == ImeKeyType.RENDER) {
                setOnLongClickListener {
                    listener?.onRenderAndInsert()
                    true
                }
            }
            if (key.type == ImeKeyType.BACKSPACE) {
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            haptic(view)
                            listener?.onBackspace()
                            handler.removeCallbacks(repeatBackspace)
                            handler.postDelayed(repeatBackspace, 380)
                            true
                        }
                        android.view.MotionEvent.ACTION_UP,
                        android.view.MotionEvent.ACTION_CANCEL,
                        -> {
                            handler.removeCallbacks(repeatBackspace)
                            true
                        }
                        else -> false
                    }
                }
            } else {
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            haptic(view)
                            if (key.type == ImeKeyType.CHAR) {
                                showKeyPreview(view, label)
                            }
                        }
                        android.view.MotionEvent.ACTION_UP,
                        android.view.MotionEvent.ACTION_CANCEL,
                        -> dismissKeyPreview()
                    }
                    false
                }
            }
        }
    }

    private fun displayLabel(key: ImeKey): String = when (key.type) {
        ImeKeyType.SHIFT -> if (capsLock) "⇪" else "⇧"
        ImeKeyType.BACKSPACE -> "⌫"
        ImeKeyType.ENTER -> "⏎"
        ImeKeyType.EMOJI -> "☺"
        ImeKeyType.SYMBOLS -> "?123"
        ImeKeyType.LETTERS -> "ABC"
        ImeKeyType.SPACE -> ""
        ImeKeyType.RENDER -> if (busy) "Rendering…" else "Render"
        ImeKeyType.INSERT -> "Insert"
        ImeKeyType.CHAR -> {
            if (page == Page.LETTERS && (shifted || capsLock) && key.output.length == 1 &&
                key.output[0].isLetter()
            ) {
                key.output.uppercase(Locale.getDefault())
            } else {
                key.label
            }
        }
    }

    private fun handleKey(key: ImeKey) {
        when (key.type) {
            ImeKeyType.CHAR -> {
                val output = if (page == Page.LETTERS && (shifted || capsLock) &&
                    key.output.length == 1 && key.output[0].isLetter()
                ) {
                    key.output.uppercase(Locale.getDefault())
                } else {
                    key.output
                }
                if (output.isNotEmpty()) {
                    listener?.onChar(output)
                    if (page == Page.EMOJI) {
                        rememberEmoji(output)
                        buildKeys()
                    } else if (page == Page.LETTERS && shifted && !capsLock) {
                        shifted = false
                        buildKeys()
                    }
                }
            }
            ImeKeyType.SPACE -> {
                listener?.onChar(" ")
                if (shifted && !capsLock) {
                    shifted = false
                    buildKeys()
                }
            }
            ImeKeyType.SHIFT -> toggleShift()
            ImeKeyType.BACKSPACE -> listener?.onBackspace()
            ImeKeyType.SYMBOLS -> {
                page = Page.SYMBOLS
                shifted = false
                buildKeys()
            }
            ImeKeyType.LETTERS -> {
                page = Page.LETTERS
                shifted = false
                buildKeys()
            }
            ImeKeyType.EMOJI -> {
                page = Page.EMOJI
                shifted = false
                buildKeys()
            }
            ImeKeyType.ENTER -> listener?.onEnter()
            ImeKeyType.RENDER -> listener?.onRender()
            ImeKeyType.INSERT -> listener?.onInsert()
        }
    }

    private fun toggleShift() {
        val now = SystemClock.uptimeMillis()
        when {
            capsLock -> {
                capsLock = false
                shifted = false
                lastShiftTap = 0L
            }
            now - lastShiftTap in 1..380 -> {
                capsLock = true
                shifted = true
                lastShiftTap = 0L
            }
            else -> {
                shifted = !shifted
                lastShiftTap = now
            }
        }
        buildKeys()
    }

    private fun showStyleMenu(anchor: View) {
        val popup = PopupMenu(context, anchor)
        val group = 1
        StylePresets.all.forEachIndexed { index, preset ->
            popup.menu.add(group, index, index, preset.displayName).apply {
                isCheckable = true
                isChecked = styleChip.text == preset.displayName
            }
        }
        popup.menu.setGroupCheckable(group, true, true)
        popup.setOnMenuItemClickListener { item ->
            val preset = StylePresets.all.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
            listener?.onStyleSelected(preset.key)
            true
        }
        popup.show()
    }

    private fun actionButton(label: String, fill: Int, textColor: Int): TextView =
        TextView(context).apply {
            typeface = Typeface.create(inter, Typeface.BOLD)
            text = label
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            background = rounded(fill, fill, 8f)
            isClickable = true
        }

    private fun haptic(view: View) {
        if (hapticsEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    private fun showKeyPreview(anchor: View, label: String) {
        dismissKeyPreview()
        if (label.length > 8) return
        val preview = TextView(context).apply {
            typeface = inter
            text = label
            setTextColor(colorText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = rounded(colorKey, colorBorder, 10f)
            measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
        }
        val popup = PopupWindow(
            preview,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false,
        ).apply {
            isOutsideTouchable = false
            isFocusable = false
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dp(6).toFloat()
        }
        runCatching {
            popup.showAsDropDown(
                anchor,
                (anchor.width - preview.measuredWidth) / 2,
                -anchor.height - preview.measuredHeight - dp(4),
            )
            keyPreview = popup
        }.onFailure {
            popup.dismiss()
        }
    }

    private fun dismissKeyPreview() {
        keyPreview?.dismiss()
        keyPreview = null
    }

    private fun recentEmojis(): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RECENT_EMOJI, null)
            ?: return emptyList()
        return raw.split(SEPARATOR).filter { it.isNotBlank() }.take(8)
    }

    private fun rememberEmoji(value: String) {
        if (value.isBlank()) return
        val list = recentEmojis().toMutableList()
        list.remove(value)
        list.add(0, value)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENT_EMOJI, list.take(8).joinToString(SEPARATOR))
            .apply()
    }

    private fun color(resource: Int): Int = ContextCompat.getColor(context, resource)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun rounded(fill: Int, stroke: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun keyBackground(fill: Int, pressed: Int): StateListDrawable =
        StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), flat(pressed, 7f))
            addState(intArrayOf(), flat(fill, 7f))
        }

    private fun flat(fill: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }

    private class CheckerboardImageView(context: Context) : View(context) {
        var bitmap: Bitmap? = null
            set(value) {
                field = value
                invalidate()
            }

        private val checkerPaint = Paint()
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val cell = (8 * context.resources.displayMetrics.density).roundToInt()
        private val light = ContextCompat.getColor(context, R.color.checker_light)
        private val dark = ContextCompat.getColor(context, R.color.checker_dark)
        private val background = ContextCompat.getColor(context, R.color.surface_input)

        init {
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(
                        0,
                        0,
                        view.width,
                        view.height,
                        (8 * context.resources.displayMetrics.density).roundToInt().toFloat(),
                    )
                }
            }
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(background)
            var row = 0
            var y = 0
            while (y < height) {
                var column = 0
                var x = 0
                while (x < width) {
                    checkerPaint.color = if ((row + column) % 2 == 0) light else dark
                    canvas.drawRect(
                        x.toFloat(),
                        y.toFloat(),
                        min(x + cell, width).toFloat(),
                        min(y + cell, height).toFloat(),
                        checkerPaint,
                    )
                    x += cell
                    column++
                }
                y += cell
                row++
            }
            val image = bitmap ?: return
            if (image.width == 0 || image.height == 0) return
            val availableWidth = (width - 8 * resources.displayMetrics.density).coerceAtLeast(1f)
            val availableHeight = (height - 8 * resources.displayMetrics.density).coerceAtLeast(1f)
            val scale = min(availableWidth / image.width, availableHeight / image.height)
            val targetWidth = image.width * scale
            val targetHeight = image.height * scale
            val left = (width - targetWidth) / 2f
            val top = (height - targetHeight) / 2f
            canvas.drawBitmap(
                image,
                null,
                RectF(left, top, left + targetWidth, top + targetHeight),
                bitmapPaint,
            )
        }
    }

    private companion object {
        const val PREFS = "ime_emoji"
        const val KEY_RECENT_EMOJI = "recent"
        const val SEPARATOR = "\u001F"
    }
}
