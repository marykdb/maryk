package maryk.core.properties.definitions.index

fun splitStringForIndex(value: String, splitOn: SplitOn): List<String> = when (splitOn) {
    SplitOn.Whitespace -> value
        .splitToSequence(' ', '\t', '\n', '\r')
        .filter { it.isNotEmpty() }
        .toList()
    SplitOn.WordBoundary -> splitOnWordBoundaries(value)
}

private fun splitOnWordBoundaries(value: String): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()

    fun flush() {
        if (current.isNotEmpty()) {
            parts += current.toString()
            current.clear()
        }
    }

    for (char in value) {
        if (char.isWhitespace() || char in nameBoundaryChars) {
            flush()
        } else {
            current.append(char)
        }
    }
    flush()

    return parts
}

private val nameBoundaryChars = setOf('-', '‐', '‑', '‒', '–', '—')
