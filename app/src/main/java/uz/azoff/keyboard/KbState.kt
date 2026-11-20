package uz.azoff.keyboard

import android.Manifest
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Mode { LETTERS, SYMBOLS, NUMPAD, PHONE }
enum class Panel { NONE, EMOJI, CLIPBOARD, TEMPLATES, TRANSLATE, GIF, CALC, TOOLS, VOICE }
enum class Internal { NONE, EMOJI_SEARCH, GIF_SEARCH, TRANSLATE }

/** Klaviaturaning butun holati va matn kiritish mantig'i */
class KbState(val ime: AzoffIme) {
    val ctx: Context get() = ime
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var mode by mutableStateOf(Mode.LETTERS)
    var symPage by mutableIntStateOf(0)
    var shift by mutableIntStateOf(0) // 0 o'chiq, 1 bir marta, 2 CAPS
    var panel by mutableStateOf(Panel.NONE)
    var internal by mutableStateOf(Internal.NONE)
    var internalText by mutableStateOf("")
    var suggestions by mutableStateOf<List<String>>(emptyList())
    var highlight by mutableIntStateOf(-1)
    var tip by mutableStateOf<String?>(null)
    var incognito by mutableStateOf(false)
    var incognitoManual by mutableStateOf(false)
    var enterLabel by mutableStateOf("⏎")
    var message by mutableStateOf<String?>(null)
    var landscape by mutableStateOf(false)
    var composingVisible by mutableStateOf("")

    // Ovoz
    var voiceListening by mutableStateOf(false)
    var voiceText by mutableStateOf("")
    private var recognizer: SpeechRecognizer? = null

    // Tarjima
    var translateResult by mutableStateOf<String?>(null)
    var translateBusy by mutableStateOf(false)
    var translateWholeField by mutableStateOf(false)

    // GIF
    var gifResults by mutableStateOf<List<Net.Gif>>(emptyList())
    var gifBusy by mutableStateOf(false)

    // Kalkulyator
    var calcExpr by mutableStateOf("")

    private val composing = StringBuilder()
    private var editorInfo: EditorInfo = EditorInfo()
    private var lastWord = ""
    private var lastSpaceTime = 0L
    private var lastShiftTime = 0L
    private var undoCorrection: Pair<String, String>? = null
    private var password = false
    private var urlOrEmail = false
    private var noSuggest = false
    private var selStart = -1
    private var selEnd = -1
    private var glideActive = false

    val settings: KbSettings get() = Store.settings.value
    val layoutId: String get() = settings.currentLayout
    private val lang: String get() = Dict.langOf(layoutId)
    private fun engine(): WordEngine? = Dict.get(ctx, lang)
    private fun ic(): InputConnection? = ime.currentInputConnection

    private val useComposing: Boolean
        get() = settings.suggestions && !password && !noSuggest && mode == Mode.LETTERS

    // ======================= Hayot sikli =======================

