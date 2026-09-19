package maryk.core.properties.references

internal fun String.splitReferenceName() = buildList {
    val segment = StringBuilder()
    var escaped = false
    for (character in this@splitReferenceName) {
        if (escaped) {
            segment.append('\\').append(character)
            escaped = false
        } else when (character) {
            '\\' -> escaped = true
            '.' -> {
                add(segment.toString())
                segment.clear()
            }
            else -> segment.append(character)
        }
    }
    if (escaped) segment.append('\\')
    add(segment.toString())
}

internal fun String.escapeReferenceSegment(): String {
    require(isNotEmpty()) { "Empty reference members have no unambiguous text representation" }
    return replace("\\", "\\\\").replace(".", "\\.")
}

internal fun String.unescapeReferenceSegment() = buildString {
        var index = 0
        while (index < this@unescapeReferenceSegment.length) {
            val character = this@unescapeReferenceSegment[index]
            if (character == '\\' && index + 1 < this@unescapeReferenceSegment.length) {
                val escaped = this@unescapeReferenceSegment[index + 1]
                if (escaped == '\\' || escaped == '.') {
                    append(escaped)
                    index++
                } else append(character)
            } else append(character)
            index++
        }
}
