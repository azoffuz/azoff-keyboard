package uz.azoff.keyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Lug'atlarni yuklash va saqlash */
object Dict {
    private val engines = HashMap<String, WordEngine>()
    private val loading = HashSet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun langOf(layout: String) = when (layout) {
        LayoutIds.UZ_NEW -> "uz_new"
        LayoutIds.UZ_LAT -> "uz_lat"
        LayoutIds.UZ_CYR -> "uz_cyr"
        LayoutIds.RU -> "ru"
        else -> "en"
    }

    @Synchronized
    fun get(ctx: Context, lang: String): WordEngine? {
        engines[lang]?.let { return it }
        if (lang !in loading) {
            loading.add(lang)
            val app = ctx.applicationContext
            scope.launch {
                val e = WordEngine()
                try {
                    when (lang) {
                        "uz_lat", "uz_new" -> app.assets.open("dict_uz_lat.txt").bufferedReader().forEachLine { l ->
                            val t = l.indexOf('\t')
                            if (t > 0) {
                                val w = l.substring(0, t)
                                e.add(if (lang == "uz_new") Translit.latToNew(w) else w, l.substring(t + 1).toIntOrNull() ?: 1)
                            }
                        }
                        "uz_cyr" -> app.assets.open("dict_uz_cyr.txt").bufferedReader().forEachLine { l ->
                            val t = l.indexOf('\t')
                            if (t > 0) e.add(l.substring(0, t), l.substring(t + 1).toIntOrNull() ?: 1)
                        }
                        "ru" -> BaseWords.ru.split(' ').forEachIndexed { i, w -> e.add(w, 500 - i) }
                        else -> BaseWords.en.split(' ').forEachIndexed { i, w -> e.add(w, 500 - i) }
                    }
                    val f = File(app.filesDir, "user_$lang.tsv")
                    if (f.exists()) e.importUser(f.readText())
                } catch (_: Exception) {
                }
                synchronized(this@Dict) {
                    engines[lang] = e
                    loading.remove(lang)
                }
            }
        }
        return null
    }

    fun saveAll(ctx: Context) {
        val list = synchronized(this) { engines.toMap() }
        val dir = ctx.applicationContext.filesDir
        scope.launch {
            list.forEach { (lang, e) ->
                if (e.userDirty) runCatching { File(dir, "user_$lang.tsv").writeText(e.exportUser()) }
            }
        }
    }

    fun clearUser(ctx: Context) {
        synchronized(this) { engines.values.forEach { it.clearUser() } }
        ctx.applicationContext.filesDir.listFiles()?.filter { it.name.startsWith("user_") }?.forEach { it.delete() }
    }

    fun topWords(limit: Int): List<Pair<String, Int>> = synchronized(this) {
        engines.values.flatMap { it.topUserWords(limit) }.sortedByDescending { it.second }.take(limit)
    }
}

/** Rus va ingliz tillari uchun eng ko'p ishlatiladigan so'zlar */
object BaseWords {
    const val ru = "и в не на я что он с как это по а но все она так его к да ты же у вы за бы от мне было вот меня еще нет о из ему теперь когда даже ну вдруг ли если уже или ни быть был него до вас нибудь опять уж вам ведь там потом себя ничего ей может они тут где есть надо ней для мы тебя их чем была сам чтоб без будто чего раз тоже себе под будет ж тогда кто этот того потому этого какой совсем ним здесь этом один почти мой тем чтобы нее сейчас были куда зачем всех никогда можно при наконец два об другой хоть после над больше тот через эти нас про всего них какая много разве три эту моя впрочем хорошо свою этой перед иногда лучше чуть том нельзя такой им более всегда конечно всю между привет спасибо пожалуйста здравствуйте хорошо да нет сегодня завтра вчера время день работа дом друг люди жизнь деньги телефон сообщение напиши позвони жду люблю понял поняла ладно давай пока скоро сейчас минут часов утром вечером"
    const val en = "the be to of and a in that have i it for not on with he as you do at this but his by from they we say her she or an will my one all would there their what so up out if about who get which go me when make can like time no just him know take people into year your good some could them see other than then now look only come its over think also back after use two how our work first well way even new want because any these give day most us hello thanks thank please yes okay ok sorry great love tomorrow today tonight morning see you soon meeting call message phone email home friend family happy birthday congratulations"
}

