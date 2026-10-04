package com.jarvis.assistant.commands

/**
 * Tiny recursive-descent calculator: +, -, *, /, parentheses, decimals, unary minus.
 * No external library needed (Android has no built-in expression evaluator).
 */
object CalculatorController {

    fun evaluate(expression: String): String {
        return try {
            val cleaned = expression
                .lowercase()
                .replace("virgül", ".")
                .replace(",", ".")
                .replace("artı", "+").replace("+", "+")
                .replace("eksi", "-")
                .replace("çarpı", "*").replace("x", "*").replace("×", "*")
                .replace("bölü", "/").replace("÷", "/")
                .replace(Regex("[^0-9+\\-*/().]"), "")
            if (cleaned.isBlank()) return "Bunu bir hesaplama olarak anlayamadım."
            val result = Parser(cleaned).parse()
            val formatted = if (result == result.toLong().toDouble()) result.toLong().toString()
            else String.format("%.4f", result).trimEnd('0').trimEnd('.')
            "Sonuç: $formatted"
        } catch (e: Exception) {
            "Bunu hesaplayamadım, tekrar söyler misin?"
        }
    }

    private class Parser(private val text: String) {
        private var pos = 0

        fun parse(): Double {
            val value = parseExpr()
            return value
        }

        private fun peek(): Char? = if (pos < text.length) text[pos] else null

        private fun parseExpr(): Double {
            var value = parseTerm()
            while (true) {
                when (peek()) {
                    '+' -> { pos++; value += parseTerm() }
                    '-' -> { pos++; value -= parseTerm() }
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (true) {
                when (peek()) {
                    '*' -> { pos++; value *= parseFactor() }
                    '/' -> { pos++; value /= parseFactor() }
                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double {
            if (peek() == '-') { pos++; return -parseFactor() }
            if (peek() == '(') {
                pos++
                val value = parseExpr()
                if (peek() == ')') pos++
                return value
            }
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] == '.')) pos++
            if (start == pos) throw IllegalArgumentException("Beklenmeyen karakter")
            return text.substring(start, pos).toDouble()
        }
    }
}
