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

    private fun bottomRow(switchKey: Key, extra: Key = Key(",", popup = listOf("…", ";", ":"))) = listOf(
        switchKey,
        extra,
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

    /** Letras con `@` en lugar de la coma, para campos de email. */
    val email: List<List<Key>> = letters.dropLast(1) +
        listOf(bottomRow(Key("?123", KeyType.SYMBOLS, weight = 1.5f), Key("@", popup = listOf(",", "_", "-"))))

    /** Letras con `/` en lugar de la coma, para URLs. */
    val url: List<List<Key>> = letters.dropLast(1) +
        listOf(bottomRow(Key("?123", KeyType.SYMBOLS, weight = 1.5f), Key("/", popup = listOf(",", ".com", ":", "-"))))

    /** Teclado numérico 3×4 para campos de número y teléfono. */
    val numpad: List<List<Key>> = listOf(
        listOf(c("1"), c("2"), c("3"), c("-")),
        listOf(c("4"), c("5"), c("6"), c("+", "*", "#")),
        listOf(c("7"), c("8"), c("9"), Key("", KeyType.DELETE)),
        listOf(Key("?123", KeyType.SYMBOLS), c("0", "(", ")"), c(".", ","), Key("", KeyType.ENTER)),
    )
}
