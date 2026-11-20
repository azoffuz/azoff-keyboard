package uz.azoff.keyboard

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

// ======================= Ranglar =======================

val accentColors = listOf(
    Color(0xFF0F766E), Color(0xFF2563EB), Color(0xFF16A34A), Color(0xFFDC2626),
    Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF64748B),
)

data class KbColors(
    val bg: Color, val key: Color, val special: Color, val pressed: Color, val text: Color,
    val sub: Color, val accent: Color, val onAccent: Color, val popup: Color, val border: Color,
)

object BgImage {
    val version = MutableStateFlow(0)
    fun file(ctx: android.content.Context) = File(ctx.filesDir, "bg.jpg")
}

@Composable
fun kbScheme(s: KbSettings): ColorScheme {
    val dark = when (s.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    return if (s.dynamicColor && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        val a = accentColors[s.accent.coerceIn(0, accentColors.size - 1)]
        if (dark) darkColorScheme(primary = a, secondary = a, tertiary = a) else lightColorScheme(primary = a, secondary = a, tertiary = a)
    }
}

fun kbColors(c: ColorScheme, dark: Boolean, s: KbSettings): KbColors {
    val transparentKeys = s.bgImage
    fun t(x: Color) = if (transparentKeys) x.copy(alpha = 0.78f) else x
    return if (dark) KbColors(
        bg = c.surfaceContainerLowest,
        key = t(c.surfaceContainerHighest),
        special = t(c.surfaceContainerHigh),
        pressed = c.primary.copy(alpha = 0.35f),
        text = c.onSurface,
        sub = c.onSurfaceVariant,
        accent = c.primary,
        onAccent = c.onPrimary,
        popup = c.surfaceContainerHighest,
        border = c.outlineVariant,
    ) else KbColors(
        bg = c.surfaceContainer,
        key = t(c.surfaceContainerLowest),
        special = t(c.surfaceContainerHighest),
        pressed = c.primary.copy(alpha = 0.25f),
        text = c.onSurface,
        sub = c.onSurfaceVariant,
        accent = c.primary,
        onAccent = c.onPrimary,
        popup = c.surfaceContainerLowest,
        border = c.outlineVariant,
    )
}

// ======================= Ildiz =======================

@Composable
fun KeyboardRoot(kb: KbState) {
    val s by Store.settings.collectAsState()
    val bgVer by BgImage.version.collectAsState()
    val scheme = kbScheme(s)
    val dark = when (s.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val colors = kbColors(scheme, dark, s)
    val ctx = LocalContext.current
    val bg: ImageBitmap? = remember(s.bgImage, bgVer) {
        if (!s.bgImage) null else runCatching {
            BitmapFactory.decodeFile(BgImage.file(ctx).absolutePath)?.asImageBitmap()
        }.getOrNull()
    }

    MaterialTheme(colorScheme = scheme) {
        Box(Modifier.fillMaxWidth().background(colors.bg)) {
            if (bg != null) {
                Image(bg, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = s.bgDim)))
            }
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                TopStrip(kb, s, colors)
                val showKeys = kb.panel == Panel.NONE || kb.internal != Internal.NONE
                if (showKeys) {
                    when (kb.internal) {
                        Internal.EMOJI_SEARCH -> EmojiSearchStrip(kb, colors)
                        Internal.GIF_SEARCH -> GifStrip(kb, s, colors)
                        Internal.TRANSLATE -> TranslateBox(kb, s, colors)
                        Internal.NONE -> {}
                    }
                    KeyboardArea(kb, s, colors)
                } else {
                    PanelHost(kb, s, colors, keyboardHeightDp(kb, s))
                }
            }
            val msg = kb.message
            if (msg != null) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp, start = 16.dp, end = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.text.copy(alpha = 0.9f))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) { Text(msg, color = colors.bg, fontSize = 13.sp) }
            }
        }
    }
}