/** Emoji ma'lumotlari: emoji, guruh, uz/ru/en kalit so'zlar, teri ranglari */
data class EmojiItem(val e: String, val group: Int, val keys: String, val skins: List<String>)

object EmojiData {
    @Volatile
    var items: List<EmojiItem> = emptyList()
        private set
    private var started = false

    val groups = listOf(
        -1 to "🕘", 0 to "😀", 1 to "👋", 3 to "🐻", 4 to "🍔", 5 to "✈️", 6 to "⚽", 7 to "💡", 8 to "❤️", 9 to "🏳️", -2 to "(^_^)",
    )

    val kaomoji = listOf(
        "(^_^)", "(^▽^)", "(✿◠‿◠)", "(◕‿◕)", "(｡♥‿♥｡)", "(づ｡◕‿‿◕｡)づ", "ʕ•ᴥ•ʔ", "(=^･ω･^=)", "(╯°□°)╯︵ ┻━┻", "┬─┬ノ( º _ ºノ)",
        "¯\\_(ツ)_/¯", "(ಥ﹏ಥ)", "(T_T)", "(>_<)", "(-_-)", "(¬‿¬)", "(•_•)", "( •_•)>⌐■-■", "(⌐■_■)", "ᕦ(ò_óˇ)ᕤ",
        "(ง'̀-'́)ง", "(っ˘ω˘ς )", "(*^▽^*)", "(≧◡≦)", "(o^^)o", "\\(^o^)/", "(•‿•)", "(ʘ‿ʘ)", "(°ロ°)", "(⊙_⊙)",
        "(o_O)", "(¬_¬)", "(￣ー￣)", "(＾＾)ｂ", "(^_−)☆", "(｡•́︿•̀｡)", "(._.)", "(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧", "♪(´▽｀)", "(~˘▾˘)~",
    )

    fun load(ctx: Context) {
        if (started) return
        started = true
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val list = ArrayList<EmojiItem>(1900)
            runCatching {
                app.assets.open("emoji.tsv").bufferedReader().forEachLine { l ->
                    val p = l.split('\t')
                    if (p.size >= 5) {
                        val keys = (p[2] + " " + p[3] + " " + p[4]).lowercase().filter { !Translit.isApostrophe(it) }
                        val skins = p.getOrNull(5)?.split(' ')?.filter { it.isNotEmpty() } ?: emptyList()
                        list.add(EmojiItem(p[0], p[1].toIntOrNull() ?: 0, keys, skins))
                    }
                }
            }
            items = list
        }
    }

    fun search(q: String, limit: Int = 40): List<String> {
        val query = q.lowercase().filter { !Translit.isApostrophe(it) }.trim()
        if (query.isEmpty()) return emptyList()
        // yangi alifbo harflari bilan yozilsa ham topilsin
        val alt = Translit.newToLat(query).filter { !Translit.isApostrophe(it) }
        return items.asSequence()
            .filter { it.keys.contains(query) || it.keys.contains(alt) }
            .sortedBy { item ->
                val words = item.keys.split(' ')
                if (words.any { it.startsWith(query) || it.startsWith(alt) }) 0 else 1
            }
            .map { it.e }
            .take(limit)
            .toList()
    }
}

/** Tugma ovozlari: dasturning o'zida sintez qilinadi (fayl kerak emas) */
object Sounds {
    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    private var preparedFor = -1

