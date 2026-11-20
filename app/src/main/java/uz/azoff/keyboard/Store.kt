package uz.azoff.keyboard

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Klaviatura tartiblari */
object LayoutIds {
    const val UZ_NEW = "uz_new"   // yangi alifbo: ö ğ ş ç
    const val UZ_LAT = "uz_lat"   // joriy lotin: oʻ gʻ
    const val UZ_CYR = "uz_cyr"   // o'zbek kirill
    const val RU = "ru"
    const val EN = "en"
    val all = listOf(UZ_NEW, UZ_LAT, UZ_CYR, RU, EN)
    fun name(id: String) = when (id) {
        UZ_NEW -> "Oʻzbek (yangi alifbo)"
        UZ_LAT -> "Oʻzbek (lotin)"
        UZ_CYR -> "Ўзбек (кирилл)"
        RU -> "Русский"
        EN -> "English"
        else -> id
    }
    fun short(id: String) = when (id) {
        UZ_NEW -> "UZ"
        UZ_LAT -> "UZ'"
        UZ_CYR -> "ЎЗ"
        RU -> "RU"
        EN -> "EN"
        else -> id
    }
}

data class Template(val shortcut: String, val text: String)

data class KbSettings(
    val layouts: List<String> = listOf(LayoutIds.UZ_NEW, LayoutIds.UZ_LAT, LayoutIds.UZ_CYR, LayoutIds.RU, LayoutIds.EN),
    val currentLayout: String = LayoutIds.UZ_NEW,
    val numberRow: Boolean = true,
    // Yozish
    val suggestions: Boolean = true,
    val autocorrect: Boolean = false,
    val autoCaps: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val autoNewLetters: Boolean = true,    // sh→ş, ch→ç, o'→ö, g'→ğ (yangi alifboda)
    val fixApostrophe: Boolean = true,     // ' → ʻ yoki ʼ
    val glide: Boolean = true,
    val glideTrail: Boolean = true,
    val nextWord: Boolean = true,
    val numberFormat: Boolean = true,
    val alphabetTips: Boolean = true,
    val templateAutoExpand: Boolean = true,
    // Ko'rinish
    val theme: Int = 0,                    // 0 tizim, 1 yorug', 2 qorong'i
    val dynamicColor: Boolean = true,
    val accent: Int = 0,
    val keyRadius: Int = 8,
    val keyHeight: Int = 52,
    val keyGap: Int = 4,
    val keyBorders: Boolean = false,
    val bgImage: Boolean = false,
    val bgDim: Float = 0.35f,
    val splitLandscape: Boolean = true,
    val oneHand: Int = 0,                  // 0 o'chiq, 1 chap, 2 o'ng
    val showPreview: Boolean = true,
    // Ovoz va tebranish
    val sound: Int = 0,                    // 0 o'chiq, 1 tizim, 2 mexanik, 3 yumshoq, 4 yozuv mashinkasi
    val volume: Float = 0.5f,
    val vibrate: Boolean = true,
    val vibrateMs: Int = 15,
    // Bufer
    val clipboardHistory: Boolean = true,
    val clipboardClearHours: Int = 0,      // 0 = hech qachon
    // Maxfiylik va internet
    val incognitoAuto: Boolean = true,
    val internet: Boolean = true,
    val giphyKey: String = "",
    val voiceLang: String = "uz-UZ",
    val translateFrom: String = "uz",
    val translateTo: String = "en",
    // Shablonlar
    val templates: List<Template> = listOf(
        Template("asl", "Assalomu alaykum!"),
        Template("rhm", "Katta rahmat!"),
        Template("tbr", "Tugʻilgan kuningiz bilan tabriklayman! 🎉"),
    ),
    val tipsShown: Int = 0,
)

object Store {
    private lateinit var prefs: SharedPreferences
    val settings = MutableStateFlow(KbSettings())
    private var inited = false

    fun init(ctx: Context) {
        if (inited) return
        inited = true
        prefs = ctx.applicationContext.getSharedPreferences("azoff_kb", Context.MODE_PRIVATE)
        settings.value = prefs.getString("settings", null)?.let { runCatching { fromJson(it) }.getOrNull() } ?: KbSettings()
        Clip.load(prefs)
        Stats.load(prefs)
        Recents.load(prefs)
    }

    fun update(t: (KbSettings) -> KbSettings) {
        val n = t(settings.value)
        if (n == settings.value) return
        settings.value = n
        prefs.edit().putString("settings", toJson(n)).apply()
    }

    internal fun save(key: String, value: String) {
        if (::prefs.isInitialized) prefs.edit().putString(key, value).apply()
    }

