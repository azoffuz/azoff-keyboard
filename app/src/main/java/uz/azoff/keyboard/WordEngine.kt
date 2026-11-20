package uz.azoff.keyboard

import java.util.TreeMap
import kotlin.math.ln

/**
 * So'z takliflari, imlo tuzatish, keyingi so'z va glayd (swipe) yozish uchun lug'at.
 * Android'ga bog'liq emas — faqat sof Kotlin.
 */
class WordEngine {
    class Entry(val word: String, var freq: Int, var user: Int)

    private val map = TreeMap<String, Entry>()
    private val byEnds = HashMap<String, MutableList<String>>()
    private val bigrams = HashMap<String, HashMap<String, Int>>()
    private val alphabet = HashSet<Char>()
    var userDirty = false
        private set

    val size: Int get() = map.size

    private fun endsKey(k: String) = "${k.first()}${k.last()}"

    fun add(word: String, freq: Int, user: Int = 0) {
        val w = word.trim()
        if (w.isEmpty()) return
        val k = w.lowercase()
        val e = map[k]
        if (e != null) {
            if (freq > e.freq) e.freq = freq
            e.user += user
            return
        }
        map[k] = Entry(w, freq, user)
        if (k.length >= 2) byEnds.getOrPut(endsKey(k)) { ArrayList() }.add(k)
        k.forEach { if (it.isLetter() || it == Translit.OK || it == Translit.TT) alphabet.add(it) }
    }

    fun contains(word: String) = map.containsKey(word.lowercase())

    private fun score(e: Entry): Double = ln(e.freq + 2.0) + e.user * 1.5

    private fun matchCase(typed: String, word: String): String {
        if (typed.isEmpty()) return word
        val letters = typed.filter { it.isLetter() }
        return when {
            letters.length > 1 && letters.all { it.isUpperCase() } -> word.uppercase()
            typed.first().isUpperCase() -> word.replaceFirstChar { it.uppercaseChar() }
            else -> word
        }
    }

    /** Prefiks bo'yicha takliflar */
    fun suggest(prefix: String, limit: Int = 3): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val p = prefix.lowercase()
        val sub = map.subMap(p, true, p + '￿', false)
        val best = sub.values.asSequence()
            .take(4000)
            .sortedByDescending { score(it) + if (it.word.length == prefix.length) 0.5 else 0.0 }
            .take(limit)
            .map { matchCase(prefix, it.word) }
            .toList()
        return best
    }

    /** Imlo tuzatish: tahrir masofasi 1 bo'lgan eng mashhur so'z */
    fun correct(word: String): String? {
        if (word.length < 3) return null
        val w = word.lowercase()
        if (map.containsKey(w)) return null
        var best: Entry? = null
        var bestScore = Double.NEGATIVE_INFINITY
        fun check(c: String) {
            val e = map[c] ?: return
            val sc = score(e)
            if (sc > bestScore) { bestScore = sc; best = e }
        }
        val chars = alphabet.toList()
        for (i in 0..w.length) {
            if (i < w.length) check(w.removeRange(i, i + 1))                     // o'chirish
            if (i < w.length - 1) check(w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2)) // o'rin almashtirish
            for (c in chars) {
                if (i < w.length && c != w[i]) check(w.substring(0, i) + c + w.substring(i + 1)) // almashtirish
                check(w.substring(0, i) + c + w.substring(i))                    // qo'shish
            }
        }
        return best?.let { matchCase(word, it.word) }
    }

    /** Keyingi so'z taxmini (foydalanuvchi yozganlaridan o'rganadi) */
    fun next(prev: String, limit: Int = 3): List<String> {
        val m = bigrams[prev.lowercase()] ?: return emptyList()
        return m.entries.sortedByDescending { it.value }.take(limit).map { map[it.key]?.word ?: it.key }
    }

    fun learn(word: String) {
        val w = word.trim().trim { !it.isLetter() && it != Translit.OK && it != Translit.TT }
        if (w.length < 2 || w.any { it.isDigit() }) return
        val k = w.lowercase()
        val e = map[k]
        if (e != null) e.user += 1 else add(if (w.first().isUpperCase() && w.drop(1).all { !it.isUpperCase() }) w else k, 1, 1)
        userDirty = true
    }

    fun learnBigram(a: String, b: String) {
        val ka = a.lowercase(); val kb = b.lowercase()
        if (ka.isEmpty() || kb.isEmpty()) return
        val m = bigrams.getOrPut(ka) { HashMap() }
        m[kb] = (m[kb] ?: 0) + 1
        userDirty = true
    }

    fun forget(word: String) {
        map[word.lowercase()]?.let { it.user = 0 }
        userDirty = true
    }

    private fun dedupe(s: String): String {
        val sb = StringBuilder()
        for (c in s) if (sb.isEmpty() || sb.last() != c) sb.append(c)
        return sb.toString()
    }

    /** Glayd yozish: barmoq o'tgan tugmalar ketma-ketligidan so'zlarni topadi */
    fun glide(path: String, limit: Int = 5): List<String> {
        val p = dedupe(path.lowercase())
        if (p.length < 2) return emptyList()
        val cands = byEnds[endsKey(p)] ?: return emptyList()
        data class S(val k: String, val s: Double)
        val scored = ArrayList<S>()
        for (k in cands) {
            val w = dedupe(k)
            if (w.length > p.length + 1) continue
            // so'z harflari yo'lda ketma-ket uchrashi kerak
            var j = 0
            var miss = 0
            for (ch in w) {
                val idx = p.indexOf(ch, j)
                if (idx < 0) { miss++ } else j = idx + 1
                if (miss > 1) break
            }
            if (miss > 1) continue
            val e = map[k] ?: continue
            val s = score(e) - miss * 3.0 - (p.length - w.length).coerceAtLeast(0) * 0.03 + w.length * 0.15
            scored.add(S(k, s))
        }
        return scored.sortedByDescending { it.s }.take(limit).map { map[it.k]!!.word }
    }

    fun topUserWords(limit: Int): List<Pair<String, Int>> =
        map.values.filter { it.user > 0 }.sortedByDescending { it.user }.take(limit).map { it.word to it.user }

    // --------- foydalanuvchi ma'lumotlarini saqlash ---------

    fun exportUser(): String {
        val sb = StringBuilder()
        map.values.filter { it.user > 0 }.forEach { sb.append("w\t").append(it.word).append('\t').append(it.user).append('\n') }
        bigrams.forEach { (a, m) -> m.forEach { (b, c) -> sb.append("b\t").append(a).append('\t').append(b).append('\t').append(c).append('\n') } }
        userDirty = false
        return sb.toString()
    }

    fun importUser(text: String) {
        text.lineSequence().forEach { line ->
            val p = line.split('\t')
            when {
                p.size >= 3 && p[0] == "w" -> {
                    val k = p[1].lowercase()
                    val n = p[2].toIntOrNull() ?: 1
                    val e = map[k]
                    if (e != null) e.user = n else add(p[1], 1, n)
                }
                p.size >= 4 && p[0] == "b" -> {
                    bigrams.getOrPut(p[1]) { HashMap() }[p[2]] = p[3].toIntOrNull() ?: 1
                }
            }
        }
        userDirty = false
    }

    fun clearUser() {
        map.values.forEach { it.user = 0 }
        bigrams.clear()
        userDirty = true
    }
}
