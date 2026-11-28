package com.jarvis.assistant.speech

/**
 * Sesli okunacak metni temizler: emoji, yüz ifadeleri (:) :D ;) <3), markdown işaretleri, kod blokları
 * ve bağlantılar okunmaz. Ekranda yazı aynen kalır, yalnızca konuşma temizlenir.
 */
object SpeechSanitizer {

    private val fencedCode = Regex("""```[\s\S]*?```""")
    private val unclosedFence = Regex("""```[\s\S]*$""")
    private val url = Regex("""https?://\S+|www\.\S+""")
    private val emoticon = Regex(
        """(?<![\p{L}\p{N}])(?:[:;=][-^']?[)(DdPpOo|/\\*\]\[]+|[xX]D|<3|</3|\^_?\^|-_-|T_T)(?![\p{L}\p{N}])"""
    )
    private val lineBullet = Regex("""(?m)^\s*(?:[-*•▪◦‣]|\d+[.)])\s+""")
    private val heading = Regex("""(?m)^\s*#{1,6}\s*""")
    private val quote = Regex("""(?m)^\s*>+\s?""")
    private val spaces = Regex("""[ \t]{2,}""")
    private val blankLines = Regex("""\n{2,}""")

    // TextToSpeech tek seferde yaklaşık 4000 karakter okuyabilir.
    private const val MAX_CHARS = 3800

    fun clean(raw: String): String {
        var t = raw
        t = fencedCode.replace(t, "\n")
        t = unclosedFence.replace(t, "\n")
        t = url.replace(t, "bağlantı")
        t = emoticon.replace(t, "")
        t = stripSymbols(t)
        t = lineBullet.replace(t, "")
        t = heading.replace(t, "")
        t = quote.replace(t, "")
        t = t.replace("**", "").replace("__", "").replace("*", "").replace("`", "").replace("~", "")
        t = t.replace("->", ", ").replace("=>", ", ").replace("→", ", ").replace("|", ", ")
        t = t.replace("•", ", ")
        t = spaces.replace(t, " ")
        t = blankLines.replace(t, "\n")
        t = t.trim()
        if (t.length > MAX_CHARS) {
            val cut = t.take(MAX_CHARS)
            val end = maxOf(cut.lastIndexOf('.'), cut.lastIndexOf('\n'))
            t = if (end > MAX_CHARS / 2) cut.substring(0, end + 1) else cut
        }
        return t
    }

    private fun stripSymbols(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (!isDecoration(cp)) sb.appendCodePoint(cp)
        }
        return sb.toString()
    }

    private fun isDecoration(cp: Int): Boolean {
        if (cp == 0xB0) return false // ° derece işareti okunsun
        if (cp == 0x200D || cp == 0xFE0F || cp == 0x20E3 || cp == 0x200B) return true
        if (cp in 0x1F000..0x1FAFF) return true // emoji bloğu (öpücük, yüzler, bayraklar...)
        if (cp in 0x2600..0x27BF) return true   // ☺ ❤ ✓ ✔ gibi semboller
        if (cp in 0x2B00..0x2BFF) return true   // ⭐ ⬆ gibi semboller
        return Character.getType(cp) == Character.OTHER_SYMBOL.toInt()
    }
}