    private fun toJson(s: KbSettings): String {
        val o = JSONObject()
        o.put("layouts", JSONArray(s.layouts))
        o.put("currentLayout", s.currentLayout)
        o.put("numberRow", s.numberRow)
        o.put("suggestions", s.suggestions)
        o.put("autocorrect", s.autocorrect)
        o.put("autoCaps", s.autoCaps)
        o.put("doubleSpacePeriod", s.doubleSpacePeriod)
        o.put("autoNewLetters", s.autoNewLetters)
        o.put("fixApostrophe", s.fixApostrophe)
        o.put("glide", s.glide)
        o.put("glideTrail", s.glideTrail)
        o.put("nextWord", s.nextWord)
        o.put("numberFormat", s.numberFormat)
        o.put("alphabetTips", s.alphabetTips)
        o.put("templateAutoExpand", s.templateAutoExpand)
        o.put("theme", s.theme)
        o.put("dynamicColor", s.dynamicColor)
        o.put("accent", s.accent)
        o.put("keyRadius", s.keyRadius)
        o.put("keyHeight", s.keyHeight)
        o.put("keyGap", s.keyGap)
        o.put("keyBorders", s.keyBorders)
        o.put("bgImage", s.bgImage)
        o.put("bgDim", s.bgDim.toDouble())
        o.put("splitLandscape", s.splitLandscape)
        o.put("oneHand", s.oneHand)
        o.put("showPreview", s.showPreview)
        o.put("sound", s.sound)
        o.put("volume", s.volume.toDouble())
        o.put("vibrate", s.vibrate)
        o.put("vibrateMs", s.vibrateMs)
        o.put("clipboardHistory", s.clipboardHistory)
        o.put("clipboardClearHours", s.clipboardClearHours)
        o.put("incognitoAuto", s.incognitoAuto)
        o.put("internet", s.internet)
        o.put("giphyKey", s.giphyKey)
        o.put("voiceLang", s.voiceLang)
        o.put("translateFrom", s.translateFrom)
        o.put("translateTo", s.translateTo)
        o.put("templates", JSONArray().also { a ->
            s.templates.forEach { a.put(JSONObject().put("s", it.shortcut).put("t", it.text)) }
        })
        o.put("tipsShown", s.tipsShown)
        return o.toString()
    }

    private fun fromJson(str: String): KbSettings {
        val o = JSONObject(str)
        val d = KbSettings()
        val layouts = o.optJSONArray("layouts")?.let { a -> (0 until a.length()).map { a.getString(it) } }
            ?.filter { it in LayoutIds.all }?.ifEmpty { null } ?: d.layouts
        val templates = o.optJSONArray("templates")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val t = a.optJSONObject(i) ?: return@mapNotNull null
                Template(t.optString("s"), t.optString("t"))
            }
        } ?: d.templates
        return KbSettings(
            layouts = layouts,
            currentLayout = o.optString("currentLayout", d.currentLayout).let { if (it in layouts) it else layouts.first() },
            numberRow = o.optBoolean("numberRow", d.numberRow),
            suggestions = o.optBoolean("suggestions", d.suggestions),
            autocorrect = o.optBoolean("autocorrect", d.autocorrect),
            autoCaps = o.optBoolean("autoCaps", d.autoCaps),
            doubleSpacePeriod = o.optBoolean("doubleSpacePeriod", d.doubleSpacePeriod),
            autoNewLetters = o.optBoolean("autoNewLetters", d.autoNewLetters),
            fixApostrophe = o.optBoolean("fixApostrophe", d.fixApostrophe),
            glide = o.optBoolean("glide", d.glide),
            glideTrail = o.optBoolean("glideTrail", d.glideTrail),
            nextWord = o.optBoolean("nextWord", d.nextWord),
            numberFormat = o.optBoolean("numberFormat", d.numberFormat),
            alphabetTips = o.optBoolean("alphabetTips", d.alphabetTips),
            templateAutoExpand = o.optBoolean("templateAutoExpand", d.templateAutoExpand),
            theme = o.optInt("theme", d.theme),
            dynamicColor = o.optBoolean("dynamicColor", d.dynamicColor),
            accent = o.optInt("accent", d.accent),
            keyRadius = o.optInt("keyRadius", d.keyRadius),
            keyHeight = o.optInt("keyHeight", d.keyHeight),
            keyGap = o.optInt("keyGap", d.keyGap),
            keyBorders = o.optBoolean("keyBorders", d.keyBorders),
            bgImage = o.optBoolean("bgImage", d.bgImage),
            bgDim = o.optDouble("bgDim", d.bgDim.toDouble()).toFloat(),
            splitLandscape = o.optBoolean("splitLandscape", d.splitLandscape),
            oneHand = o.optInt("oneHand", d.oneHand),
            showPreview = o.optBoolean("showPreview", d.showPreview),
            sound = o.optInt("sound", d.sound),
            volume = o.optDouble("volume", d.volume.toDouble()).toFloat(),
            vibrate = o.optBoolean("vibrate", d.vibrate),
            vibrateMs = o.optInt("vibrateMs", d.vibrateMs),
            clipboardHistory = o.optBoolean("clipboardHistory", d.clipboardHistory),
            clipboardClearHours = o.optInt("clipboardClearHours", d.clipboardClearHours),
            incognitoAuto = o.optBoolean("incognitoAuto", d.incognitoAuto),
            internet = o.optBoolean("internet", d.internet),
            giphyKey = o.optString("giphyKey", d.giphyKey),
            voiceLang = o.optString("voiceLang", d.voiceLang),
            translateFrom = o.optString("translateFrom", d.translateFrom),
            translateTo = o.optString("translateTo", d.translateTo),
            templates = templates,
            tipsShown = o.optInt("tipsShown", d.tipsShown),
        )
    }
}