    fun onStart(info: EditorInfo, restarting: Boolean) {
        editorInfo = info
        landscape = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        password = (cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        urlOrEmail = cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_URI ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
        noSuggest = (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0 || urlOrEmail
        val noLearn = (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        incognito = incognitoManual || (settings.incognitoAuto && (password || noLearn))

        if (!restarting || mode == Mode.NUMPAD || mode == Mode.PHONE) {
            mode = when (cls) {
                InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_DATETIME -> Mode.NUMPAD
                InputType.TYPE_CLASS_PHONE -> Mode.PHONE
                else -> Mode.LETTERS
            }
        }
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        enterLabel = if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) "⏎" else when (action) {
            EditorInfo.IME_ACTION_GO -> "→"
            EditorInfo.IME_ACTION_SEARCH -> "🔍"
            EditorInfo.IME_ACTION_SEND -> "➤"
            EditorInfo.IME_ACTION_NEXT -> "⇥"
            EditorInfo.IME_ACTION_DONE -> "✓"
            else -> "⏎"
        }
        composing.clear(); composingVisible = ""
        lastWord = ""
        undoCorrection = null
        selStart = info.initialSelStart; selEnd = info.initialSelEnd
        if (!restarting) {
            panel = Panel.NONE; internal = Internal.NONE; internalText = ""
        }
        Clip.expire(settings.clipboardClearHours)
        Sounds.prepare(ctx, settings.sound)
        engine()
        autoShift()
        updateSuggestions()
    }

    fun onFinish() {
        composing.clear(); composingVisible = ""
        stopVoice()
        Dict.saveAll(ctx)
        Stats.persist()
    }

    fun destroy() {
        stopVoice()
        recognizer?.destroy(); recognizer = null
        scope.cancel()
    }

    fun onSelection(newStart: Int, newEnd: Int, candStart: Int, candEnd: Int) {
        selStart = newStart; selEnd = newEnd
        if (composing.isNotEmpty() && (candStart == -1 || newStart != candEnd || newEnd != candEnd)) {
            composing.clear(); composingVisible = ""
            ic()?.finishComposingText()
            lastWord = ""
            glideActive = false
            updateSuggestions()
        }
        if (composing.isEmpty() && newStart == newEnd) autoShift()
    }

    // ======================= Fikr-mulohaza =======================

    fun feedback(type: KT) {
        val s = settings
        if (s.vibrate) ime.vibrate(s.vibrateMs)
        Sounds.play(ctx, s.sound, s.volume, type)
    }

    fun toast(msg: String) {
        message = msg
        scope.launch { delay(2500); if (message == msg) message = null }
    }

    // ======================= Tugmalar =======================

    fun press(key: Key, alt: String? = null) {
        when (key.type) {
            KT.CHAR -> typeText(alt ?: key.output, fromLetterKey = key.isLetter && alt == null)
            KT.SHIFT -> toggleShift()
            KT.DEL -> delete()
            KT.ENTER -> enter()
            KT.SPACE -> space()
            KT.SYMBOLS -> { mode = Mode.SYMBOLS; symPage = 0 }
            KT.ABC -> { mode = Mode.LETTERS; autoShift() }
            KT.SYM_PAGE -> symPage = 1 - symPage
            KT.LANG -> nextLayout()
        }
    }

    private fun applyShift(t: String): String = if (shift > 0) t.uppercase() else t

    private fun isWordChar(c: Char) = c.isLetter() || c == Translit.OK || c == Translit.TT

    fun typeText(raw: String, fromLetterKey: Boolean = false) {
        var t = if (raw.any { it.isLetter() }) applyShift(raw) else raw
        if (internal != Internal.NONE) {
            internalText += t
            if (shift == 1) shift = 0
            return
        }
        val ic = ic() ?: return
        val s = settings
        undoCorrection = null

        // Tutuq belgisini to'g'rilash va yangi harflarga almashtirish
        if (t.length == 1 && Translit.isApostrophe(t[0]) && (layoutId == LayoutIds.UZ_LAT || layoutId == LayoutIds.UZ_NEW)) {
            val prev = composing.lastOrNull() ?: ic.getTextBeforeCursor(1, 0)?.lastOrNull()
            val og = prev != null && (prev.lowercaseChar() == 'o' || prev.lowercaseChar() == 'g')
            if (layoutId == LayoutIds.UZ_NEW && s.autoNewLetters && og && prev != null) {
                val repl = if (prev.lowercaseChar() == 'o') "ö" else "ğ"
                replaceLastChar(if (prev.isUpperCase()) repl.uppercase() else repl)
                if (shift == 1) shift = 0
                afterChar()
                return
            }
            if (s.fixApostrophe) t = if (og && layoutId == LayoutIds.UZ_LAT) Translit.OK.toString() else Translit.TT.toString()
        }

        val wordish = t.all { isWordChar(it) } || (t.length == 1 && t[0].isDigit() && (composing.isEmpty() || composing.all { it.isDigit() }))
        if (wordish && useComposing) {
            if (glideActive) {
                // glayddan keyin yangi harf — oldingi so'zni yakunlaymiz
                commitComposing(false)
                ic.commitText(" ", 1)
            }
            composing.append(t)
            if (layoutId == LayoutIds.UZ_NEW && s.autoNewLetters && composing.length >= 2) {
                val a = composing[composing.length - 2]
                val b = composing[composing.length - 1]
                if (b.lowercaseChar() == 'h' && (a.lowercaseChar() == 's' || a.lowercaseChar() == 'c')) {
                    val r = if (a.lowercaseChar() == 's') "ş" else "ç"
                    composing.setLength(composing.length - 2)
                    composing.append(if (a.isUpperCase()) r.uppercase() else r)
                }
            }
            ic.setComposingText(composing, 1)
            composingVisible = composing.toString()
        } else if (wordish && !useComposing && layoutId == LayoutIds.UZ_NEW && s.autoNewLetters && t.equals("h", true) && !password) {
            val prev = ic.getTextBeforeCursor(1, 0)?.lastOrNull()
            if (prev != null && (prev.lowercaseChar() == 's' || prev.lowercaseChar() == 'c')) {
                val r = if (prev.lowercaseChar() == 's') "ş" else "ç"
                ic.deleteSurroundingText(1, 0)
                ic.commitText(if (prev.isUpperCase()) r.uppercase() else r, 1)
            } else ic.commitText(t, 1)
        } else {
            val hadWord = composing.isNotEmpty()
            commitComposing(false)
            // "so'z ." → "so'z. " (avtomatik qo'yilgan bo'sh joyni olib tashlash)
            if (t.length == 1 && t[0] in ".,!?;:" && !hadWord && mode == Mode.LETTERS) {
                val before = ic.getTextBeforeCursor(2, 0)
                if (before != null && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit() && lastSpaceTime > 0) {
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText("$t ", 1)
                    lastSpaceTime = 0
                    if (t[0] in ".!?") lastWord = ""
                    afterChar(); autoShift(); updateSuggestions()
                    return
                }
            }
            ic.commitText(t, 1)
            if (t.any { it in ".!?\n" }) lastWord = ""
        }
        if (shift == 1) shift = 0
        lastSpaceTime = 0
        afterChar()
        if (!wordish) autoShift()
        updateSuggestions()
    }

    private fun replaceLastChar(r: String) {
        val ic = ic() ?: return
        if (composing.isNotEmpty()) {
            composing.setLength(composing.length - 1)
            composing.append(r)
            ic.setComposingText(composing, 1)
            composingVisible = composing.toString()
            updateSuggestions()
        } else {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(r, 1)
        }
    }

    private fun afterChar() {
        if (!incognito) Stats.onChar()
    }

    /** Yozilayotgan so'zni yakunlaydi. fromSpace bo'lsa shablon/avtotuzatish qo'llanadi. */
    private fun commitComposing(fromSpace: Boolean): String {
        if (composing.isEmpty()) return ""
        val ic = ic()
        val word = composing.toString()
        var final = word
        val s = settings
        if (fromSpace && !glideActive) {
            val tpl = s.templates.firstOrNull { it.shortcut.isNotBlank() && it.shortcut.equals(word, ignoreCase = true) }
            if (tpl != null && s.templateAutoExpand) {
                final = tpl.text
            } else if (s.autocorrect && !urlOrEmail) {
                engine()?.correct(word)?.let { final = it }
            }
        }
        ic?.commitText(final, 1)
        if (final != word) undoCorrection = word to final
        learn(final)
        composing.clear(); composingVisible = ""
        glideActive = false
        return final
    }

    private fun learn(word: String) {
        if (incognito || word.isBlank()) return
        Stats.onWord()
        if (word.contains(' ')) return
        val e = engine() ?: return
        e.learn(word)
        if (settings.nextWord && lastWord.isNotEmpty()) e.learnBigram(lastWord, word)
    }

    fun space() {
        if (internal != Internal.NONE) { internalText += " "; return }
        val ic = ic() ?: return
        val now = SystemClock.uptimeMillis()
        if (composing.isNotEmpty()) {
            val w = commitComposing(true)
            ic.commitText(" ", 1)
            lastWord = w.substringAfterLast(' ')
        } else if (settings.doubleSpacePeriod && now - lastSpaceTime < 600 && mode == Mode.LETTERS) {
            val before = ic.getTextBeforeCursor(2, 0)
            if (before != null && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                lastSpaceTime = 0
                lastWord = ""
                afterChar(); autoShift(); updateSuggestions()
                return
            }
            ic.commitText(" ", 1)
        } else {
            undoCorrection = null
            ic.commitText(" ", 1)
        }
        lastSpaceTime = now
        afterChar()
        autoShift()
        updateSuggestions()
    }

    fun delete() {
        if (internal != Internal.NONE) {
            internalText = dropLastCodePoint(internalText)
            return
        }
        val ic = ic() ?: return
        val undo = undoCorrection
        undoCorrection = null
        if (undo != null && composing.isEmpty()) {
            val before = ic.getTextBeforeCursor(undo.second.length + 1, 0)?.toString()
            if (before == undo.second + " ") {
                ic.deleteSurroundingText(undo.second.length + 1, 0)
                ic.commitText(undo.first, 1)
                updateSuggestions()
                return
            }
        }
        lastSpaceTime = 0
        if (composing.isNotEmpty()) {
            val s = dropLastCodePoint(composing.toString())
            composing.clear(); composing.append(s)
            if (composing.isEmpty()) ic.commitText("", 1) else ic.setComposingText(composing, 1)
            composingVisible = composing.toString()
            glideActive = false
        } else {
            val sel = ic.getSelectedText(0)
            if (!sel.isNullOrEmpty()) ic.commitText("", 1)
            else ime.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            if (useComposing) restartComposing()
        }
        autoShift()
        updateSuggestions()
    }

    /** O'chirgandan keyin kursor oldidagi so'zni qayta "tahrirlanadigan" qiladi */
    private fun restartComposing() {
        val ic = ic() ?: return
        if (selStart != selEnd) return
        val before = ic.getTextBeforeCursor(40, 0)?.toString() ?: return
        val after = ic.getTextAfterCursor(1, 0)?.toString() ?: ""
        if (after.isNotEmpty() && isWordChar(after[0])) return
        val word = before.takeLastWhile { isWordChar(it) }
        if (word.isEmpty() || word.length >= 40) return
        // indekslarsiz: so'zni olib, qayta "composing" sifatida qo'yamiz
        ic.beginBatchEdit()
        ic.deleteSurroundingText(word.length, 0)
        ic.setComposingText(word, 1)
        ic.endBatchEdit()
        composing.clear(); composing.append(word)
        composingVisible = word
    }

    fun deleteWord() {
        if (internal != Internal.NONE) {
            internalText = internalText.trimEnd().dropLastWhile { !it.isWhitespace() }
            return
        }
        val ic = ic() ?: return
        if (composing.isNotEmpty()) {
            composing.clear(); composingVisible = ""
            ic.commitText("", 1)
        } else {
            val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: return
            val trimmed = before.trimEnd()
            var n = before.length - trimmed.length
            val word = trimmed.takeLastWhile { isWordChar(it) || it.isDigit() }
            n += if (word.isNotEmpty()) word.length else if (trimmed.isNotEmpty()) 1 else 0
            if (n > 0) ic.deleteSurroundingText(n, 0)
        }
        autoShift(); updateSuggestions()
    }

    private fun dropLastCodePoint(s: String): String {
        if (s.isEmpty()) return s
        val cut = if (s.length >= 2 && Character.isSurrogatePair(s[s.length - 2], s[s.length - 1])) 2 else 1
        return s.substring(0, s.length - cut)
    }

    fun enter() {
        when (internal) {
            Internal.TRANSLATE -> { translate(); return }
            Internal.GIF_SEARCH -> { searchGifs(); return }
            Internal.EMOJI_SEARCH -> return
            Internal.NONE -> {}
        }
        val ic = ic() ?: return
        commitComposing(false)
        val info = editorInfo
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0 &&
            action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            ic.performEditorAction(action)
        } else {
            ime.sendKeyChar('\n')
        }
        lastWord = ""
        lastSpaceTime = 0
        autoShift(); updateSuggestions()
    }

    fun toggleShift() {
        val now = SystemClock.uptimeMillis()
        shift = when (shift) {
            0 -> if (now - lastShiftTime < 350) 2 else 1
            1 -> if (now - lastShiftTime < 350) 2 else 0
            else -> 0
        }
        lastShiftTime = now
    }

    private fun autoShift() {
        if (mode != Mode.LETTERS || shift == 2 || internal != Internal.NONE) return
        if (!settings.autoCaps || urlOrEmail || password) { if (shift == 1) shift = 0; return }
        val ic = ic() ?: return
        val caps = try { ic.getCursorCapsMode(editorInfo.inputType) } catch (e: Exception) { 0 }
        shift = if (caps != 0) 1 else 0
    }

    fun nextLayout() {
        commitComposing(false)
        val list = settings.layouts
        val idx = list.indexOf(layoutId)
        val next = list[(idx + 1) % list.size]
        Store.update { it.copy(currentLayout = next) }
        mode = Mode.LETTERS
        Dict.get(ctx, Dict.langOf(next))
        autoShift(); updateSuggestions()
    }

    fun setLayout(id: String) {
        commitComposing(false)
        Store.update { it.copy(currentLayout = id) }
        mode = Mode.LETTERS
        autoShift(); updateSuggestions()
    }

    fun showImePicker() {
        (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    fun moveCursor(steps: Int) {
        if (steps == 0) return
        if (composing.isNotEmpty()) commitComposing(false)
        val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(kotlin.math.abs(steps)) { ime.sendDownUpKeyEvents(code) }
    }

    fun beginCursorDrag() {
        if (composing.isNotEmpty()) commitComposing(false)
        updateSuggestions()
    }

    // ======================= Takliflar =======================

    fun updateSuggestions() {
        val s = settings
        tip = null
        highlight = -1
        if (!s.suggestions || password || internal != Internal.NONE || mode != Mode.LETTERS) {
            suggestions = emptyList(); return
        }
        val w = composing.toString()
        val e = engine()
        val list = ArrayList<String>()
        if (w.isNotEmpty()) {
            s.templates.filter { it.shortcut.isNotBlank() && it.shortcut.equals(w, true) }.forEach { list.add(it.text) }
            if (glideActive) {
                list.addAll(glideCandidates.filter { it != w })
            } else if (w.all { it.isDigit() }) {
                if (s.numberFormat && w.length >= 4) {
                    list.add(Calc.groupDigits(w)); list.add(Calc.groupDigits(w) + " soʻm")
                }
            } else if (e != null) {
                val corr = if (!e.contains(w)) e.correct(w) else null
                if (corr != null) { list.add(corr); if (s.autocorrect) highlight = 0 }
                e.suggest(w, 4).forEach { if (it != w && it != corr) list.add(it) }
            }
            if (layoutId == LayoutIds.UZ_NEW && s.alphabetTips) {
                val lw = w.lowercase()
                tip = when {
                    lw.contains("o'") || lw.contains("o${Translit.OK}") -> "oʻ → ö"
                    lw.contains("g'") || lw.contains("g${Translit.OK}") -> "gʻ → ğ"
                    else -> null
                }
            }
        } else if (s.nextWord && lastWord.isNotEmpty() && e != null) {
            list.addAll(e.next(lastWord))
        }
        suggestions = list.distinct().take(5)
    }

    fun pickSuggestion(text: String) {
        feedback(KT.CHAR)
        val ic = ic() ?: return
        val isTemplate = settings.templates.any { it.text == text }
        if (composing.isNotEmpty()) {
            ic.commitText(text, 1)
            if (!isTemplate) learn(text.replace(" soʻm", ""))
            composing.clear(); composingVisible = ""
        } else {
            ic.commitText(text, 1)
            if (!isTemplate) learn(text)
        }
        glideActive = false
        ic.commitText(" ", 1)
        lastWord = text.substringAfterLast(' ')
        lastSpaceTime = SystemClock.uptimeMillis()
        autoShift(); updateSuggestions()
    }

    // ======================= Glayd yozish =======================

    private var glideCandidates: List<String> = emptyList()

    fun onGlide(path: String) {
        val e = engine() ?: return
        val cands = e.glide(path, 5)
        if (cands.isEmpty()) {
            // so'z topilmasa, birinchi harfni yozamiz
            return
        }
        val ic = ic() ?: return
        commitComposing(false)
        val before = ic.getTextBeforeCursor(1, 0)
        if (!before.isNullOrEmpty() && !before[0].isWhitespace() && before[0] !in "([\"'«") ic.commitText(" ", 1)
        val best = if (shift > 0) cands[0].replaceFirstChar { it.uppercaseChar() } else cands[0]
        if (shift == 1) shift = 0
        composing.clear(); composing.append(best)
        ic.setComposingText(composing, 1)
        composingVisible = best
        glideCandidates = cands.drop(1)
        glideActive = true
        updateSuggestions()
    }

    // ======================= Panellar =======================

    fun openPanel(p: Panel) {
        commitComposing(false)
        if (panel == p) { closePanel(); return }
        panel = p
        internal = Internal.NONE
        internalText = ""
        when (p) {
            Panel.TRANSLATE -> { internal = Internal.TRANSLATE; translateResult = null; translateWholeField = false }
            Panel.GIF -> { internal = Internal.GIF_SEARCH; if (gifResults.isEmpty()) searchGifs() }
            Panel.VOICE -> startVoice()
            else -> {}
        }
        updateSuggestions()
    }

    fun closePanel() {
        if (panel == Panel.VOICE) stopVoice()
        panel = Panel.NONE
        internal = Internal.NONE
        internalText = ""
        mode = if (mode == Mode.NUMPAD || mode == Mode.PHONE) mode else Mode.LETTERS
        autoShift(); updateSuggestions()
    }

    fun commitRaw(text: String) {
        val ic = ic() ?: return
        commitComposing(false)
        ic.commitText(text, 1)
        lastSpaceTime = 0
        updateSuggestions()
    }

    fun insertEmoji(e: String) {
        feedback(KT.CHAR)
        if (!incognito) Recents.add(e)
        commitRaw(e)
    }

    fun openSettings(extra: String? = null) {
        val i = Intent(ctx, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (extra != null) i.putExtra("open", extra)
        ctx.startActivity(i)
    }

    fun toggleIncognito() {
        incognitoManual = !incognitoManual
        incognito = incognitoManual || (settings.incognitoAuto && password)
        toast(if (incognito) "Inkognito yoqildi: hech narsa eslab qolinmaydi" else "Inkognito oʻchirildi")
    }

    // ----- Matn vositalari -----

    fun transformField(f: (String) -> String) {
        val ic = ic() ?: return
        commitComposing(false)
        val sel = ic.getSelectedText(0)
        if (!sel.isNullOrEmpty()) {
            ic.commitText(f(sel.toString()), 1)
            return
        }
        val et = ic.getExtractedText(ExtractedTextRequest(), 0)
        val text = et?.text?.toString()
        if (text.isNullOrEmpty()) { toast("Maydonda matn yoʻq"); return }
        ic.beginBatchEdit()
        ic.setSelection(0, text.length)
        ic.commitText(f(text), 1)
        ic.endBatchEdit()
        toast("Tayyor ✓")
    }

    fun contextAction(id: Int) {
        commitComposing(false)
        ic()?.performContextMenuAction(id)
    }

    fun undo() {
        val ic = ic() ?: return
        commitComposing(false)
        val t = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON))
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON))
    }

