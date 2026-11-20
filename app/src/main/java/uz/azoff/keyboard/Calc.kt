package uz.azoff.keyboard

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Oddiy kalkulyator: + − × ÷ % ( ) va o'nli sonlar */
object Calc {
    private class Parser(val s: String) {
        var i = 0
        fun peek(): Char? { skip(); return s.getOrNull(i) }
        fun skip() { while (i < s.length && s[i] == ' ') i++ }

        fun expr(): Double {
            var v = term()
            while (true) {
                when (peek()) {
                    '+' -> { i++; v += term() }
                    '-', '−' -> { i++; v -= term() }
                    else -> return v
                }
            }
        }

        fun term(): Double {
            var v = factor()
            while (true) {
                when (peek()) {
                    '*', '×' -> { i++; v *= factor() }
                    '/', '÷' -> { i++; val d = factor(); if (d == 0.0) throw ArithmeticException("0"); v /= d }
                    '%' -> { i++; v /= 100.0 }
                    else -> return v
                }
            }
        }

        fun factor(): Double {
            val c = peek() ?: throw IllegalArgumentException("end")
            if (c == '-' || c == '−') { i++; return -factor() }
            if (c == '+') { i++; return factor() }
            if (c == '(') {
                i++
                val v = expr()
                if (peek() == ')') i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == ',')) i++
            if (start == i) throw IllegalArgumentException("num")
            return s.substring(start, i).replace(',', '.').toDouble()
        }
    }

    fun eval(expr: String): String? = try {
        val p = Parser(expr)
        val v = p.expr()
        if (p.peek() != null) null else format(v)
    } catch (e: Exception) {
        null
    }

    fun format(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "?"
        val bd = BigDecimal(v).round(MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros()
        val plain = bd.toPlainString()
        return if (plain == "-0") "0" else plain
    }

    /** 1234567 → "1 234 567" */
    fun groupDigits(digits: String): String {
        val neg = digits.startsWith("-")
        val d = digits.removePrefix("-")
        val intPart = d.substringBefore('.')
        val frac = if (d.contains('.')) "," + d.substringAfter('.') else ""
        val grouped = intPart.reversed().chunked(3).joinToString(" ").reversed()
        return (if (neg) "-" else "") + grouped + frac
    }
}