// ---------------- Bufer tarixi ----------------

data class ClipItem(val text: String, val time: Long, val pinned: Boolean)

object Clip {
    val items = MutableStateFlow<List<ClipItem>>(emptyList())

    fun load(prefs: SharedPreferences) {
        val raw = prefs.getString("clips", null) ?: return
        items.value = runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                ClipItem(o.getString("t"), o.optLong("time"), o.optBoolean("p"))
            }
        }.getOrDefault(emptyList())
    }

    private fun persist() {
        val a = JSONArray()
        items.value.forEach { a.put(JSONObject().put("t", it.text).put("time", it.time).put("p", it.pinned)) }
        Store.save("clips", a.toString())
    }

    fun add(text: String) {
        if (text.isBlank()) return
        val old = items.value.firstOrNull { it.text == text }
        val rest = items.value.filter { it.text != text }
        val item = ClipItem(text, System.currentTimeMillis(), old?.pinned ?: false)
        items.value = (listOf(item) + rest).let { list ->
            val pinned = list.filter { it.pinned }
            val normal = list.filter { !it.pinned }.take(50)
            pinned + normal
        }
        persist()
    }

    fun togglePin(item: ClipItem) {
        items.value = items.value.map { if (it == item) it.copy(pinned = !it.pinned) else it }
            .sortedWith(compareByDescending<ClipItem> { it.pinned }.thenByDescending { it.time })
        persist()
    }

    fun remove(item: ClipItem) {
        items.value = items.value.filter { it != item }
        persist()
    }

    fun clear() {
        items.value = items.value.filter { it.pinned }
        persist()
    }

    fun expire(hours: Int) {
        if (hours <= 0) return
        val limit = System.currentTimeMillis() - hours * 3600_000L
        val n = items.value.filter { it.pinned || it.time >= limit }
        if (n.size != items.value.size) {
            items.value = n; persist()
        }
    }
}

// ---------------- So'nggi emoji'lar ----------------

object Recents {
    val emojis = MutableStateFlow<List<String>>(emptyList())

    fun load(prefs: SharedPreferences) {
        emojis.value = prefs.getString("recent_emoji", "")!!.split('\u0001').filter { it.isNotEmpty() }
    }

    fun add(e: String) {
        emojis.value = (listOf(e) + emojis.value.filter { it != e }).take(32)
        Store.save("recent_emoji", emojis.value.joinToString("\u0001"))
    }
}

// ---------------- Statistika ----------------

object Stats {
    data class Day(val words: Int, val chars: Int)

    val days = MutableStateFlow<Map<String, Day>>(emptyMap())
    val totalWords = MutableStateFlow(0L)
    val totalChars = MutableStateFlow(0L)
    val typingMs = MutableStateFlow(0L)
    private var lastKey = 0L
    private var dirty = 0

    fun load(prefs: SharedPreferences) {
        val raw = prefs.getString("stats", null) ?: return
        runCatching {
            val o = JSONObject(raw)
            totalWords.value = o.optLong("w")
            totalChars.value = o.optLong("c")
            typingMs.value = o.optLong("ms")
            val d = o.optJSONObject("d") ?: JSONObject()
            val m = mutableMapOf<String, Day>()
            d.keys().forEach { k ->
                val a = d.getJSONArray(k)
                m[k] = Day(a.getInt(0), a.getInt(1))
            }
            days.value = m
        }
    }

    fun persist() {
        val d = JSONObject()
        days.value.entries.sortedByDescending { it.key }.take(60).forEach { (k, v) -> d.put(k, JSONArray().put(v.words).put(v.chars)) }
        Store.save(
            "stats",
            JSONObject().put("w", totalWords.value).put("c", totalChars.value).put("ms", typingMs.value).put("d", d).toString()
        )
        dirty = 0
    }

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun onChar() {
        val now = System.currentTimeMillis()
        if (lastKey != 0L && now - lastKey < 5000) typingMs.value += now - lastKey
        lastKey = now
        totalChars.value += 1
        val t = today()
        val cur = days.value[t] ?: Day(0, 0)
        days.value = days.value + (t to cur.copy(chars = cur.chars + 1))
        if (++dirty > 40) persist()
    }

    fun onWord() {
        totalWords.value += 1
        val t = today()
        val cur = days.value[t] ?: Day(0, 0)
        days.value = days.value + (t to cur.copy(words = cur.words + 1))
    }

    /** Daqiqasiga so'z (o'rtacha so'z = 5 belgi) */
    fun wpm(): Int {
        val min = typingMs.value / 60000.0
        if (min < 0.2) return 0
        return ((totalChars.value / 5.0) / min).toInt()
    }

    fun reset() {
        days.value = emptyMap(); totalWords.value = 0; totalChars.value = 0; typingMs.value = 0
        persist()
    }
}