    // ----- Kalkulyator -----

    fun calcInput(k: String) {
        feedback(KT.CHAR)
        calcExpr = when (k) {
            "C" -> ""
            "⌫" -> calcExpr.dropLast(1)
            "=" -> Calc.eval(calcExpr) ?: calcExpr
            else -> calcExpr + k
        }
    }

    // ----- Tarjima -----

    fun translate() {
        val s = settings
        if (!s.internet) { toast("Internet funksiyalari sozlamalarda oʻchirilgan"); return }
        val src = if (translateWholeField) {
            ic()?.getExtractedText(ExtractedTextRequest(), 0)?.text?.toString() ?: ""
        } else internalText
        if (src.isBlank()) { toast("Tarjima uchun matn yozing"); return }
        translateBusy = true
        scope.launch {
            val r = Net.translate(src, s.translateFrom, s.translateTo)
            translateBusy = false
            if (r == null) toast("Tarjima qilib boʻlmadi. Internetni tekshiring.")
            translateResult = r?.let { if (s.translateTo == "uz" && layoutId == LayoutIds.UZ_NEW) Translit.latToNew(it) else it }
        }
    }

    fun insertTranslation() {
        val r = translateResult ?: return
        val ic = ic() ?: return
        if (translateWholeField) {
            val text = ic.getExtractedText(ExtractedTextRequest(), 0)?.text?.toString() ?: ""
            ic.beginBatchEdit(); ic.setSelection(0, text.length); ic.commitText(r, 1); ic.endBatchEdit()
        } else {
            ic.commitText(r, 1)
        }
        internalText = ""
        translateResult = null
        closePanel()
    }

