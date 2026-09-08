package maryk.core.properties.references

import maryk.lib.exceptions.ParseException

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
    if (escaped) throw ParseException("Property reference cannot end with an escape")
    add(segment.toString())
}

internal fun String.escapeReferenceSegment() =
    if (isEmpty()) "\\0" else replace("\\", "\\\\").replace(".", "\\.")

internal fun String.unescapeReferenceSegment() = when (this) {
    "\\0" -> ""
    else -> buildString {
        var index = 0
        while (index < this@unescapeReferenceSegment.length) {
            val character = this@unescapeReferenceSegment[index]
            if (character == '\\') {
                if (++index == this@unescapeReferenceSegment.length) {
                    throw ParseException("Property reference contains an incomplete escape")
                }
                append(this@unescapeReferenceSegment[index])
            } else append(character)
            index++
        }
    }
}