fun currentLayout(kb: KbState, s: KbSettings): KbLayout = when (kb.mode) {
    Mode.LETTERS -> Layouts.letters(s.currentLayout, s.numberRow)
    Mode.SYMBOLS -> Layouts.symbols(kb.symPage, s.currentLayout)
    Mode.NUMPAD -> Layouts.numpad(false)
    Mode.PHONE -> Layouts.numpad(true)
}

fun keyboardHeightDp(kb: KbState, s: KbSettings): Int {
    val l = currentLayout(kb, s)
    val hasNum = kb.mode == Mode.LETTERS && s.numberRow
    val rows = l.rows.size
    return if (hasNum) ((rows - 1) * s.keyHeight + s.keyHeight * 0.8f).toInt() + 8 else rows * s.keyHeight + 8
}

// ======================= Tugmalar maydoni =======================

class KeyBox(val key: Key, val row: Int, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
}

fun computeBoxes(layout: KbLayout, widthPx: Float, rowH: Float, numRowH: Float, hasNumberRow: Boolean, split: Boolean): List<KeyBox> {
    val gapUnits = if (split) 1.6f else 0f
    val unit = widthPx / (layout.units + gapUnits)
    val out = ArrayList<KeyBox>()
    var y = 0f
    layout.rows.forEachIndexed { r, keys ->
        val h = if (hasNumberRow && r == 0) numRowH else rowH
        val total = keys.fold(0f) { a, k -> a + k.width }
        val rowW = (total + gapUnits) * unit
        var x = (widthPx - rowW) / 2f
        var cum = 0f
        var gapDone = !split
        keys.forEach { k ->
            if (!gapDone && cum + k.width / 2f > total / 2f) {
                x += gapUnits * unit; gapDone = true
            }
            out.add(KeyBox(k, r, x, y, x + k.width * unit, y + h))
            x += k.width * unit
            cum += k.width
        }
        y += h
    }
    return out
}

data class LongPopup(val box: KeyBox, val options: List<String>, val selected: Int, val left: Float, val top: Float, val cellW: Float, val cellH: Float)

/** Barmoq harakatlarini boshqaradi: bosish, uzoq bosish, glayd, kursor, so'z o'chirish */
class TouchHandler(private val kb: KbState, private val scope: CoroutineScope) {
    var boxes: List<KeyBox> = emptyList()
    var widthPx = 0f
    var slop = 20f
    var stepPx = 26f

    val pressed = mutableStateListOf<KeyBox>()
    var preview by mutableStateOf<KeyBox?>(null)
    var popup by mutableStateOf<LongPopup?>(null)
    val trail = mutableStateListOf<Offset>()
    var deleteSwipe by mutableStateOf(false)

    private class P(val box: KeyBox, val downX: Float, val downY: Float) {
        var mode = 0 // 0 oddiy, 1 kursor, 2 so'z o'chirish, 3 glayd
        var longJob: Job? = null
        var repeatJob: Job? = null
        var popupShown = false
        var handled = false
        var lastX = downX
        val path = StringBuilder()
        var lastGlide: KeyBox? = null
    }

    private val pointers = HashMap<Long, P>()

    fun hit(pos: Offset): KeyBox? {
        if (boxes.isEmpty()) return null
        boxes.firstOrNull { pos.x >= it.left && pos.x < it.right && pos.y >= it.top && pos.y < it.bottom }?.let { return it }
        return boxes.minByOrNull { b ->
            val dx = maxOf(b.left - pos.x, 0f, pos.x - b.right)
            val dy = maxOf(b.top - pos.y, 0f, pos.y - b.bottom)
            dx * dx + dy * dy
        }
    }

    private fun longOptions(k: Key): List<String> {
        val opts = k.longPress
        return if (kb.shift > 0) opts.map { if (it.any { c -> c.isLetter() }) it.uppercase() else it } else opts
    }

    private fun glideAllowed(b: KeyBox): Boolean =
        kb.settings.glide && kb.mode == Mode.LETTERS && kb.internal == Internal.NONE && b.key.isLetter && pointers.size == 1