    // ----- GIF -----

    fun searchGifs() {
        val s = settings
        if (!s.internet) { toast("Internet funksiyalari sozlamalarda oʻchirilgan"); return }
        if (s.giphyKey.isBlank()) { gifResults = emptyList(); return }
        gifBusy = true
        val q = internalText
        scope.launch {
            val r = Net.gifs(s.giphyKey, q)
            gifBusy = false
            if (r == null) toast("GIF yuklab boʻlmadi") else gifResults = r
        }
    }

    fun sendGif(g: Net.Gif) {
        val ic = ic() ?: return
        val info = editorInfo
        val mimes = EditorInfoCompat.getContentMimeTypes(info)
        val supported = mimes.any { ClipDescription.compareMimeTypes("image/gif", it) }
        if (!supported) {
            ic.commitText(g.page.ifBlank { g.full }, 1)
            toast("Bu ilova GIF qabul qilmaydi — havola qoʻyildi")
            return
        }
        scope.launch {
            val f = Net.download(ctx, g.full, g.id.ifBlank { System.currentTimeMillis().toString() })
            if (f == null) { toast("GIF yuklab boʻlmadi"); return@launch }
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            val content = InputContentInfoCompat(uri, ClipDescription("GIF", arrayOf("image/gif")), Uri.parse(g.page))
            val flags = if (Build.VERSION.SDK_INT >= 25) InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION else 0
            val ok = ime.currentInputConnection?.let { InputConnectionCompat.commitContent(it, info, content, flags, null) } ?: false
            if (!ok) ime.currentInputConnection?.commitText(g.page, 1)
        }
    }