    fun prepare(ctx: Context, kind: Int) {
        if (kind < 2 || kind == preparedFor) return
        preparedFor = kind
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val p = pool ?: SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                ).build().also { pool = it }
            val dir = File(app.cacheDir, "sounds").apply { mkdirs() }
            listOf("key", "space", "del").forEach { name ->
                val f = File(dir, "${kind}_$name.wav")
                if (!f.exists()) writeWav(f, synth(kind, name))
                synchronized(ids) { ids["${kind}_$name"] = p.load(f.absolutePath, 1) }
            }
        }
    }

    fun play(ctx: Context, kind: Int, volume: Float, type: KT) {
        if (kind == 0) return
        if (kind == 1) {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val fx = when (type) {
                KT.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                KT.DEL -> AudioManager.FX_KEYPRESS_DELETE
                KT.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            am.playSoundEffect(fx, volume)
            return
        }
        val name = when (type) { KT.SPACE, KT.ENTER -> "space"; KT.DEL -> "del"; else -> "key" }
        val id = synchronized(ids) { ids["${kind}_$name"] } ?: run { prepare(ctx, kind); return }
        pool?.play(id, volume, volume, 1, 0, 1f)
    }

    private const val RATE = 22050

    private fun synth(kind: Int, name: String): ShortArray {
        val rnd = Random(kind * 31 + name.hashCode())
        val lenMs = when (kind) { 2 -> 45; 3 -> 35; else -> 70 }
        val n = RATE * lenMs / 1000
        val out = ShortArray(n)
        val pitch = when (name) { "space" -> 0.75; "del" -> 1.15; else -> 1.0 }
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            val v = when (kind) {
                // mexanik: shovqinli "chiq" + past "tuk"
                2 -> (rnd.nextDouble(-1.0, 1.0) * exp(-t * 180) * 0.6 +
                    sin(2 * PI * 180 * pitch * t) * exp(-t * 60) * 0.5)
                // yumshoq: qisqa past sinus
                3 -> sin(2 * PI * 520 * pitch * t) * exp(-t * 120) * 0.45
                // yozuv mashinkasi: o'tkir zarba + metall jaranglash
                else -> (rnd.nextDouble(-1.0, 1.0) * exp(-t * 400) * 0.8 +
                    sin(2 * PI * 2400 * pitch * t) * exp(-t * 45) * 0.18 +
                    sin(2 * PI * 140 * t) * exp(-t * 90) * 0.35)
            }
            out[i] = (v.coerceIn(-1.0, 1.0) * 30000).toInt().toShort()
        }
        return out
    }

    private fun writeWav(f: File, pcm: ShortArray) {
        val dataLen = pcm.size * 2
        val bb = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(36 + dataLen); bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()); bb.putInt(16); bb.putShort(1); bb.putShort(1)
        bb.putInt(RATE); bb.putInt(RATE * 2); bb.putShort(2); bb.putShort(16)
        bb.put("data".toByteArray()); bb.putInt(dataLen)
        pcm.forEach { bb.putShort(it) }
        FileOutputStream(f).use { it.write(bb.array()) }
    }
}

/** Internet xizmatlari: tarjima (MyMemory) va GIF (GIPHY) */
object Net {
    private fun get(url: String): String {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "AzoffKeyboard/1.0")
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    suspend fun translate(text: String, from: String, to: String): String? = withContext(Dispatchers.IO) {
        try {
            val src = if (Translit.hasNewLetters(text)) Translit.newToLat(text) else text
            val json = org.json.JSONObject(get("https://api.mymemory.translated.net/get?q=${enc(src)}&langpair=$from|$to"))
            val r = json.optJSONObject("responseData")?.optString("translatedText")
            if (r.isNullOrBlank()) null else android.text.Html.fromHtml(r, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        } catch (e: Exception) {
            null
        }
    }

    data class Gif(val id: String, val preview: String, val full: String, val page: String)

    suspend fun gifs(key: String, query: String): List<Gif>? = withContext(Dispatchers.IO) {
        try {
            val url = if (query.isBlank())
                "https://api.giphy.com/v1/gifs/trending?api_key=${enc(key)}&limit=30&rating=g"
            else
                "https://api.giphy.com/v1/gifs/search?api_key=${enc(key)}&q=${enc(query)}&limit=30&rating=g"
            val data = org.json.JSONObject(get(url)).getJSONArray("data")
            (0 until data.length()).mapNotNull { i ->
                val o = data.getJSONObject(i)
                val imgs = o.optJSONObject("images") ?: return@mapNotNull null
                val prev = imgs.optJSONObject("fixed_width_small")?.optString("url")
                    ?: imgs.optJSONObject("fixed_width")?.optString("url") ?: return@mapNotNull null
                val full = imgs.optJSONObject("fixed_width")?.optString("url") ?: prev
                Gif(o.optString("id"), prev, full, o.optString("url"))
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun download(ctx: Context, url: String, name: String): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(ctx.cacheDir, "gifs").apply { mkdirs() }
            dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86400000L }?.forEach { it.delete() }
            val f = File(dir, "$name.gif")
            java.net.URL(url).openStream().use { input -> FileOutputStream(f).use { input.copyTo(it) } }
            f
        } catch (e: Exception) {
            null
        }
    }
}