    fun down(id: Long, pos: Offset) {
        // tez yozishda: oldingi barmoq hali ko'tarilmagan bo'lsa, uning harfini darhol yozamiz
        pointers.values.forEach { o ->
            if (o.mode == 0 && !o.popupShown && !o.handled && o.box.key.type == KT.CHAR) {
                o.longJob?.cancel()
                kb.press(o.box.key)
                o.handled = true
                if (preview === o.box) preview = null
            }
        }
        val b = hit(pos) ?: return
        val p = P(b, pos.x, pos.y)
        pointers[id] = p
        kb.feedback(b.key.type)
        pressed.add(b)
        when (b.key.type) {
            KT.CHAR -> {
                if (kb.settings.showPreview) preview = b
                val opts = longOptions(b.key)
                if (opts.isNotEmpty()) {
                    p.longJob = scope.launch {
                        delay(330)
                        if (p.mode != 0) return@launch
                        val cellW = (b.width * 0.95f).coerceAtLeast(60f)
                        val total = cellW * opts.size
                        val left = (b.left).coerceAtMost(widthPx - total).coerceAtLeast(0f)
                        popup = LongPopup(b, opts, 0, left, b.top - b.height * 1.1f, cellW, b.height)
                        p.popupShown = true
                        preview = null
                    }
                }
            }
            KT.DEL -> {
                kb.delete()
                p.repeatJob = scope.launch {
                    delay(400)
                    while (true) { kb.delete(); delay(55) }
                }
            }
            KT.SPACE, KT.LANG -> {
                p.longJob = scope.launch {
                    delay(450)
                    if (p.mode == 0) { p.handled = true; kb.showImePicker() }
                }
            }
            KT.SHIFT -> { kb.toggleShift(); p.handled = true }
            else -> {}
        }
    }

    fun move(id: Long, pos: Offset) {
        val p = pointers[id] ?: return
        val dx = pos.x - p.downX
        val dy = pos.y - p.downY
        if (p.popupShown) {
            val pop = popup ?: return
            val idx = ((pos.x - pop.left) / pop.cellW).toInt().coerceIn(0, pop.options.size - 1)
            if (idx != pop.selected) popup = pop.copy(selected = idx)
            return
        }
        when (p.box.key.type) {
            KT.SPACE -> {
                if (p.mode == 0 && abs(dx) > slop) {
                    p.mode = 1; p.longJob?.cancel(); p.lastX = pos.x
                    kb.beginCursorDrag()
                }
                if (p.mode == 1) {
                    val steps = ((pos.x - p.lastX) / stepPx).toInt()
                    if (steps != 0) { kb.moveCursor(steps); p.lastX += steps * stepPx }
                }
            }
            KT.DEL -> if (p.mode == 0 && dx < -slop * 2) {
                p.mode = 2; p.repeatJob?.cancel(); deleteSwipe = true
            }
            KT.CHAR -> {
                if (p.mode == 0 && glideAllowed(p.box) && hypot(dx, dy) > p.box.width * 0.75f) {
                    p.mode = 3
                    p.longJob?.cancel()
                    preview = null
                    p.path.append(p.box.key.output.lowercase())
                    p.lastGlide = p.box
                    trail.clear()
                    trail.add(Offset(p.downX, p.downY))
                }
                if (p.mode == 3) {
                    if (kb.settings.glideTrail) {
                        trail.add(pos)
                        if (trail.size > 90) trail.removeAt(0)
                    }
                    val k = hit(pos)
                    if (k != null && k !== p.lastGlide && k.key.isLetter) {
                        p.path.append(k.key.output.lowercase())
                        p.lastGlide = k
                    }
                }
            }
            else -> {}
        }
    }

