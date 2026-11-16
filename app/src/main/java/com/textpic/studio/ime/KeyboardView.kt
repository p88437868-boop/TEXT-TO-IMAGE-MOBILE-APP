package com.textpic.studio.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import com.textpic.studio.data.db.TemplateEntity
import com.textpic.studio.data.prefs.HapticStrength
import com.textpic.studio.data.prefs.KeyFeedback
import com.textpic.studio.data.prefs.KeySoundId
import com.textpic.studio.data.prefs.KeyboardPrefRules
import com.textpic.studio.data.prefs.KeyboardPrefs
import com.textpic.studio.data.prefs.KeyboardTheme
import com.textpic.studio.data.prefs.OneHandedSide
import kotlin.math.abs
import kotlin.math.min

/**
 * The keyboard, drawn on a canvas.
 *
 * It consumes the four JSON pages from [KeyboardSpecs], so the layout lives in
 * the assets and this class only paints and routes touches. Nothing here talks to
 * the network and nothing is logged.
 *
 * The view is a [FrameLayout] only so the Templates panel can show one small text
 * field for a template placeholder; every other pixel is drawn by hand.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    interface Listener {
        fun onText(text: String)
        fun onBackspace()
        fun onBackspaceDragSelect()
        fun onSpace()
        fun onEnter()
        fun onEditorAction(action: Int)
        fun onGlobe()
        fun onCursorMove(direction: Int)
        fun onSelectAll()
        fun onCut()
        fun onCopy()
        fun onPaste()
        fun onRender()

        /** Render, insert and then send — the primary toolbar button. */
        fun onSend()
        fun onRenderAndInsert()
        fun onInsert()
        fun onDiscard()
        fun onOpenAppSettings()
        fun onTemplate(template: TemplateEntity)
        fun clipText(): String?
        fun clipSensitive(): Boolean
    }

    private enum class Panel { NONE, MENU, CLIPBOARD, EMOJI, EDITING, TEMPLATES, TEMPLATE_FORM }

    private data class PanelItem(
        val rect: RectF,
        val kind: String,
        val value: String = "",
        val payload: Int = 0,
    )

    var listener: Listener? = null

    /** Called when the theme changes, so the IME can recolour the navigation bar. */
    var onAppearanceChanged: (() -> Unit)? = null

    private val darkMode: Boolean
        get() = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private val landscape: Boolean
        get() = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE

    private var prefs = KeyboardPrefs()
    private var palette = KeyboardPalettes.of(KeyboardTheme.DARK, true)

    private var page = KeyboardPageId.LETTERS
    private var shift = ShiftState.OFF
    private var field = FieldKind.TEXT
    private var imeAction = EditorInfo.IME_ACTION_NONE
    private var capsMode = 0

    private val pages = mutableMapOf<KeyboardPageId, KeyboardPage>()
    private var placed: List<PlacedKey> = emptyList()
    private var panelItems: List<PanelItem> = emptyList()
    private var panel = Panel.NONE
    private var panelScroll = 0f
    private var panelScrollMax = 0f
    private var emojiCategory = 0

    private var pressed: PlacedKey? = null
    private var pressedTouch = RectF()
    private var popupAlternates: List<String> = emptyList()
    private var popupIndex = -1
    private var popupRect = RectF()
    private var spaceSliding = false
    private var spaceAccum = 0f
    private var shiftSliding = false
    private var backspaceSliding = false
    private var backspaceAccum = 0f

    private var clips: List<ClipEntry> = emptyList()
    private var templates: List<TemplateEntity> = emptyList()

    private var previewBitmap: android.graphics.Bitmap? = null
    private var previewMeta: String = ""
    private var busy = false
    private var remainingLabel: String? = null
    private var remainingLow = false
    private var statusNote: String? = null

    /** Height of the phone's navigation bar, painted with the keyboard background. */
    private var navigationInset = 0
    private var frame = KeyboardFrame.of(720f, 1600f, 3, false)
    private var metrics = KeyboardMetrics.of(720f, 400f, 4)

    private val density = resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val iconFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val scratch = RectF()

    /** Skin tone chosen for an emoji, remembered for the life of the keyboard. */
    private val emojiTones = mutableMapOf<String, String>()
    private var toneTarget: String? = null
    private var toneRect = RectF()

    private var pendingTemplate: TemplateEntity? = null
    private var pendingPlaceholders: List<String> = emptyList()
    private val filledValues = mutableMapOf<String, String>()
    private var currentPlaceholder: String? = null

    /** One field, only visible while a template asks for a value. */
    private val formField = EditText(context).apply {
        visibility = GONE
        isSingleLine = true
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(dpx(12f), dpx(6f), dpx(12f), dpx(6f))
        setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                acceptPlaceholder()
                true
            } else {
                false
            }
        }
    }

    private val soundPlayer by lazy { KeySoundPlayer(context) }
    private val feedback = KeyFeedback(
        vibrate = { amplitude, duration ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = android.os.VibrationEffect.createOneShot(duration, amplitude)
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                runCatching {
                    val vibrator = context.getSystemService(android.os.Vibrator::class.java)
                    vibrator?.vibrate(effect)
                }
            } else {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        },
        playSound = { sound, volume -> soundPlayer.play(sound, volume) },
        vibrateFallback = { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) },
    )

    private val backspaceRepeat = object : Runnable {
        override fun run() {
            listener?.onBackspace()
            feedback.onKeyDown(prefs)
            handler.postDelayed(this, 55L)
        }
    }

    private val renderLongPress = object : Runnable {
        override fun run() {
            listener?.onRenderAndInsert()
        }
    }

    private val longPress = object : Runnable {
        override fun run() {
            val current = pressed ?: return
            val key = current.key
            if (key.kind == KeyKind.COMMA) {
                openPanel(Panel.EMOJI)
                pressed = null
                invalidate()
                return
            }
            if (key.kind == KeyKind.OPERATORS) return
            val alternates = KeyboardRules.alternates(key)
            if (alternates.isEmpty() || !prefs.popupPreview) {
                if (key.kind == KeyKind.CHAR || key.kind == KeyKind.PERIOD) {
                    listener?.onText(key.output)
                    shift = ShiftLogic.afterChar(shift)
                    pressed = null
                    invalidate()
                }
                return
            }
            popupAlternates = alternates
            popupIndex = 0
            feedback.onKeyDown(prefs)
            invalidate()
        }
    }

    init {
        isClickable = true
        isFocusable = false
        setWillNotDraw(false)
        setBackgroundColor(Color.TRANSPARENT)
        addView(formField)
    }

    private fun dp(value: Float): Float = value * density

    private fun dpx(value: Float): Int = (value * density).toInt()

    // ---------------------------------------------------------------- public API

    fun applyPrefs(prefs: KeyboardPrefs) {
        this.prefs = prefs
        palette = KeyboardPalettes.of(prefs.theme, darkMode)
        formField.setTextColor(palette.text)
        formField.setHintTextColor(palette.hint)
        formField.setBackgroundColor(palette.card)
        soundPlayer.setVolume(KeyboardPrefRules.volumeFraction(prefs.soundVolume))
        onAppearanceChanged?.invoke()
        requestLayout()
        invalidate()
    }

    fun setEditor(kind: FieldKind, action: Int, caps: Int) {
        val changed = kind != field || action != imeAction
        field = kind
        imeAction = action
        capsMode = caps
        if (prefs.autoCapitalize) shift = ShiftLogic.fromCapsMode(caps, shift)
        page = FieldKinds.startPage(kind)
        if (changed) {
            popupAlternates = emptyList()
            pressed = null
            closePanel()
        }
        requestLayout()
        invalidate()
    }

    /** The phone's navigation bar height, so the last row never sits under it. */
    fun setNavigationInset(px: Int) {
        if (px == navigationInset) return
        navigationInset = px.coerceAtLeast(0)
        requestLayout()
        invalidate()
    }

    fun setRemaining(label: String?, low: Boolean) {
        remainingLabel = label
        remainingLow = low
        invalidate()
    }

    fun setTemplates(list: List<TemplateEntity>) {
        templates = list
        if (panel == Panel.TEMPLATES) invalidate()
    }

    fun showPreview(bitmap: android.graphics.Bitmap, meta: String) {
        previewBitmap = bitmap
        previewMeta = meta
        statusNote = null
        invalidate()
    }

    fun showInserted() {
        previewBitmap = null
        statusNote = "Inserted"
        invalidate()
    }

    fun clearPreview() {
        previewBitmap = null
        previewMeta = ""
        statusNote = null
        invalidate()
    }

    fun setBusy(value: Boolean) {
        busy = value
        invalidate()
    }

    /** The IME reports the clipboard so the panel can show a pin/delete list. */
    fun refreshClips(entries: List<ClipEntry>) {
        clips = entries
        invalidate()
    }

    /** True while a rendered image waits in the preview strip. */
    fun hasPreview(): Boolean = previewBitmap != null

    fun navigationInsetPx(): Int = navigationInset

    fun currentPage(): KeyboardPageId = page

    fun currentShift(): ShiftState = shift

    fun isPanelOpen(): Boolean = panel != Panel.NONE

    /** True when a form field is holding focus (a template placeholder). */
    fun isFormOpen(): Boolean = panel == Panel.TEMPLATE_FORM

    fun closePanel() {
        val wasForm = panel == Panel.TEMPLATE_FORM
        panel = Panel.NONE
        panelScroll = 0f
        toneTarget = null
        hideForm(clearValues = wasForm)
        requestLayout()
        invalidate()
    }

    fun backgroundArgb(): Int = palette.background

    // ------------------------------------------------------------------ measure

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthPx = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()
        frame = KeyboardFrame.of(
            widthPx.toFloat(),
            screenHeight,
            prefs.heightLevel,
            landscape,
        )
        setMeasuredDimension(
            widthPx,
            NavigationInsetRules.keyboardHeight(frame.totalHeightPx.toInt(), navigationInset),
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildLayout()
    }

    /** The rectangle the keys may use: below the toolbar, above the navigation bar. */
    private fun bodyRect(out: RectF) {
        val top = frame.toolbarHeightPx + frame.paddingPx
        val bottom = (height - navigationInset - frame.paddingPx).coerceAtLeast(top + 1f)
        out.set(0f, top, width.toFloat(), bottom)
    }

    private fun rebuildLayout() {
        if (width == 0) return
        val body = RectF()
        bodyRect(body)
        val pageModel = pageWithExtras(pageWithNumberRow(currentPageModel()))
        metrics = KeyboardMetrics.of(
            body.width(),
            body.height(),
            pageModel.rows.size.coerceAtLeast(1),
            landscape,
        )
        placed = KeyboardGeometry.layout(
            pageModel,
            body.width(),
            body.height(),
            density,
            metrics,
        )
        for (key in placed) {
            key.visual.offset(0f, body.top)
            key.touch.offset(0f, body.top)
        }
        if (prefs.oneHanded != OneHandedSide.OFF) {
            val fraction = KeyboardPrefRules.oneHandedWidthFraction(prefs.oneHanded)
            val shiftX = if (prefs.oneHanded == OneHandedSide.LEFT) 0f else width * (1f - fraction)
            for (key in placed) {
                key.visual.left = shiftX + key.visual.left * fraction
                key.visual.right = shiftX + key.visual.right * fraction
                key.touch.left = shiftX + key.touch.left * fraction
                key.touch.right = shiftX + key.touch.right * fraction
            }
        }
        positionForm()
    }

    private fun currentPageModel(): KeyboardPage = pages.getOrPut(page) {
        runCatching { KeyboardSpecs.load(context, page) }
            .getOrElse { KeyboardPage(page, emptyList()) }
    }

    private fun pageWithNumberRow(model: KeyboardPage): KeyboardPage {
        if (!prefs.numberRow && field != FieldKind.PASSWORD) {
            return model
        }
        if (model.id != KeyboardPageId.LETTERS) return model
        val digits = (1..9).map { digit ->
            KeyCell(
                key = KeyboardKey(
                    kind = KeyKind.CHAR,
                    label = digit.toString(),
                    output = digit.toString(),
                    weight = 1f,
                ),
            )
        } + KeyCell(
            key = KeyboardKey(kind = KeyKind.CHAR, label = "0", output = "0", weight = 1f),
        )
        return model.copy(rows = listOf(digits) + model.rows)
    }

    /** Email and URL fields get the extra keys the spec asks for. */
    private fun pageWithExtras(model: KeyboardPage): KeyboardPage {
        if (model.id != KeyboardPageId.LETTERS) return model
        val extras = when (field) {
            FieldKind.EMAIL -> listOf("@" to 5f, "." to 5f)
            FieldKind.URL -> listOf("@" to 3f, "." to 3f, "/" to 2f, ".com" to 2f)
            else -> return model
        }
        val row = extras.map { (text, weight) ->
            KeyCell(
                key = KeyboardKey(
                    kind = KeyKind.CHAR,
                    label = text,
                    output = text,
                    weight = weight,
                    style = KeyStyle.SPECIAL,
                ),
            )
        }
        return model.copy(rows = model.rows + listOf(row))
    }

    // -------------------------------------------------------------------- draw

    override fun onDraw(canvas: Canvas) {
        fillPaint.color = palette.background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)

        if (undoVisible) {
            drawUndoBar(canvas)
            return
        }

        if (panel != Panel.NONE) {
            drawPanel(canvas)
            return
        }
        val body = RectF()
        bodyRect(body)
        drawToolbar(canvas)
        drawKeys(canvas, body)
        drawPopup(canvas)
    }

    private fun toolbarTop(): Float = frame.paddingPx * 0.4f + dp(2f)

    private fun toolbarHeight(): Float = (frame.toolbarHeightPx - frame.paddingPx * 0.8f).coerceAtLeast(dp(40f))

    private fun drawToolbar(canvas: Canvas) {
        val top = toolbarTop()
        val height = toolbarHeight()
        val centerY = top + height / 2f

        if (previewBitmap != null || statusNote != null || busy) {
            drawPreviewStrip(canvas, top, height)
            return
        }

        val slot = width / 5f
        drawGridIcon(canvas, slot * 0.5f, centerY, 10f * density)
        drawSmallPill(canvas, slot + dp(2f), slot * 2f - dp(2f), top, height, "Preview", false)
        drawSendPill(canvas, slot * 2f + dp(2f), slot * 3f - dp(2f), top, height, centerY)
        drawGearIcon(canvas, slot * 3.5f, centerY, 9f * density)
        drawClipboardCircle(canvas, slot * 4.5f, centerY, 16f * density)

        remainingLabel?.let { label ->
            smallPaint.textAlign = Paint.Align.LEFT
            smallPaint.textSize = 10f * density
            smallPaint.color = if (remainingLow) 0xFFF2B01E.toInt() else palette.hint
            canvas.drawText(label, dp(8f), top + height - dp(1f), smallPaint)
            smallPaint.textAlign = Paint.Align.CENTER
        }
    }

    private fun drawGridIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        iconPaint.color = palette.icon
        iconFill.color = palette.icon
        val cell = radius * 0.72f
        val gap = radius * 0.24f
        for (row in 0..1) {
            for (column in 0..1) {
                scratch.set(
                    cx - radius + column * (cell + gap),
                    cy - radius + row * (cell + gap),
                    cx - radius + column * (cell + gap) + cell,
                    cy - radius + row * (cell + gap) + cell,
                )
                canvas.drawRoundRect(scratch, 1.5f * density, 1.5f * density, iconPaint)
            }
        }
    }

    private fun drawSmallPill(
        canvas: Canvas,
        left: Float,
        right: Float,
        top: Float,
        height: Float,
        label: String,
        primary: Boolean,
    ) {
        scratch.set(left, top, right, top + height)
        fillPaint.color = if (primary) palette.accent else Color.TRANSPARENT
        canvas.drawRoundRect(scratch, height / 2f, height / 2f, fillPaint)
        textPaint.color = if (primary) palette.accentText else palette.icon
        textPaint.textSize = width * 0.026f
        canvas.drawText(label, scratch.centerX(), scratch.centerY() + 5f * density, textPaint)
    }

    private fun drawSendPill(canvas: Canvas, left: Float, right: Float, top: Float, height: Float, centerY: Float) {
        scratch.set(left, top, right, top + height)
        fillPaint.color = palette.accent
        canvas.drawRoundRect(scratch, height / 2f, height / 2f, fillPaint)
        textPaint.color = palette.accentText
        textPaint.textSize = width * 0.028f
        canvas.drawText("Send", scratch.centerX() - dp(6f), centerY + 5f * density, textPaint)
        iconPaint.color = palette.accentText
        val arrowX = scratch.centerX() + dp(24f)
        val arrowSize = dp(5f)
        path.reset()
        path.moveTo(arrowX - arrowSize, centerY)
        path.lineTo(arrowX + arrowSize, centerY)
        path.moveTo(arrowX + arrowSize * 0.2f, centerY - arrowSize * 0.8f)
        path.lineTo(arrowX + arrowSize, centerY)
        path.lineTo(arrowX + arrowSize * 0.2f, centerY + arrowSize * 0.8f)
        canvas.drawPath(path, iconPaint)
    }

    private fun drawGearIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        iconPaint.color = palette.icon
        canvas.drawCircle(cx, cy, radius * 0.55f, iconPaint)
        for (index in 0 until 8) {
            val angle = Math.toRadians(index * 45.0)
            val startX = cx + (radius * 0.62f * Math.cos(angle)).toFloat()
            val startY = cy + (radius * 0.62f * Math.sin(angle)).toFloat()
            val endX = cx + (radius * 0.95f * Math.cos(angle)).toFloat()
            val endY = cy + (radius * 0.95f * Math.sin(angle)).toFloat()
            canvas.drawLine(startX, startY, endX, endY, iconPaint)
        }
    }

    private fun drawClipboardCircle(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        fillPaint.color = palette.circle
        canvas.drawCircle(cx, cy, radius, fillPaint)
        iconPaint.color = palette.icon
        scratch.set(cx - radius * 0.42f, cy - radius * 0.34f, cx + radius * 0.42f, cy + radius * 0.5f)
        canvas.drawRoundRect(scratch, 2f * density, 2f * density, iconPaint)
        scratch.set(cx - radius * 0.2f, cy - radius * 0.55f, cx + radius * 0.2f, cy - radius * 0.28f)
        canvas.drawRoundRect(scratch, 1.5f * density, 1.5f * density, iconPaint)
    }

    private fun previewButtons(): Triple<RectF, RectF, RectF> {
        val top = toolbarTop()
        val height = toolbarHeight()
        val buttonWidth = min(dp(70f).toFloat(), width * 0.19f)
        val gap = dp(6f).toFloat()
        val right = width - dp(6f)
        val discard = RectF(right - buttonWidth, top + dp(3f), right, top + height - dp(3f))
        val send = RectF(discard.left - gap - buttonWidth, discard.top, discard.left - gap, discard.bottom)
        val insert = RectF(send.left - gap - buttonWidth, send.top, send.left - gap, send.bottom)
        return Triple(insert, send, discard)
    }

    private var undoVisible = false
    private var undoAction: (() -> Unit)? = null
    private var undoRect = RectF()

    /** "Sending… Undo" above the keyboard while a confirmed send waits. */
    fun showUndo(onUndo: () -> Unit) {
        undoVisible = true
        undoAction = onUndo
        invalidate()
    }

    fun hideUndo() {
        undoVisible = false
        undoAction = null
        invalidate()
    }

    private fun drawUndoBar(canvas: Canvas) {
        val top = toolbarTop()
        val height = toolbarHeight()
        fillPaint.color = palette.card
        canvas.drawRect(0f, top - dp(2f), width.toFloat(), top + height, fillPaint)
        smallPaint.textAlign = Paint.Align.LEFT
        smallPaint.textSize = 12.5f * density
        smallPaint.color = palette.text
        canvas.drawText("Sending\u2026", dp(14f), top + height / 2f + 4f * density, smallPaint)
        val buttonWidth = dp(78f).toFloat()
        undoRect.set(width - buttonWidth - dp(10f), top + dp(4f), width - dp(10f), top + height - dp(4f))
        textPaint.color = palette.accent
        textPaint.textSize = 13f * density
        canvas.drawText("Undo", undoRect.centerX(), undoRect.centerY() + 5f * density, textPaint)
        smallPaint.textAlign = Paint.Align.CENTER
    }

    private fun drawPreviewStrip(canvas: Canvas, top: Float, height: Float) {
        val bitmap = previewBitmap
        val (insert, send, discard) = previewButtons()
        if (bitmap != null) {
            val targetHeight = height - 6f * density
            val maxWidth = (insert.left - dp(14f)).coerceAtLeast(dp(24f))
            val scale = min(targetHeight / bitmap.height, maxWidth / bitmap.width)
            scratch.set(dp(8f), top + 3f * density, dp(8f) + bitmap.width * scale, top + 3f * density + bitmap.height * scale)
            canvas.drawBitmap(bitmap, null, scratch, fillPaint)
        }
        smallPaint.textAlign = Paint.Align.LEFT
        smallPaint.textSize = 11f * density
        smallPaint.color = palette.hint
        val note = statusNote ?: if (busy) "Rendering…" else previewMeta
        if (bitmap == null) {
            canvas.drawText(note, dp(10f), top + height / 2f + 4f * density, smallPaint)
        }
        smallPaint.textAlign = Paint.Align.CENTER

        if (previewBitmap != null || busy) {
            drawButton(canvas, discard, "Discard", false, palette.special)
            drawButton(canvas, send, "Send", true, palette.accent)
            drawButton(canvas, insert, if (busy) "…" else "Insert", true, palette.accent)
        } else {
            drawButton(canvas, discard, "Close", false, palette.circle)
        }
    }

    private fun drawButton(canvas: Canvas, rect: RectF, label: String, primary: Boolean, color: Int) {
        scratch.set(rect)
        fillPaint.color = color
        canvas.drawRoundRect(scratch, scratch.height() / 2f, scratch.height() / 2f, fillPaint)
        textPaint.color = if (primary) palette.accentText else palette.text
        textPaint.textSize = min(width * 0.031f, 12f * density)
        canvas.drawText(label, scratch.centerX(), scratch.centerY() + 4f * density, textPaint)
    }

    private fun drawKeys(canvas: Canvas, body: RectF) {
        for (placedKey in placed) {
            val key = placedKey.key
            val visual = placedKey.visual
            scratch.set(visual)
            if (scratch.bottom > body.bottom) scratch.bottom = body.bottom
            if (scratch.top < body.top) scratch.top = body.top
            if (scratch.width() <= 0f || scratch.height() <= 0f) continue
            val isPressed = pressed === placedKey
            fillPaint.color = when (key.style) {
                KeyStyle.NORMAL -> if (isPressed) palette.keyPressed else palette.key
                KeyStyle.SPECIAL -> if (isPressed) palette.specialPressed else palette.special
                KeyStyle.PILL -> if (isPressed) palette.pillPressed else palette.pill
                KeyStyle.ACCENT -> palette.accent
            }
            if (key.style == KeyStyle.PILL || key.kind == KeyKind.ACTION) {
                canvas.drawRoundRect(scratch, scratch.height() / 2f, scratch.height() / 2f, fillPaint)
            } else {
                val radius = min(metrics.radius, scratch.height() / 2f)
                canvas.drawRoundRect(scratch, radius, radius, fillPaint)
            }
            drawKeyContent(canvas, placedKey, scratch)
        }
    }

    private fun drawKeyContent(canvas: Canvas, placedKey: PlacedKey, box: RectF) {
        val key = placedKey.key
        val centerX = box.centerX()
        val centerY = box.centerY()
        textPaint.color = if (key.style == KeyStyle.ACCENT) palette.accentText else palette.text
        when (key.kind) {
            KeyKind.CHAR, KeyKind.MODE, KeyKind.COMMA, KeyKind.PERIOD -> {
                val stacked = key.stackedLabel
                if (stacked != null) {
                    textPaint.textSize = width * 0.022f
                    canvas.drawText(stacked.first, centerX, centerY - 3f * density, textPaint)
                    canvas.drawText(stacked.second, centerX, centerY + 11f * density, textPaint)
                } else {
                    val label = if (key.kind == KeyKind.CHAR && key.output.length == 1 &&
                        key.output[0].isLetter()
                    ) {
                        if (ShiftLogic.isUpper(shift)) key.output.uppercase() else key.output
                    } else {
                        key.label
                    }
                    textPaint.textSize = if (key.output.length == 1 && !key.output[0].isLetter()) {
                        width * 0.038f
                    } else if (key.label.length > 2) {
                        width * 0.026f
                    } else {
                        width * 0.036f
                    }
                    canvas.drawText(label, centerX, centerY + textPaint.textSize * 0.35f, textPaint)
                }
                key.hint?.let { hint ->
                    smallPaint.textAlign = Paint.Align.RIGHT
                    smallPaint.textSize = width * 0.022f
                    smallPaint.color = palette.hint
                    canvas.drawText(hint, box.right - 4f * density, box.top + 13f * density, smallPaint)
                    smallPaint.textAlign = Paint.Align.CENTER
                }
            }

            KeyKind.SHIFT -> drawShiftIcon(canvas, centerX, centerY, 11f * density)
            KeyKind.BACKSPACE -> drawBackspaceIcon(canvas, centerX, centerY, 11f * density)
            KeyKind.SPACE -> {
                textPaint.textSize = width * 0.026f
                textPaint.color = palette.hint
                canvas.drawText("English", centerX, centerY + 4f * density, textPaint)
            }
            KeyKind.ACTION -> drawActionIcon(canvas, centerX, centerY, 9f * density)
            KeyKind.GLOBE -> drawGlobeIcon(canvas, centerX, centerY, 9f * density)
            KeyKind.OPERATORS -> drawOperators(canvas, box)
        }
    }

    private fun drawShiftIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val active = ShiftLogic.isUpper(shift)
        iconPaint.color = if (active) palette.accent else palette.text
        path.reset()
        path.moveTo(cx, cy - size)
        path.lineTo(cx + size * 0.85f, cy - size * 0.05f)
        path.lineTo(cx, cy - size * 0.05f)
        path.lineTo(cx, cy + size * 0.8f)
        path.lineTo(cx, cy - size * 0.05f)
        path.lineTo(cx - size * 0.85f, cy - size * 0.05f)
        path.close()
        if (active) {
            iconFill.color = palette.accent
            iconFill.style = Paint.Style.FILL
            canvas.drawPath(path, iconFill)
        }
        iconPaint.style = if (active) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        canvas.drawPath(path, iconPaint)
        iconPaint.style = Paint.Style.STROKE
        if (shift == ShiftState.LOCKED) {
            iconFill.color = palette.accent
            canvas.drawRect(cx - size * 0.5f, cy + size * 1.05f, cx + size * 0.5f, cy + size * 1.3f, iconFill)
        }
    }

    private fun drawBackspaceIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        iconPaint.color = palette.text
        path.reset()
        val halfWidth = size * 1.15f
        val halfHeight = size * 0.8f
        path.moveTo(cx - halfWidth, cy)
        path.lineTo(cx - halfWidth + halfHeight, cy - halfHeight)
        path.lineTo(cx + halfWidth, cy - halfHeight)
        path.lineTo(cx + halfWidth, cy + halfHeight)
        path.lineTo(cx - halfWidth + halfHeight, cy + halfHeight)
        path.close()
        canvas.drawPath(path, iconPaint)
        canvas.drawLine(cx - size * 0.2f, cy - size * 0.35f, cx + size * 0.45f, cy + size * 0.35f, iconPaint)
        canvas.drawLine(cx + size * 0.45f, cy - size * 0.35f, cx - size * 0.2f, cy + size * 0.35f, iconPaint)
    }

    private fun drawGlobeIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        iconPaint.color = palette.text
        canvas.drawCircle(cx, cy, size, iconPaint)
        canvas.drawLine(cx - size, cy, cx + size, cy, iconPaint)
        scratch.set(cx - size * 0.45f, cy - size, cx + size * 0.45f, cy + size)
        canvas.drawOval(scratch, iconPaint)
    }

    private fun drawActionIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        iconPaint.color = palette.text
        iconFill.color = palette.text
        when (imeAction) {
            EditorInfo.IME_ACTION_SEARCH -> {
                canvas.drawCircle(cx - size * 0.2f, cy - size * 0.2f, size * 0.6f, iconPaint)
                canvas.drawLine(cx + size * 0.25f, cy + size * 0.25f, cx + size * 0.9f, cy + size * 0.9f, iconPaint)
            }
            EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_SEND -> {
                path.reset()
                path.moveTo(cx - size * 0.8f, cy)
                path.lineTo(cx + size * 0.9f, cy)
                path.moveTo(cx + size * 0.1f, cy - size * 0.75f)
                path.lineTo(cx + size * 0.9f, cy)
                path.lineTo(cx + size * 0.1f, cy + size * 0.75f)
                canvas.drawPath(path, iconPaint)
            }
            EditorInfo.IME_ACTION_DONE -> {
                path.reset()
                path.moveTo(cx - size * 0.75f, cy)
                path.lineTo(cx - size * 0.15f, cy + size * 0.6f)
                path.lineTo(cx + size * 0.8f, cy - size * 0.65f)
                canvas.drawPath(path, iconPaint)
            }
            else -> {
                textPaint.color = palette.text
                textPaint.textSize = width * 0.034f
                canvas.drawText("\u23CE", cx, cy + 6f * density, textPaint)
            }
        }
    }

    private fun drawOperators(canvas: Canvas, box: RectF) {
        val symbols = listOf("+", "\u2212", "*", "/")
        textPaint.color = palette.text
        textPaint.textSize = width * 0.034f
        val slot = box.height() / symbols.size
        symbols.forEachIndexed { index, symbol ->
            canvas.drawText(symbol, box.centerX(), box.top + slot * index + slot / 2f + 4f * density, textPaint)
        }
        fillPaint.color = palette.hint
        canvas.drawRect(box.right - 2.5f * density, box.top + 8f * density, box.right - 1f * density, box.bottom - 8f * density, fillPaint)
    }

    private fun drawPopup(canvas: Canvas) {
        if (popupAlternates.isEmpty()) return
        val key = pressed ?: return
        val keyRect = RectF(key.visual)
        val total = popupAlternates.size
        val cellWidth = dp(44f).toFloat()
        val widthNeeded = cellWidth * total + dp(10f)
        val left = (keyRect.centerX() - widthNeeded / 2f).coerceIn(dp(4f), width - widthNeeded - dp(4f))
        val bottom = (keyRect.top - 6f * density).coerceAtLeast(frame.toolbarHeightPx + dp(4f))
        val top = bottom - dp(52f)
        popupRect.set(left, top, left + widthNeeded, bottom)

        fillPaint.color = palette.card
        canvas.drawRoundRect(popupRect, dp(12f), dp(12f), fillPaint)
        popupHitLeft = left
        iconPaint.color = palette.accent
        iconPaint.strokeWidth = 2f * density
        val selected = left + dp(5f) + cellWidth * popupIndex
        scratch.set(selected, popupRect.top + dp(4f), selected + cellWidth, popupRect.bottom - dp(4f))
        canvas.drawRoundRect(scratch, dp(9f), dp(9f), iconPaint)
        iconPaint.strokeWidth = 1.8f * density
        textPaint.color = palette.text
        textPaint.textSize = width * 0.038f
        popupAlternates.forEachIndexed { index, value ->
            canvas.drawText(
                value,
                left + dp(5f) + cellWidth * index + cellWidth / 2f,
                popupRect.centerY() + 7f * density,
                textPaint,
            )
        }
    }

    private var popupHitLeft = 0f

    // ------------------------------------------------------------------ panels

    private fun openPanel(target: Panel) {
        panel = target
        panelScroll = 0f
        toneTarget = null
        if (target != Panel.TEMPLATE_FORM) hideForm(clearValues = true)
        requestLayout()
        invalidate()
    }

    private fun headerHeight(): Float = dp(44f).toFloat()

    private fun drawPanel(canvas: Canvas) {
        val header = headerHeight()
        fillPaint.color = palette.panel
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
        val items = ArrayList<PanelItem>(24)

        iconPaint.color = palette.icon
        path.reset()
        path.moveTo(dp(18f), header / 2f)
        path.lineTo(dp(28f), header / 2f - dp(7f))
        path.moveTo(dp(18f), header / 2f)
        path.lineTo(dp(28f), header / 2f + dp(7f))
        canvas.drawPath(path, iconPaint)
        items.add(PanelItem(RectF(dp(4f), 0f, dp(40f), header), "close"))

        smallPaint.textAlign = Paint.Align.LEFT
        smallPaint.textSize = 13f * density
        smallPaint.color = palette.text
        val title = when (panel) {
            Panel.MENU -> "TextPic"
            Panel.CLIPBOARD -> "Clipboard"
            Panel.EMOJI -> "Emoji"
            Panel.EDITING -> "Text editing"
            Panel.TEMPLATES -> "Templates"
            Panel.TEMPLATE_FORM -> currentPlaceholder?.let { "Fill in: ${it.replaceFirstChar(Char::uppercase)}" }.orEmpty()
            Panel.NONE -> ""
        }
        canvas.drawText(title, dp(44f), header / 2f + 4.5f * density, smallPaint)
        smallPaint.textAlign = Paint.Align.CENTER

        when (panel) {
            Panel.MENU -> drawMenuPanel(canvas, header, items)
            Panel.CLIPBOARD -> drawClipboardPanel(canvas, header, items)
            Panel.EMOJI -> drawEmojiPanel(canvas, header, items)
            Panel.EDITING -> drawEditingPanel(canvas, header, items)
            Panel.TEMPLATES -> drawTemplatesPanel(canvas, header, items)
            Panel.TEMPLATE_FORM -> drawTemplateForm(canvas, header, items)
            Panel.NONE -> Unit
        }
        panelItems = items
    }

    private fun drawMenuPanel(canvas: Canvas, top: Float, items: MutableList<PanelItem>) {
        val entries = listOf(
            Triple("Clipboard", "clipboard", 0),
            Triple("Emoji", "emoji", 0),
            Triple("Templates", "templates", 0),
            Triple("Text editing", "editing", 0),
            Triple("One-handed", "onehanded", 0),
            Triple("Resize", "resize", 0),
            Triple("Theme", "theme", 0),
            Triple("Send: ${sendModeLabel()}", "sendmode", 0),
            Triple("Delay: ${prefs.sendDelayMs} ms", "senddelay", 0),
            Triple("Confirm send: ${if (prefs.confirmBeforeSend) "on" else "off"}", "sendconfirm", 0),
            Triple("Settings", "open_settings", 0),
        )
        val columns = 4
        val rows = (entries.size + columns - 1) / columns
        val areaTop = top + dp(8f)
        val cellWidth = width.toFloat() / columns
        val cellHeight = min((height - areaTop) / rows, dp(74f))
        entries.forEachIndexed { index, entry ->
            val column = index % columns
            val row = index / columns
            val rect = RectF(
                column * cellWidth + dp(6f),
                areaTop + row * cellHeight + dp(4f),
                (column + 1) * cellWidth - dp(6f),
                areaTop + (row + 1) * cellHeight - dp(4f),
            )
            fillPaint.color = palette.card
            canvas.drawRoundRect(rect, dp(12f), dp(12f), fillPaint)
            drawMenuGlyph(canvas, entry.second, rect.centerX(), rect.centerY() - dp(10f), 10f * density)
            smallPaint.textAlign = Paint.Align.CENTER
            smallPaint.textSize = 9.5f * density
            smallPaint.color = palette.text
            canvas.drawText(entry.first, rect.centerX(), rect.bottom - dp(7f), smallPaint)
            items.add(PanelItem(rect, entry.second))
        }
    }

    private fun sendModeLabel(): String = when (prefs.afterInsert) {
        AfterInsert.APP_ACTION -> "app action"
        AfterInsert.ENTER -> "Enter"
        AfterInsert.NOTHING -> "nothing"
    }

    private fun drawMenuGlyph(canvas: Canvas, kind: String, cx: Float, cy: Float, size: Float) {
        iconPaint.color = palette.icon
        when (kind) {
            "clipboard" -> {
                scratch.set(cx - size, cy - size * 0.8f, cx + size, cy + size)
                canvas.drawRoundRect(scratch, 2f * density, 2f * density, iconPaint)
                canvas.drawLine(cx - size * 0.4f, cy - size * 0.3f, cx + size * 0.4f, cy - size * 0.3f, iconPaint)
            }
            "emoji" -> canvas.drawCircle(cx, cy, size, iconPaint)
            "templates" -> {
                canvas.drawRect(cx - size, cy - size * 0.8f, cx + size, cy - size * 0.1f, iconPaint)
                canvas.drawRect(cx - size, cy + size * 0.2f, cx + size, cy + size * 0.9f, iconPaint)
            }
            "editing" -> {
                canvas.drawLine(cx - size, cy, cx + size, cy, iconPaint)
                canvas.drawLine(cx, cy - size, cx, cy + size, iconPaint)
            }
            "onehanded" -> canvas.drawRect(cx - size * 0.5f, cy - size, cx + size, cy + size, iconPaint)
            "resize" -> {
                canvas.drawLine(cx - size, cy + size, cx + size, cy - size, iconPaint)
                canvas.drawLine(cx + size * 0.2f, cy - size, cx + size, cy - size, iconPaint)
            }
            "theme" -> {
                canvas.drawCircle(cx, cy, size, iconPaint)
                scratch.set(cx - size, cy - size, cx + size, cy + size)
                canvas.drawArc(scratch, 90f, 180f, true, iconPaint)
            }
            "sendmode", "senddelay", "sendconfirm" -> {
                path.reset()
                path.moveTo(cx - size * 0.9f, cy)
                path.lineTo(cx + size * 0.9f, cy)
                path.moveTo(cx + size * 0.2f, cy - size * 0.7f)
                path.lineTo(cx + size * 0.9f, cy)
                path.lineTo(cx + size * 0.2f, cy + size * 0.7f)
                canvas.drawPath(path, iconPaint)
            }
            else -> drawGearIcon(canvas, cx, cy, size)
        }
    }

    private fun drawClipboardPanel(canvas: Canvas, top: Float, items: MutableList<PanelItem>) {
        val areaTop = top + dp(6f)
        val clearRect = RectF(width - dp(92f), top, width - dp(8f), top + dp(34f))
        fillPaint.color = palette.special
        canvas.drawRoundRect(clearRect, dp(17f), dp(17f), fillPaint)
        smallPaint.textAlign = Paint.Align.CENTER
        smallPaint.textSize = 11f * density
        smallPaint.color = palette.text
        canvas.drawText("Clear all", clearRect.centerX(), clearRect.centerY() + 4f * density, smallPaint)
        items.add(PanelItem(clearRect, "clip_clear"))

        if (clips.isEmpty()) {
            smallPaint.textSize = 12f * density
            smallPaint.color = palette.hint
            canvas.drawText(
                if (prefs.clipboardEnabled) "Nothing copied yet." else "Clipboard history is off.",
                width / 2f,
                areaTop + dp(40f),
                smallPaint,
            )
            return
        }

        val columns = 2
        val cardHeight = dp(62f).toFloat()
        val gap = dp(8f).toFloat()
        val cardWidth = (width - gap * (columns + 1)) / columns
        val availableHeight = height - areaTop
        val totalRows = (clips.size + columns - 1) / columns
        val contentHeight = totalRows * (cardHeight + gap)
        panelScrollMax = (contentHeight - availableHeight).coerceAtLeast(0f)
        panelScroll = panelScroll.coerceIn(0f, panelScrollMax)

        val save = canvas.save()
        canvas.clipRect(0f, areaTop, width.toFloat(), height.toFloat())
        clips.forEachIndexed { index, clip ->
            val column = index % columns
            val row = index / columns
            val left = gap + column * (cardWidth + gap)
            val cardTop = areaTop + gap + row * (cardHeight + gap) - panelScroll
            val rect = RectF(left, cardTop, left + cardWidth, cardTop + cardHeight)
            if (rect.bottom < areaTop || rect.top > height) return@forEachIndexed
            fillPaint.color = palette.card
            canvas.drawRoundRect(rect, dp(10f), dp(10f), fillPaint)
            smallPaint.textAlign = Paint.Align.LEFT
            smallPaint.textSize = 11f * density
            smallPaint.color = palette.text
            val line = clip.text.replace("\n", " ").take(46)
            canvas.drawText(line, rect.left + dp(8f), rect.top + dp(20f), smallPaint)
            smallPaint.textSize = 9f * density
            smallPaint.color = if (clip.pinned) palette.accent else palette.hint
            canvas.drawText(
                if (clip.pinned) "Pinned" else relativeTime(clip.createdAt),
                rect.left + dp(8f),
                rect.bottom - dp(10f),
                smallPaint,
            )
            items.add(PanelItem(rect, "clip", clip.text, clip.id.toInt()))
        }
        canvas.restoreToCount(save)
    }

    private fun relativeTime(createdAt: Long): String {
        val minutes = ((System.currentTimeMillis() - createdAt) / 60_000L).coerceAtLeast(0)
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 1_440 -> "${minutes / 60} h ago"
            else -> "${minutes / 1_440} d ago"
        }
    }

    private fun emojiTabs(): List<String> = listOf("Recent") + EmojiData.categories.map { it.label }

    private fun emojiList(): List<String> = if (emojiCategory == 0) {
        EmojiData.recent().ifEmpty { EmojiData.categories.first().emoji }
    } else {
        EmojiData.categories[emojiCategory - 1].emoji
    }

    private fun drawEmojiPanel(canvas: Canvas, top: Float, items: MutableList<PanelItem>) {
        val tabs = emojiTabs()
        val tabHeight = dp(30f).toFloat()
        val tabWidth = width.toFloat() / 5
        val firstRow = RectF(0f, top, width.toFloat(), top + tabHeight)
        var column = 0
        tabs.take(5).forEach { label ->
            val rect = RectF(column * tabWidth, top, (column + 1) * tabWidth, top + tabHeight)
            smallPaint.textAlign = Paint.Align.CENTER
            smallPaint.textSize = 10f * density
            smallPaint.color = if (column == emojiCategory) palette.accent else palette.hint
            canvas.drawText(label, rect.centerX(), rect.centerY() + 3.5f * density, smallPaint)
            items.add(PanelItem(rect, "emoji_tab", label, column))
            column++
        }
        val secondTop = top + tabHeight
        var secondColumn = 0
        tabs.drop(5).forEach { label ->
            val rect = RectF(secondColumn * tabWidth, secondTop, (secondColumn + 1) * tabWidth, secondTop + tabHeight)
            smallPaint.textAlign = Paint.Align.CENTER
            smallPaint.textSize = 10f * density
            smallPaint.color = if ((secondColumn + 5) == emojiCategory) palette.accent else palette.hint
            canvas.drawText(label, rect.centerX(), rect.centerY() + 3.5f * density, smallPaint)
            items.add(PanelItem(rect, "emoji_tab", label, secondColumn + 5))
            secondColumn++
        }

        val gridTop = secondTop + tabHeight + dp(6f)
        val bottomBar = dp(44f).toFloat()
        val availableHeight = (height - bottomBar - gridTop).coerceAtLeast(1f)
        val columns = 8
        val cell = (width - dp(12f)) / columns
        val list = emojiList()
        val rows = (list.size + columns - 1) / columns
        panelScrollMax = (rows * cell - availableHeight).coerceAtLeast(0f)
        panelScroll = panelScroll.coerceIn(0f, panelScrollMax)

        val save = canvas.save()
        canvas.clipRect(0f, gridTop, width.toFloat(), height - bottomBar)
        list.forEachIndexed { index, emoji ->
            val columnIndex = index % columns
            val rowIndex = index / columns
            val left = dp(6f) + columnIndex * cell
            val cellTop = gridTop + rowIndex * cell - panelScroll
            if (cellTop + cell < gridTop || cellTop > height - bottomBar) return@forEachIndexed
            textPaint.textSize = cell * 0.62f
            textPaint.color = palette.text
            canvas.drawText(emoji, left + cell / 2f, cellTop + cell * 0.72f, textPaint)
            items.add(PanelItem(RectF(left, cellTop, left + cell, cellTop + cell), "emoji", emoji))
        }
        canvas.restoreToCount(save)

        // A thin scroll indicator, so the grid does not look like it ends.
        if (panelScrollMax > 0f) {
            val trackHeight = height - bottomBar - gridTop - dp(8f)
            val thumb = trackHeight * (availableHeight / (availableHeight + panelScrollMax))
            val thumbTop = gridTop + dp(4f) + (trackHeight - thumb) * (panelScroll / panelScrollMax)
            fillPaint.color = palette.hint
            fillPaint.alpha = 90
            canvas.drawRoundRect(
                width - dp(3f),
                thumbTop,
                width - dp(1f),
                thumbTop + thumb,
                dp(1f),
                dp(1f),
                fillPaint,
            )
            fillPaint.alpha = 255
        }

        val barTop = height - bottomBar
        val abcRect = RectF(dp(6f), barTop + dp(4f), width * 0.3f, height - dp(4f))
        fillPaint.color = palette.special
        canvas.drawRoundRect(abcRect, dp(8f), dp(8f), fillPaint)
        textPaint.color = palette.text
        textPaint.textSize = 13f * density
        canvas.drawText("ABC", abcRect.centerX(), abcRect.centerY() + 5f * density, textPaint)
        items.add(PanelItem(abcRect, "emoji_abc"))

        val backRect = RectF(width - dp(70f), barTop + dp(4f), width - dp(6f), height - dp(4f))
        canvas.drawRoundRect(backRect, dp(8f), dp(8f), fillPaint)
        drawBackspaceIcon(canvas, backRect.centerX(), backRect.centerY(), 10f * density)
        items.add(PanelItem(backRect, "backspace"))

        drawTonePopup(canvas, items)
    }

    /** Five tone swatches above the emoji that was long-pressed. */
    private fun drawTonePopup(canvas: Canvas, items: MutableList<PanelItem>) {
        val base = toneTarget ?: return
        val total = EmojiData.SKIN_TONES.size + 1
        val cell = dp(38f).toFloat()
        val needed = cell * total + dp(10f)
        val left = (toneRect.centerX() - needed / 2f).coerceIn(dp(4f), width - needed - dp(4f))
        val bottom = (toneRect.top - dp(6f)).coerceAtLeast(headerHeight() + dp(6f))
        val top = bottom - dp(48f)
        val popup = RectF(left, top, left + needed, bottom)
        fillPaint.color = palette.card
        canvas.drawRoundRect(popup, dp(12f), dp(12f), fillPaint)
        textPaint.textSize = cell * 0.55f
        var index = 0
        // The plain emoji first, then the five tones.
        canvas.drawText(base, popup.left + dp(5f) + cell * 0.5f, popup.centerY() + cell * 0.18f, textPaint.apply { color = palette.text })
        items.add(
            PanelItem(
                RectF(popup.left + dp(5f), popup.top, popup.left + dp(5f) + cell, popup.bottom),
                "emoji_tone",
                base,
                0,
            ),
        )
        EmojiData.SKIN_TONES.forEachIndexed { toneIndex, tone ->
            val cx = popup.left + dp(5f) + cell * (toneIndex + 1) + cell / 2f
            canvas.drawText(EmojiData.withSkinTone(base, tone), cx, popup.centerY() + cell * 0.18f, textPaint.apply {
                textSize = cell * 0.42f
            })
            items.add(
                PanelItem(
                    RectF(cx - cell / 2f, popup.top, cx + cell / 2f, popup.bottom),
                    "emoji_tone",
                    tone,
                    toneIndex + 1,
                ),
            )
            index = toneIndex
        }
        if (index < 0) return
    }

    private fun drawTemplatesPanel(canvas: Canvas, top: Float, items: MutableList<PanelItem>) {
        val areaTop = top + dp(8f)
        if (templates.isEmpty()) {
            smallPaint.textAlign = Paint.Align.CENTER
            smallPaint.textSize = 12f * density
            smallPaint.color = palette.hint
            canvas.drawText("No templates yet.", width / 2f, areaTop + dp(30f), smallPaint)
            return
        }
        val cardHeight = dp(56f).toFloat()
        val gap = dp(8f).toFloat()
        val cardWidth = width - dp(16f)
        val available = height - areaTop
        panelScrollMax = (templates.size * (cardHeight + gap) - available).coerceAtLeast(0f)
        panelScroll = panelScroll.coerceIn(0f, panelScrollMax)
        val save = canvas.save()
        canvas.clipRect(0f, areaTop, width.toFloat(), height.toFloat())
        templates.forEachIndexed { index, template ->
            val cardTop = areaTop + gap + index * (cardHeight + gap) - panelScroll
            val rect = RectF(dp(8f), cardTop, dp(8f) + cardWidth, cardTop + cardHeight)
            if (rect.bottom < areaTop || rect.top > height) return@forEachIndexed
            fillPaint.color = palette.card
            canvas.drawRoundRect(rect, dp(10f), dp(10f), fillPaint)
            smallPaint.textAlign = Paint.Align.LEFT
            smallPaint.textSize = 12.5f * density
            smallPaint.color = palette.text
            canvas.drawText(template.title, rect.left + dp(10f), rect.top + dp(22f), smallPaint)
            smallPaint.textSize = 10f * density
            smallPaint.color = palette.hint
            canvas.drawText(
                template.body.replace("\n", " ").take(56),
                rect.left + dp(10f),
                rect.top + dp(38f),
                smallPaint,
            )
            if (TemplateRules.needsValues(template)) {
                smallPaint.color = palette.accent
                smallPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText("fill in", rect.right - dp(10f), rect.top + dp(22f), smallPaint)
                smallPaint.textAlign = Paint.Align.LEFT
            }
            items.add(PanelItem(rect, "template", template.body, index))
        }
        canvas.restoreToCount(save)
    }

    private fun drawTemplateForm(canvas: Canvas, top: Float, items: MutableList<PanelItem>) {
        val promptTop = top + dp(10f)
        smallPaint.textAlign = Paint.Align.LEFT
        smallPaint.textSize = 12f * density
        smallPaint.color = palette.hint
        val template = pendingTemplate
        canvas.drawText(
            template?.title.orEmpty(),
            dp(12f),
            promptTop + dp(4f),
            smallPaint,
        )
        // The text field itself is a real child view, positioned in positionForm().
        val formHeight = dp(44f).toFloat()
        val formTop = promptTop + dp(12f)
        scratch.set(dp(12f), formTop, width - dp(12f), formTop + formHeight)
        fillPaint.color = palette.card
        canvas.drawRoundRect(scratch, dp(10f), dp(10f), fillPaint)

        val buttonWidth = dp(74f).toFloat()
        val gap = dp(8f).toFloat()
        val buttonTop = formTop + formHeight + dp(10f)
        val buttonHeight = dp(38f).toFloat()
        val skip = RectF(width - dp(12f) - buttonWidth * 2 - gap, buttonTop, width - dp(12f) - buttonWidth - gap, buttonTop + buttonHeight)
        val next = RectF(width - dp(12f) - buttonWidth, buttonTop, width - dp(12f), buttonTop + buttonHeight)
        drawButton(canvas, skip, "Skip", false, palette.special)
        drawButton(canvas, next, "Next", true, palette.accent)
        items.add(PanelItem(skip, "placeholder_skip"))
        items.add(PanelItem(next, "placeholder_next"))
    }

    private fun positionForm() {
        if (panel != Panel.TEMPLATE_FORM) {
            formField.visibility = GONE
            return
        }
        val header = headerHeight()
        val promptTop = header + dp(10f)
        val height = dpx(44f)
        val top = (promptTop + dp(12f)).toInt()
        formField.visibility = VISIBLE
        formField.layout(dpx(12f), top, width - dpx(12f), top + height)
    }

    private fun drawEditingPanel(canvas: Canvas, top: Float, items: MutableList<PanelItem>) {
        val entries = listOf(
            "\u2190" to "left",
            "\u2192" to "right",
            "Home" to "home",
            "End" to "end",
            "Select all" to "selectall",
            "Cut" to "cut",
            "Copy" to "copy",
            "Paste" to "paste",
        )
        val columns = 4
        val rows = 2
        val areaTop = top + dp(10f)
        val cellWidth = width.toFloat() / columns
        val cellHeight = min((height - areaTop - dp(10f)) / rows, dp(66f))
        entries.forEachIndexed { index, entry ->
            val column = index % columns
            val row = index / columns
            val rect = RectF(
                column * cellWidth + dp(6f),
                areaTop + row * cellHeight + dp(4f),
                (column + 1) * cellWidth - dp(6f),
                areaTop + (row + 1) * cellHeight - dp(4f),
            )
            fillPaint.color = palette.card
            canvas.drawRoundRect(rect, dp(12f), dp(12f), fillPaint)
            textPaint.color = palette.text
            textPaint.textSize = if (entry.first.length <= 2) 20f * density else 11f * density
            canvas.drawText(entry.first, rect.centerX(), rect.centerY() + 6f * density, textPaint)
            items.add(PanelItem(rect, "edit", entry.second))
        }
    }

    // ------------------------------------------------------------------ touch

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return handleDown(event)
            MotionEvent.ACTION_MOVE -> return handleMove(event)
            MotionEvent.ACTION_UP -> return handleUp(event)
            MotionEvent.ACTION_POINTER_DOWN -> return handleExtraPointer(event)
            MotionEvent.ACTION_POINTER_UP -> return true
            MotionEvent.ACTION_CANCEL -> {
                resetGesture()
                return true
            }
        }
        return true
    }

    private fun handleDown(event: MotionEvent): Boolean {
        if (undoVisible) {
            if (undoRect.contains(event.x, event.y)) undoAction?.invoke()
            return true
        }
        if (panel != Panel.NONE) {
            panelScrollVelocity = 0f
            handler.removeCallbacks(fling)
            velocityTracker?.recycle()
            velocityTracker = android.view.VelocityTracker.obtain().also { it.addMovement(event) }
            panelItems.firstOrNull { it.rect.contains(event.x, event.y) }?.let { item ->
                panelScrollAnchor = event.y
                panelScrollStart = panelScroll
                pressedPanelItem = item
                if (item.kind == "emoji" && EmojiData.supportsTone(item.value)) {
                    pressedEmoji = item
                    handler.postDelayed(emojiLongPress, KeyboardPrefRules.longPressMs(prefs.longPressMs))
                }
                return true
            }
            panelScrollAnchor = event.y
            panelScrollStart = panelScroll
            return true
        }
        if (previewBitmap != null || statusNote != null || busy) {
            return handlePreviewTouch(event)
        }
        toolbarActionAt(event.x, event.y)?.let { action ->
            pressedToolbarAction = action
            when (action) {
                "render" -> {
                    listener?.onRender()
                    handler.postDelayed(renderLongPress, 450L)
                }
                "send" -> listener?.onSend()
                "clipboard" -> openPanel(Panel.CLIPBOARD)
                "gear", "grid" -> openPanel(Panel.MENU)
            }
            invalidate()
            return true
        }
        val hit = KeyboardGeometry.keyAt(placed, event.x, event.y) ?: return true
        pressed = hit
        pressedTouch.set(hit.touch)
        spaceSliding = false
        spaceAccum = 0f
        shiftSliding = hit.key.kind == KeyKind.SHIFT
        backspaceSliding = false
        backspaceAccum = 0f
        popupAlternates = emptyList()
        feedback.onKeyDown(prefs)
        if (hit.key.kind == KeyKind.BACKSPACE) {
            handler.postDelayed(backspaceRepeat, 400L)
        } else {
            handler.postDelayed(longPress, KeyboardPrefRules.longPressMs(prefs.longPressMs))
        }
        invalidate()
        return true
    }

    private var pressedEmoji: PanelItem? = null

    private var velocityTracker: android.view.VelocityTracker? = null

    /** Continues a panel scroll after the finger lifts, slowing to a stop. */
    private val fling = object : Runnable {
        override fun run() {
            if (panelScrollVelocity == 0f) return
            panelScroll = (panelScroll + panelScrollVelocity * 0.016f).coerceIn(0f, panelScrollMax)
            panelScrollVelocity *= 0.92f
            if (abs(panelScrollVelocity) < 20f) {
                panelScrollVelocity = 0f
            } else {
                handler.postDelayed(this, 16L)
            }
            invalidate()
        }
    }
    private var panelScrollVelocity = 0f

    /** Long-press on a hand or face opens the five skin tones. */
    private val emojiLongPress = object : Runnable {
        override fun run() {
            val item = pressedEmoji ?: return
            pressedEmoji = null
            pressedPanelItem = null
            toneTarget = item.value
            toneRect.set(item.rect)
            feedback.onKeyDown(prefs)
            invalidate()
        }
    }

    private var pressedToolbarAction: String? = null
    private var pressedPanelItem: PanelItem? = null
    private var panelScrollAnchor = 0f
    private var panelScrollStart = 0f

    private fun handlePreviewTouch(event: MotionEvent): Boolean {
        val (insert, send, discard) = previewButtons()
        if (previewBitmap != null || busy) {
            if (insert.contains(event.x, event.y)) {
                if (!busy) listener?.onInsert()
                return true
            }
            if (send.contains(event.x, event.y)) {
                if (!busy) listener?.onSend()
                return true
            }
            if (discard.contains(event.x, event.y)) {
                listener?.onDiscard()
                return true
            }
            return true
        }
        if (discard.contains(event.x, event.y)) listener?.onDiscard()
        return true
    }

    /**
     * A second thumb landing while the first is still down commits its own key
     * straight away, so fast two-thumb typing never drops or reorders letters.
     */
    private fun handleExtraPointer(event: MotionEvent): Boolean {
        if (panel != Panel.NONE) return true
        if (previewBitmap != null || statusNote != null || busy) return true
        val index = event.actionIndex
        val x = event.getX(index)
        val y = event.getY(index)
        val hit = KeyboardGeometry.keyAt(placed, x, y) ?: return true
        feedback.onKeyDown(prefs)
        commitKey(hit)
        invalidate()
        return true
    }

    private fun handleMove(event: MotionEvent): Boolean {
        if (panel != Panel.NONE) {
            velocityTracker?.addMovement(event)
            if (pressedPanelItem == null && panelScrollMax > 0f) {
                panelScroll = (panelScrollStart - (event.y - panelScrollAnchor)).coerceIn(0f, panelScrollMax)
                invalidate()
            }
            return true
        }
        if (previewBitmap != null || statusNote != null || busy) return true
        val current = pressed ?: return true
        if (popupAlternates.isNotEmpty()) {
            val index = popupIndexAt(event.x)
            if (index != popupIndex) {
                popupIndex = index
                invalidate()
            }
            return true
        }
        val key = current.key
        when (key.kind) {
            KeyKind.SPACE -> {
                val delta = event.x - pressedTouch.centerX()
                if (!spaceSliding && abs(delta) > 18f * density) {
                    spaceSliding = true
                    spaceAccum = 0f
                }
                if (spaceSliding) {
                    spaceAccum += delta
                    val step = 14f * density
                    while (spaceAccum > step) {
                        listener?.onCursorMove(1)
                        spaceAccum -= step
                    }
                    while (spaceAccum < -step) {
                        listener?.onCursorMove(-1)
                        spaceAccum += step
                    }
                }
            }
            KeyKind.BACKSPACE -> {
                val delta = event.x - pressedTouch.centerX()
                if (delta < -24f * density) {
                    backspaceSliding = true
                    backspaceAccum -= delta
                    if (backspaceAccum > 40f * density) {
                        backspaceAccum = 0f
                        listener?.onBackspaceDragSelect()
                    }
                }
            }
            KeyKind.SHIFT -> Unit
            else -> {
                if (!current.touch.contains(event.x, event.y)) {
                    pressed = null
                    handler.removeCallbacks(longPress)
                    invalidate()
                }
            }
        }
        return true
    }

    private fun handleUp(event: MotionEvent): Boolean {
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(backspaceRepeat)
        handler.removeCallbacks(renderLongPress)
        if (panel != Panel.NONE) {
            handler.removeCallbacks(emojiLongPress)
            val tracker = velocityTracker
            if (tracker != null && pressedPanelItem == null) {
                tracker.computeCurrentVelocity(1_000)
                val velocity = -tracker.yVelocity
                if (abs(velocity) > 120f) {
                    panelScrollVelocity = velocity * 0.35f
                    handler.post(fling)
                }
            }
            velocityTracker = null
            tracker?.recycle()
            val item = pressedPanelItem
            pressedPanelItem = null
            pressedEmoji = null
            if (item != null && item.rect.contains(event.x, event.y)) handlePanelItem(item)
            invalidate()
            return true
        }
        pressedToolbarAction = null
        if (previewBitmap != null || statusNote != null || busy) return true
        lastUpY = event.y
        val current = pressed
        pressed = null
        if (current == null) {
            invalidate()
            return true
        }
        if (popupAlternates.isNotEmpty()) {
            val value = popupAlternates[popupIndex.coerceIn(0, popupAlternates.lastIndex)]
            popupAlternates = emptyList()
            listener?.onText(value)
            shift = ShiftLogic.afterChar(shift)
            invalidate()
            return true
        }
        commitKey(current)
        invalidate()
        return true
    }

    private fun commitKey(placedKey: PlacedKey) {
        val key = placedKey.key
        when (key.kind) {
            KeyKind.CHAR -> {
                val text = if (key.output.length == 1 && key.output[0].isLetter() &&
                    ShiftLogic.isUpper(shift)
                ) {
                    key.output.uppercase()
                } else {
                    key.output
                }
                listener?.onText(text)
                shift = ShiftLogic.afterChar(shift)
                if (prefs.autoCapitalize && shift == ShiftState.OFF) {
                    shift = ShiftLogic.fromCapsMode(capsMode, shift)
                }
            }
            KeyKind.SHIFT -> shift = ShiftLogic.afterTap(shift)
            KeyKind.BACKSPACE -> if (!backspaceSliding) listener?.onBackspace()
            KeyKind.SPACE -> if (!spaceSliding) listener?.onSpace()
            KeyKind.ACTION -> {
                if (imeAction == EditorInfo.IME_ACTION_NONE ||
                    imeAction == EditorInfo.IME_ACTION_UNSPECIFIED
                ) {
                    listener?.onEnter()
                } else {
                    listener?.onEditorAction(imeAction)
                }
            }
            KeyKind.MODE -> {
                val target = key.target ?: KeyboardSpecs.nextPage(page, key.label)
                if (target != null) {
                    page = target
                    rebuildLayout()
                }
            }
            KeyKind.GLOBE -> listener?.onGlobe()
            KeyKind.COMMA -> {
                listener?.onText(",")
                shift = ShiftLogic.afterChar(shift)
            }
            KeyKind.PERIOD -> {
                listener?.onText(".")
                shift = ShiftLogic.afterChar(shift)
            }
            KeyKind.OPERATORS -> {
                val symbols = listOf("+", "\u2212", "*", "/")
                val slot = placedKey.visual.height() / symbols.size
                val offset = (lastUpY - placedKey.visual.top).coerceIn(0f, placedKey.visual.height() - 1f)
                val index = (offset / slot).toInt().coerceIn(0, symbols.lastIndex)
                listener?.onText(symbols[index])
            }
        }
    }

    private var lastUpY = 0f

    private fun popupIndexAt(x: Float): Int {
        if (popupAlternates.isEmpty()) return 0
        val cellWidth = dp(44f).toFloat()
        val index = ((x - popupHitLeft) / cellWidth).toInt()
        return index.coerceIn(0, popupAlternates.lastIndex)
    }

    private fun toolbarActionAt(x: Float, y: Float): String? {
        val top = toolbarTop()
        if (y < top || y > top + toolbarHeight()) return null
        if (previewBitmap != null || statusNote != null || busy) return null
        val slot = width / 5f
        return when {
            x < slot -> "grid"
            x < slot * 2 -> "render"
            x < slot * 3 -> "send"
            x < slot * 4 -> "gear"
            else -> "clipboard"
        }
    }

    private fun handlePanelItem(item: PanelItem) {
        when (item.kind) {
            "close" -> closePanel()
            "clipboard" -> openPanel(Panel.CLIPBOARD)
            "emoji" -> openPanel(Panel.EMOJI)
            "templates" -> openPanel(Panel.TEMPLATES)
            "editing" -> openPanel(Panel.EDITING)
            "open_settings" -> listener?.onOpenAppSettings()
            "sendmode" -> {
                prefs = prefs.copy(
                    afterInsert = when (prefs.afterInsert) {
                        AfterInsert.APP_ACTION -> AfterInsert.ENTER
                        AfterInsert.ENTER -> AfterInsert.NOTHING
                        AfterInsert.NOTHING -> AfterInsert.APP_ACTION
                    },
                )
                prefsChanged?.invoke(prefs)
                invalidate()
            }
            "senddelay" -> {
                val next = ((prefs.sendDelayMs / 100 + 1) % 14 + 2) * 100
                prefs = prefs.copy(sendDelayMs = KeyboardPrefRules.sendDelayMs(next))
                prefsChanged?.invoke(prefs)
                invalidate()
            }
            "sendconfirm" -> {
                prefs = prefs.copy(confirmBeforeSend = !prefs.confirmBeforeSend)
                prefsChanged?.invoke(prefs)
                invalidate()
            }
            "onehanded" -> {
                prefs = prefs.copy(
                    oneHanded = when (prefs.oneHanded) {
                        OneHandedSide.OFF -> OneHandedSide.LEFT
                        OneHandedSide.LEFT -> OneHandedSide.RIGHT
                        OneHandedSide.RIGHT -> OneHandedSide.OFF
                    },
                )
                prefsChanged?.invoke(prefs)
                palette = KeyboardPalettes.of(prefs.theme, darkMode)
                rebuildLayout()
                closePanel()
            }
            "resize" -> {
                prefs = prefs.copy(
                    heightLevel = KeyboardPrefRules.clampLevel(prefs.heightLevel + 1),
                )
                prefsChanged?.invoke(prefs)
                requestLayout()
                rebuildLayout()
                invalidate()
            }
            "theme" -> {
                prefs = prefs.copy(
                    theme = when (prefs.theme) {
                        KeyboardTheme.DARK -> KeyboardTheme.LIGHT
                        KeyboardTheme.LIGHT -> KeyboardTheme.SYSTEM
                        KeyboardTheme.SYSTEM -> KeyboardTheme.DARK
                    },
                )
                prefsChanged?.invoke(prefs)
                palette = KeyboardPalettes.of(prefs.theme, darkMode)
                onAppearanceChanged?.invoke()
                invalidate()
            }
            "clip" -> {
                listener?.onText(item.value)
                closePanel()
            }
            "clip_clear" -> {
                clips = clips.filter { it.pinned }
                clipsCleared?.invoke()
                invalidate()
            }
            "emoji_tab" -> {
                emojiCategory = item.payload
                panelScroll = 0f
                toneTarget = null
                invalidate()
            }
            "emoji" -> {
                val tone = emojiTones[item.value]
                val text = if (tone != null && EmojiData.supportsTone(item.value)) {
                    EmojiData.withSkinTone(item.value, tone)
                } else {
                    item.value
                }
                EmojiData.noteUsed(item.value)
                listener?.onText(text)
                toneTarget = null
                invalidate()
            }
            "emoji_tone" -> {
                val base = toneTarget
                if (base != null) {
                    if (item.payload == 0) {
                        emojiTones.remove(base)
                        EmojiData.noteUsed(base)
                        listener?.onText(base)
                    } else {
                        emojiTones[base] = item.value
                        EmojiData.noteUsed(base)
                        listener?.onText(EmojiData.withSkinTone(base, item.value))
                    }
                }
                toneTarget = null
                invalidate()
            }
            "template" -> {
                val template = templates.getOrNull(item.payload) ?: return
                startTemplate(template)
            }
            "placeholder_next" -> acceptPlaceholder()
            "placeholder_skip" -> {
                currentPlaceholder?.let { key -> filledValues.remove(key) }
                advancePlaceholder()
            }
            "emoji_abc" -> closePanel()
            "backspace" -> listener?.onBackspace()
            "edit" -> when (item.value) {
                "left" -> listener?.onCursorMove(-1)
                "right" -> listener?.onCursorMove(1)
                "home" -> listener?.onCursorMove(-10_000)
                "end" -> listener?.onCursorMove(10_000)
                "selectall" -> listener?.onSelectAll()
                "cut" -> listener?.onCut()
                "copy" -> listener?.onCopy()
                "paste" -> listener?.onPaste()
            }
        }
    }

    // ---------------------------------------------------------------- templates

    private fun startTemplate(template: TemplateEntity) {
        val tokens = TemplateRules.placeholders(template.body)
        if (tokens.isEmpty()) {
            listener?.onTemplate(template)
            closePanel()
            return
        }
        pendingTemplate = template
        pendingPlaceholders = tokens
        filledValues.clear()
        panel = Panel.TEMPLATE_FORM
        advancePlaceholder()
    }

    private fun advancePlaceholder() {
        val next = pendingPlaceholders.firstOrNull { it !in filledValues }
        if (next == null) {
            finishTemplate()
            return
        }
        currentPlaceholder = next
        formField.visibility = VISIBLE
        formField.setText("")
        formField.hint = next.replaceFirstChar(Char::uppercase)
        positionForm()
        requestLayout()
        invalidate()
        formField.requestFocus()
        formField.post {
            runCatching {
                val imm = context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                imm?.showSoftInput(formField, 0)
            }
        }
    }

    private fun acceptPlaceholder() {
        val key = currentPlaceholder ?: return
        val value = formField.text?.toString()?.trim().orEmpty()
        if (value.isNotEmpty()) filledValues[key] = value
        advancePlaceholder()
    }

    private fun finishTemplate() {
        val template = pendingTemplate
        val values = filledValues.toMap()
        hideForm(clearValues = true)
        closePanel()
        if (template != null) {
            listener?.onText(TemplateRules.fill(template.body, values))
        }
    }

    private fun hideForm(clearValues: Boolean) {
        formField.visibility = GONE
        formField.clearFocus()
        if (clearValues) {
            pendingTemplate = null
            pendingPlaceholders = emptyList()
            filledValues.clear()
            currentPlaceholder = null
        }
    }

    /** Nullable hooks the IME wires to persistence. */
    var prefsChanged: ((KeyboardPrefs) -> Unit)? = null
    var clipsCleared: (() -> Unit)? = null

    private fun resetGesture() {
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(backspaceRepeat)
        pressed = null
        pressedPanelItem = null
        popupAlternates = emptyList()
        spaceSliding = false
        shiftSliding = false
        backspaceSliding = false
        invalidate()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        soundPlayer.release()
        previewBitmap = null
        super.onDetachedFromWindow()
    }

    // ----------------------------------------------------------------- helpers

    /** A stronger vibration that confirms an image was sent. */
    fun confirmVibration() {
        feedback.onKeyDown(prefs, strong = true)
    }

    /** Used by the IME to show the clipboard chip in the toolbar area. */
    fun clipboardChip(text: String?) {
        if (text.isNullOrBlank()) return
        statusNote = "Copied: ${text.take(24)}"
        invalidate()
        handler.postDelayed({ statusNote = null; invalidate() }, 8_000L)
    }

    fun pressPreviewKey(label: String) {
        if (label.isEmpty()) return
    }
}
