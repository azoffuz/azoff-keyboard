package uz.azoff.keyboard

enum class KT { CHAR, SHIFT, DEL, ENTER, SPACE, SYMBOLS, ABC, SYM_PAGE, LANG }

data class Key(
    val label: String,
    val output: String = label,
    val type: KT = KT.CHAR,
    val width: Float = 1f,
    val longPress: List<String> = emptyList(),
    val hint: String? = null,
) {
    /** Harf tugmasimi (shift ta'sir qiladi) */
    val isLetter: Boolean get() = type == KT.CHAR && label.any { it.isLetter() }
}

data class KbLayout(val id: String, val rows: List<List<Key>>, val units: Float)

object Layouts {
    private const val OK = Translit.OK
    private const val TT = Translit.TT

    private val accents = mapOf(
        "a" to listOf("à", "á", "â", "ä", "ã", "å", "æ"),
        "e" to listOf("é", "è", "ê", "ë", "ə"),
        "i" to listOf("í", "ì", "î", "ï", "ı"),
        "o" to listOf("ö", "o$OK", "ó", "ò", "ô", "õ", "ø"),
        "u" to listOf("ü", "ú", "ù", "û"),
        "c" to listOf("ç", "ch", "ć", "č"),
        "s" to listOf("ş", "sh", "ś", "š", "ß"),
        "g" to listOf("ğ", "g$OK"),
        "n" to listOf("ñ", "ng", "ń"),
        "y" to listOf("ý", "ÿ"),
        "z" to listOf("ž", "ź", "ż"),
        "ö" to listOf("o$OK", "o"),
        "ğ" to listOf("g$OK", "g"),
        "ş" to listOf("sh", "s"),
        "ç" to listOf("ch", "c"),
        "o$OK" to listOf("ö", "o"),
        "g$OK" to listOf("ğ", "g"),
        TT.toString() to listOf("$OK", "'", "’", "`", "\""),
        "е" to listOf("ё", "э"),
        "ь" to listOf("ъ"),
        "ш" to listOf("щ"),
        "х" to listOf("ҳ", "ъ"),
        "и" to listOf("й"),
        "у" to listOf("ў"),
        "к" to listOf("қ"),
        "г" to listOf("ғ"),
        "ў" to listOf("у"),
        "қ" to listOf("к"),
        "ғ" to listOf("г"),
        "ҳ" to listOf("х"),
    )
    private val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    private fun row(chars: String, digitHints: Boolean = false): List<Key> =
        chars.split(' ').filter { it.isNotEmpty() }.mapIndexed { i, c ->
            val lp = mutableListOf<String>()
            val d = if (digitHints && i < 10) digits[i] else null
            if (d != null) lp.add(d)
            lp.addAll(accents[c] ?: emptyList())
            Key(c, longPress = lp, hint = d)
        }

    private fun shift(w: Float) = Key("⇧", type = KT.SHIFT, width = w)
    private fun del(w: Float) = Key("⌫", type = KT.DEL, width = w)

    private val periodLong = listOf(",", "?", "!", ":", ";", "-", "…", "\"", "'", "(", ")", "/", "@", "#", "%", "&")

    private fun bottom(units: Float, symbolsMode: Boolean, spaceLabel: String): List<Key> = listOf(
        if (symbolsMode) Key("ABC", type = KT.ABC, width = 1.5f) else Key("?123", type = KT.SYMBOLS, width = 1.5f),
        Key("🌐", type = KT.LANG),
        Key(",", longPress = listOf("😊", ";", ":", "\"", "'", "!", "?")),
        Key(spaceLabel, " ", KT.SPACE, width = units - 6f),
        Key(".", longPress = periodLong),
        Key("⏎", type = KT.ENTER, width = 1.5f),
    )

    private fun numberRow(units: Float): List<Key> =
        digits.map { Key(it, width = units / 10f, longPress = listOf(superscript(it), fraction(it)).filter { s -> s.isNotEmpty() }) }

    private fun superscript(d: String) = mapOf("1" to "¹", "2" to "²", "3" to "³", "0" to "°")[d] ?: ""
    private fun fraction(d: String) = mapOf("1" to "½", "2" to "⅔", "3" to "¾", "4" to "¼")[d] ?: ""

    fun letters(id: String, numbers: Boolean): KbLayout {
        val (rows, units) = when (id) {
            LayoutIds.UZ_NEW -> listOf(
                row("q w e r t y u i o p ö", !numbers),
                row("a s d f g h j k l ğ ş"),
                listOf(shift(1.5f)) + row("z x c ç v b n m") + del(1.5f),
            ) to 11f
            LayoutIds.UZ_LAT -> listOf(
                row("q w e r t y u i o p o$OK", !numbers),
                row("a s d f g h j k l g$OK $TT"),
                listOf(shift(2f)) + row("z x c v b n m") + del(2f),
            ) to 11f
            LayoutIds.UZ_CYR -> listOf(
                row("й ц у к е н г ш ў з х ҳ", !numbers),
                row("ф қ в а п р о л д ж э ғ"),
                listOf(shift(1.5f)) + row("я ч с м и т ь б ю") + del(1.5f),
            ) to 12f
            LayoutIds.RU -> listOf(
                row("й ц у к е н г ш щ з х", !numbers),
                row("ф ы в а п р о л д ж э"),
                listOf(shift(1f)) + row("я ч с м и т ь б ю") + del(1f),
            ) to 11f
            else -> listOf(
                row("q w e r t y u i o p", !numbers),
                row("a s d f g h j k l"),
                listOf(shift(1.5f)) + row("z x c v b n m") + del(1.5f),
            ) to 10f
        }
        val all = (if (numbers) listOf(numberRow(units)) else emptyList()) + rows +
            listOf(bottom(units, false, LayoutIds.short(id)))
        return KbLayout(id, all, units)
    }

    fun symbols(page: Int, id: String): KbLayout {
        val u = 10f
        val rows = if (page == 0) listOf(
            row("1 2 3 4 5 6 7 8 9 0"),
            row("@ # $ _ & - + ( ) /"),
            listOf(Key("=\\<", type = KT.SYM_PAGE, width = 1.5f)) + row("* \" ' : ; ! ?") + del(1.5f),
        ) else listOf(
            row("~ ` | • √ π ÷ × ¶ ∆"),
            row("£ € ¥ ^ ° = { } \\ %"),
            listOf(Key("?123", type = KT.SYM_PAGE, width = 1.5f)) + row("© ® ™ [ ] < >") + del(1.5f),
        )
        return KbLayout("sym$page", rows + listOf(bottom(u, true, LayoutIds.short(id))), u)
    }

    fun numpad(phone: Boolean): KbLayout {
        val rows = if (phone) listOf(
            row("1 2 3 +"),
            row("4 5 6") + Key("ABC", type = KT.ABC),
            row("7 8 9") + del(1f),
            row("* 0 #") + Key("⏎", type = KT.ENTER),
        ) else listOf(
            row("1 2 3 -"),
            row("4 5 6") + Key("␣", " ", KT.SPACE),
            row("7 8 9") + del(1f),
            listOf(Key("ABC", type = KT.ABC)) + row("0 .") + Key("⏎", type = KT.ENTER),
        )
        return KbLayout(if (phone) "phone" else "num", rows, 4f)
    }
}