    fun up(id: Long) {
        val p = pointers.remove(id) ?: return
        p.longJob?.cancel()
        p.repeatJob?.cancel()
        pressed.remove(p.box)
        if (preview === p.box) preview = null
        when {
            p.handled -> {}
            p.popupShown -> {
                val pop = popup
                if (pop != null) kb.press(p.box.key, pop.options[pop.selected])
            }
            p.mode == 1 -> {}
            p.mode == 2 -> { deleteSwipe = false; kb.deleteWord() }
            p.mode == 3 -> {
                trail.clear()
                if (p.path.length >= 2) kb.onGlide(p.path.toString()) else kb.press(p.box.key)
            }
            p.box.key.type == KT.DEL -> {}
            else -> kb.press(p.box.key)
        }
        if (p.popupShown) popup = null
        if (pointers.isEmpty()) { trail.clear(); deleteSwipe = false; pressed.clear() }
    }
}

@Composable
fun KeyboardArea(kb: KbState, s: KbSettings, colors: KbColors) {
    val layout = currentLayout(kb, s)
    val oneHand = if (kb.mode == Mode.LETTERS || kb.mode == Mode.SYMBOLS) s.oneHand else 0
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (oneHand == 2) OneHandSide(kb, colors, toLeft = true)
        BoxWithConstraints(Modifier.weight(1f)) {
            val widthPx = constraints.maxWidth.toFloat()
            KeysView(kb, s, colors, layout, widthPx)
        }
        if (oneHand == 1) OneHandSide(kb, colors, toLeft = false)
    }
}

@Composable
private fun OneHandSide(kb: KbState, colors: KbColors, toLeft: Boolean) {
    Column(
        Modifier.width(56.dp).height(keyboardHeightDp(kb, kb.settings).dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SideButton(if (toLeft) "◀" else "▶", colors) { Store.update { it.copy(oneHand = if (toLeft) 1 else 2) } }
        SideButton("⤢", colors) { Store.update { it.copy(oneHand = 0) } }
    }
}

@Composable
private fun SideButton(t: String, colors: KbColors, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(colors.special).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, color = colors.text, fontSize = 18.sp) }
}

