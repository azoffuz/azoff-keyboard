package uz.azoff.keyboard

/**
 * O'zbek yozuvlari orasida o'girish:
 *  - joriy lotin (oʻ gʻ sh ch)  ⇄  yangi lotin (ö ğ ş ç)
 *  - lotin  ⇄  kirill
 */
object Translit {
    const val OK = 'ʻ'   // ʻ  (oʻ, gʻ)
    const val TT = 'ʼ'   // ʼ  (tutuq belgisi)
    private val apostrophes = setOf('\'', '‘', '’', '`', OK, TT, '´')

    fun isApostrophe(c: Char) = c in apostrophes

    private fun applyCase(src: String, out: String): String {
        val letters = src.filter { it.isLetter() }
        if (letters.isEmpty()) return out
        return when {
            letters.length > 1 && letters.all { it.isUpperCase() } -> out.uppercase()
            letters.first().isUpperCase() -> out.replaceFirstChar { it.uppercaseChar() }
            else -> out
        }
    }

    /** Joriy lotin → yangi alifbo */
    fun latToNew(t: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < t.length) {
            val c = t[i]
            val n = t.getOrNull(i + 1)
            val n2 = t.getOrNull(i + 2)
            val lc = c.lowercaseChar()
            when {
                (lc == 'o' || lc == 'g') && n != null && isApostrophe(n) -> {
                    val r = if (lc == 'o') "ö" else "ğ"
                    sb.append(if (c.isUpperCase()) r.uppercase() else r); i += 2
                }
                lc == 's' && n != null && isApostrophe(n) && n2 != null && n2.lowercaseChar() == 'h' -> {
                    // sʼh (ikki alohida tovush) → yangi alifboda shunchaki "sh"
                    sb.append(c).append(n2); i += 3
                }
                (lc == 's' || lc == 'c') && n != null && n.lowercaseChar() == 'h' -> {
                    val r = if (lc == 's') "ş" else "ç"
                    sb.append(applyCase("$c$n", r)); i += 2
                }
                isApostrophe(c) -> { sb.append(TT); i++ }
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString()
    }

    /** Yangi alifbo → joriy lotin */
    fun newToLat(t: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < t.length) {
            val c = t[i]
            val n = t.getOrNull(i + 1)
            when {
                c.lowercaseChar() == 's' && n != null && n.lowercaseChar() == 'h' -> {
                    sb.append(c).append(TT).append(n); i += 2
                }
                c == 'ö' -> { sb.append("o").append(OK); i++ }
                c == 'Ö' -> { sb.append("O").append(OK); i++ }
                c == 'ğ' -> { sb.append("g").append(OK); i++ }
                c == 'Ğ' -> { sb.append("G").append(OK); i++ }
                c == 'ş' -> { sb.append("sh"); i++ }
                c == 'Ş' -> { sb.append(if (n != null && n.isUpperCase()) "SH" else "Sh"); i++ }
                c == 'ç' -> { sb.append("ch"); i++ }
                c == 'Ç' -> { sb.append(if (n != null && n.isUpperCase()) "CH" else "Ch"); i++ }
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString()
    }

    private val cyrVowels = "аеёиоуэюяўАЕЁИОУЭЮЯЎ"

    private val c2l = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'ё' to "yo", 'ж' to "j", 'з' to "z",
        'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p",
        'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "x", 'ч' to "ch", 'ш' to "sh",
        'щ' to "sh", 'ъ' to TT.toString(), 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya", 'ў' to "o$OK",
        'қ' to "q", 'ғ' to "g$OK", 'ҳ' to "h", 'ы' to "i",
    )

    /** Kirill → joriy lotin */
    fun cyrToLat(t: String): String {
        val sb = StringBuilder()
        for (i in t.indices) {
            val c = t[i]
            val lc = c.lowercaseChar()
            val prev = t.getOrNull(i - 1)
            val next = t.getOrNull(i + 1)
            val wordStart = prev == null || !prev.isLetter()
            val r: String? = when {
                lc == 'е' -> if (wordStart || (prev != null && (prev in cyrVowels || prev.lowercaseChar() == 'ъ' || prev.lowercaseChar() == 'ь'))) "ye" else "e"
                lc == 'ц' -> if (!wordStart && prev != null && prev in cyrVowels) "ts" else "s"
                lc == 'с' && next != null && next.lowercaseChar() == 'ҳ' -> "s$TT"
                else -> c2l[lc]
            }
            if (r == null) {
                sb.append(c)
            } else if (c.isUpperCase()) {
                val nextUpper = next != null && next.isUpperCase()
                val prevUpper = prev != null && prev.isUpperCase()
                sb.append(if (nextUpper || (prevUpper && (next == null || !next.isLetter()))) r.uppercase() else r.replaceFirstChar { it.uppercaseChar() })
            } else {
                sb.append(r)
            }
        }
        return sb.toString()
    }

    private val l2c = mapOf(
        'a' to 'а', 'b' to 'б', 'd' to 'д', 'f' to 'ф', 'g' to 'г', 'h' to 'ҳ', 'i' to 'и', 'j' to 'ж',
        'k' to 'к', 'l' to 'л', 'm' to 'м', 'n' to 'н', 'o' to 'о', 'p' to 'п', 'q' to 'қ', 'r' to 'р',
        's' to 'с', 't' to 'т', 'u' to 'у', 'v' to 'в', 'x' to 'х', 'y' to 'й', 'z' to 'з', 'c' to 'с', 'w' to 'в',
    )

    /** Joriy lotin (yoki yangi alifbo) → kirill */
    fun latToCyr(t: String): String {
        val isNew = hasNewLetters(t)
        val sb = StringBuilder()
        var i = 0
        fun put(src: String, r: String) = sb.append(applyCase(src, r))
        while (i < t.length) {
            val c = t[i]
            val lc = c.lowercaseChar()
            val n = t.getOrNull(i + 1)
            val ln = n?.lowercaseChar()
            val n2 = t.getOrNull(i + 2)
            val prev = t.getOrNull(i - 1)
            val wordStart = prev == null || !prev.isLetter()
            when {
                lc == 'ö' -> { put("$c", "ў"); i++ }
                lc == 'ğ' -> { put("$c", "ғ"); i++ }
                lc == 'ş' -> { put("$c", "ш"); i++ }
                lc == 'ç' -> { put("$c", "ч"); i++ }
                (lc == 'o' || lc == 'g') && n != null && isApostrophe(n) -> { put("$c", if (lc == 'o') "ў" else "ғ"); i += 2 }
                lc == 's' && n != null && isApostrophe(n) && n2?.lowercaseChar() == 'h' -> {
                    put("$c", "с"); sb.append(if (n2.isUpperCase()) 'Ҳ' else 'ҳ'); i += 3
                }
                lc == 's' && ln == 'h' && !isNew -> { put("$c$n", "ш"); i += 2 }
                lc == 'c' && ln == 'h' -> { put("$c$n", "ч"); i += 2 }
                lc == 'y' && ln == 'o' && !(n2 != null && isApostrophe(n2)) -> { put("$c$n", "ё"); i += 2 }
                lc == 'y' && ln == 'u' -> { put("$c$n", "ю"); i += 2 }
                lc == 'y' && ln == 'a' -> { put("$c$n", "я"); i += 2 }
                lc == 'y' && ln == 'e' -> { put("$c$n", "е"); i += 2 }
                lc == 'e' -> { put("$c", if (wordStart) "э" else "е"); i++ }
                isApostrophe(c) && prev != null && prev.isLetter() -> { sb.append('ъ'); i++ }
                l2c.containsKey(lc) -> { val r = l2c[lc]!!; sb.append(if (c.isUpperCase()) r.uppercaseChar() else r); i++ }
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString()
    }

    /** Tutuq belgilarini to'g'rilash: oʻ gʻ uchun ʻ, qolganlari uchun ʼ */
    fun fixApostrophes(t: String): String {
        val sb = StringBuilder()
        for (i in t.indices) {
            val c = t[i]
            if (isApostrophe(c)) {
                val prev = t.getOrNull(i - 1)
                val next = t.getOrNull(i + 1)
                if (prev != null && (prev.lowercaseChar() == 'o' || prev.lowercaseChar() == 'g') &&
                    (next == null || next.isLetter() || !next.isLetterOrDigit())
                ) {
                    // "o'" yoki "g'" — oʻ / gʻ
                    sb.append(OK)
                } else if (prev != null && prev.isLetter() && next != null && next.isLetter()) {
                    sb.append(TT)
                } else {
                    sb.append(c)
                }
            } else sb.append(c)
        }
        return sb.toString()
    }

    private val cyrLook = mapOf('а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'у' to 'y', 'х' to 'x', 'к' to 'k',
        'А' to 'A', 'В' to 'B', 'Е' to 'E', 'К' to 'K', 'М' to 'M', 'Н' to 'H', 'О' to 'O', 'Р' to 'P', 'С' to 'C', 'Т' to 'T', 'Х' to 'X', 'і' to 'i')
    private val latLook = cyrLook.entries.associate { (k, v) -> v to k }

    /** Bir so'z ichida aralashgan lotin/kirill belgilarni bir xillashtiradi */
    fun cleanMixed(t: String): String {
        val sb = StringBuilder()
        val word = StringBuilder()
        fun flush() {
            if (word.isEmpty()) return
            val w = word.toString()
            val cyr = w.count { it in 'Ѐ'..'ӿ' }
            val lat = w.count { it in 'a'..'z' || it in 'A'..'Z' }
            sb.append(
                when {
                    cyr > 0 && lat > 0 && lat >= cyr -> w.map { cyrLook[it] ?: it }.joinToString("")
                    cyr > 0 && lat > 0 -> w.map { latLook[it] ?: it }.joinToString("")
                    else -> w
                }
            )
            word.clear()
        }
        for (c in t) {
            if (c.isLetter() || isApostrophe(c)) word.append(c) else { flush(); sb.append(c) }
        }
        flush()
        return sb.toString()
    }

    /** Matn qaysi yozuvda ekanini taxmin qiladi */
    fun isMostlyCyrillic(t: String): Boolean {
        val cyr = t.count { it in 'Ѐ'..'ӿ' }
        val lat = t.count { it in 'a'..'z' || it in 'A'..'Z' }
        return cyr > lat
    }

    fun hasNewLetters(t: String) = t.any { it in "öÖğĞşŞçÇ" }
}
