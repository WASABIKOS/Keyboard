package com.wasabikos.keyboard

enum class KeyType { CHAR, SHIFT, DELETE, ENTER, SPACE, SYMBOLS, LETTERS, GLOBE, MIC, MORE_SYMBOLS, EMOJI }

data class Key(
    val label: String,
    val type: KeyType = KeyType.CHAR,
    val weight: Float = 1f,
    val popup: List<String> = emptyList(),
) {
    val hint: String? get() = popup.firstOrNull()
}

object Layouts {
    private fun c(label: String, vararg popup: String) = Key(label, popup = popup.toList())

    private fun bottomRow(switchKey: Key) = listOf(
        switchKey,
        Key(",", popup = listOf("…", ";", ":")),
        Key("", KeyType.SPACE, weight = 5f),
        Key(".", popup = listOf("!?", "!", "?", "¡", "¿")),
        Key("", KeyType.ENTER, weight = 1.5f),
    )

    val letters: List<List<Key>> = listOf(
        listOf(
            c("q", "1"), c("w", "2"), c("e", "3", "é", "è", "ë"), c("r", "4"), c("t", "5"),
            c("y", "6"), c("u", "7", "ú", "ü"), c("i", "8", "í"), c("o", "9", "ó"), c("p", "0"),
        ),
        listOf(
            c("a", "á", "à", "ä"), c("s"), c("d"), c("f"), c("g"),
            c("h"), c("j"), c("k"), c("l"), c("ñ", "n"),
        ),
        listOf(
            Key("", KeyType.SHIFT, weight = 1.5f),
            c("z"), c("x"), c("c", "ç"), c("v"), c("b"), c("n", "ñ"), c("m"),
            Key("", KeyType.DELETE, weight = 1.5f),
        ),
        bottomRow(Key("?123", KeyType.SYMBOLS, weight = 1.5f)),
    )

    val symbols: List<List<Key>> = listOf(
        "1234567890".map { c(it.toString()) },
        listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/").map { c(it) },
        listOf(Key("=\\<", KeyType.MORE_SYMBOLS, weight = 1.5f)) +
            listOf("*", "\"", "'", ":", ";", "!", "?").map { c(it) } +
            Key("", KeyType.DELETE, weight = 1.5f),
        bottomRow(Key("ABC", KeyType.LETTERS, weight = 1.5f)),
    )

    val moreSymbols: List<List<Key>> = listOf(
        listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆").map { c(it) },
        listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\").map { c(it) },
        listOf(Key("?123", KeyType.SYMBOLS, weight = 1.5f)) +
            listOf("%", "©", "®", "™", "✓", "[", "]").map { c(it) } +
            Key("", KeyType.DELETE, weight = 1.5f),
        bottomRow(Key("ABC", KeyType.LETTERS, weight = 1.5f)),
    )
}