@Composable
fun KeysView(kb: KbState, s: KbSettings, colors: KbColors, layout: KbLayout, widthPx: Float) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val rowH = with(density) { s.keyHeight.dp.toPx() }
    val gap = with(density) { s.keyGap.dp.toPx() }
    val hasNum = kb.mode == Mode.LETTERS && s.numberRow
    val split = kb.landscape && s.splitLandscape && (kb.mode == Mode.LETTERS || kb.mode == Mode.SYMBOLS)
    val boxes = remember(layout, widthPx, rowH, split, hasNum) {
        computeBoxes(layout, widthPx, rowH, rowH * 0.8f, hasNum, split)
    }
    val touch = remember(kb) { TouchHandler(kb, scope) }
    touch.boxes = boxes
    touch.widthPx = widthPx
    touch.slop = with(density) { 10.dp.toPx() }
    touch.stepPx = with(density) { 11.dp.toPx() }
    val totalH = boxes.maxOfOrNull { it.bottom } ?: 0f

    Box(
        Modifier
            .fillMaxWidth()
            .height(with(density) { totalH.toDp() })
            .pointerInput(touch) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        ev.changes.forEach { c ->
                            when {
                                c.changedToDownIgnoreConsumed() -> touch.down(c.id.value, c.position)
                                c.changedToUpIgnoreConsumed() -> touch.up(c.id.value)
                                c.pressed -> touch.move(c.id.value, c.position)
                            }
                            c.consume()
                        }
                    }
                }
            }
    ) {
        boxes.forEach { b ->
            KeyCell(b, kb, s, colors, pressed = touch.pressed.any { it === b }, gap = gap, deleteSwipe = touch.deleteSwipe)
        }

        // Glayd izi
        if (touch.trail.size > 1) {
            val pts = touch.trail.toList()
            Canvas(Modifier.fillMaxWidth().height(with(density) { totalH.toDp() })) {
                val path = Path()
                path.moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
                drawPath(path, colors.accent.copy(alpha = 0.7f), style = Stroke(width = 9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        // Tugma ko'rinishi (preview)
        touch.preview?.let { b ->
            val w = b.width * 1.25f
            val h = b.height * 1.15f
            val left = (b.centerX - w / 2f).coerceIn(0f, (widthPx - w).coerceAtLeast(0f))
            val top = b.top - h * 0.95f
            Box(
                Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                    .shadow(6.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.popup),
                contentAlignment = Alignment.Center,
            ) {
                Text(keyLabel(b.key, kb, s), color = colors.text, fontSize = 28.sp)
            }
        }

        // Uzoq bosilgandagi qo'shimcha belgilar
        touch.popup?.let { pop ->
            Row(
                Modifier
                    .offset { IntOffset(pop.left.roundToInt(), pop.top.roundToInt()) }
                    .shadow(8.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.popup)
            ) {
                pop.options.forEachIndexed { i, o ->
                    Box(
                        Modifier
                            .size(with(density) { pop.cellW.toDp() }, with(density) { pop.cellH.toDp() })
                            .background(if (i == pop.selected) colors.accent else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(o, color = if (i == pop.selected) colors.onAccent else colors.text, fontSize = if (o.length > 2) 15.sp else 22.sp)
                    }
                }
            }
        }
    }
}

fun keyLabel(k: Key, kb: KbState, s: KbSettings): String = when (k.type) {
    KT.CHAR -> if (kb.shift > 0 && k.isLetter) k.label.uppercase() else k.label
    KT.SHIFT -> if (kb.shift == 2) "⇪" else "⇧"
    KT.ENTER -> kb.enterLabel
    KT.SPACE -> if (kb.mode == Mode.LETTERS) LayoutIds.name(s.currentLayout) else if (k.label == "␣") "␣" else LayoutIds.short(s.currentLayout)
    else -> k.label
}

@Composable
private fun KeyCell(b: KeyBox, kb: KbState, s: KbSettings, colors: KbColors, pressed: Boolean, gap: Float, deleteSwipe: Boolean) {
    val density = LocalDensity.current
    val k = b.key
    val special = k.type != KT.CHAR && k.type != KT.SPACE
    val bg = when {
        k.type == KT.ENTER -> colors.accent
        k.type == KT.DEL && deleteSwipe -> Color(0xFFE53935)
        pressed -> colors.pressed
        k.type == KT.SHIFT && kb.shift > 0 -> colors.accent.copy(alpha = 0.3f)
        special -> colors.special
        else -> colors.key
    }
    val fg = when {
        k.type == KT.ENTER -> colors.onAccent
        k.type == KT.SHIFT && kb.shift > 0 -> colors.accent
        k.type == KT.SPACE -> colors.sub
        else -> colors.text
    }
    val label = keyLabel(k, kb, s)
    val size = when {
        k.type == KT.SPACE -> 13.sp
        k.type == KT.SYMBOLS || k.type == KT.ABC || k.type == KT.SYM_PAGE -> 14.sp
        label.length > 2 -> 16.sp
        k.type == KT.CHAR -> 21.sp
        else -> 20.sp
    }
    val w = with(density) { (b.width - gap).coerceAtLeast(1f).toDp() }
    val h = with(density) { (b.height - gap).coerceAtLeast(1f).toDp() }
    Box(
        Modifier
            .offset { IntOffset((b.left + gap / 2f).roundToInt(), (b.top + gap / 2f).roundToInt()) }
            .size(w, h)
            .clip(RoundedCornerShape(s.keyRadius.dp))
            .background(bg)
            .then(if (s.keyBorders) Modifier.border(1.dp, colors.border, RoundedCornerShape(s.keyRadius.dp)) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = fg,
            fontSize = size,
            fontWeight = if (k.type == KT.CHAR) FontWeight.Normal else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        if (k.hint != null && k.type == KT.CHAR) {
            Text(
                k.hint,
                color = colors.sub,
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp),
            )
        } else if (k.longPress.isNotEmpty() && k.type == KT.CHAR && k.longPress.first() != k.label) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 3.dp)
                    .size(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.sub.copy(alpha = 0.6f))
            )
        }
    }
}