    // ----- Ovozli yozish -----

    fun startVoice() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            panel = Panel.NONE
            toast("Mikrofonga ruxsat bering")
            openSettings("mic")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            panel = Panel.NONE
            switchToVoiceIme()
            return
        }
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(ctx).also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { voiceText = "Gapiring…" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { voiceListening = false }
            override fun onError(error: Int) {
                voiceListening = false
                voiceText = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tushunilmadi. Qayta urinib koʻring."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Internet bilan muammo"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofonga ruxsat yoʻq"
                    else -> "Xatolik ($error)"
                }
            }
            override fun onResults(results: Bundle?) {
                voiceListening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    val out = when (layoutId) {
                        LayoutIds.UZ_NEW -> if (settings.voiceLang.startsWith("uz")) Translit.latToNew(text) else text
                        LayoutIds.UZ_CYR -> if (settings.voiceLang.startsWith("uz") && !Translit.isMostlyCyrillic(text)) Translit.latToCyr(text) else text
                        else -> text
                    }
                    commitRaw("$out ")
                    voiceText = out
                } else voiceText = "Hech narsa eshitilmadi"
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { voiceText = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, settings.voiceLang)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        try {
            r.startListening(intent)
            voiceListening = true
            voiceText = "Tayyorlanmoqda…"
        } catch (e: Exception) {
            voiceText = "Ovozli kiritishni boshlab boʻlmadi"
        }
    }

    fun stopVoice() {
        if (voiceListening) runCatching { recognizer?.stopListening() }
        voiceListening = false
    }

    private fun switchToVoiceIme() {
        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        for (imi in imm.enabledInputMethodList) {
            for (st in imm.getEnabledInputMethodSubtypeList(imi, true)) {
                if (st.mode == "voice" && Build.VERSION.SDK_INT >= 28) {
                    ime.switchInputMethod(imi.id, st)
                    return
                }
            }
        }
        toast("Ovozli kiritish xizmati topilmadi (Google ilovasini oʻrnating)")
    }
}
