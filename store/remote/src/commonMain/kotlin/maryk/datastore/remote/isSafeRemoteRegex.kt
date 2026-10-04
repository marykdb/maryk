package maryk.datastore.remote

/**
 * Accept a bounded grammar for full-string matches: consuming character atoms and
 * at most one repetition. No groups or alternatives can multiply backtracking paths.
 * This guarantee does not apply to unanchored find/containsMatchIn calls.
 */
internal fun Regex.isSafeRemoteRegex(): Boolean {
    val pattern = pattern
    if (pattern.length > 256) return false
    var position = 0
    var repetitions = 0
    var canRepeat = false

    fun readEscape(): Boolean {
        if (++position >= pattern.length) return false
        val escaped = pattern[position++]
        return escaped in "dDsSwWnrtf\\.^$|?*+()[]{}-&"
    }

    while (position < pattern.length) {
        when (pattern[position]) {
            '^' -> {
                if (position != 0) return false
                position++
                canRepeat = false
            }
            '$' -> {
                if (position != pattern.lastIndex) return false
                position++
                canRepeat = false
            }
            '\\' -> {
                if (!readEscape()) return false
                canRepeat = true
            }
            '[' -> {
                position++
                if (position < pattern.length && pattern[position] == '^') position++
                var atoms = 0
                while (position < pattern.length && pattern[position] != ']') {
                    when (pattern[position]) {
                        '[', '&' -> return false
                        '\\' -> if (!readEscape()) return false
                        else -> position++
                    }
                    atoms++
                }
                if (atoms == 0 || position >= pattern.length) return false
                position++
                canRepeat = true
            }
            '*', '+', '?' -> {
                if (!canRepeat || ++repetitions > 1) return false
                position++
                canRepeat = false
            }
            '(', ')', '|', '{', '}', ']' -> return false
            else -> {
                position++
                canRepeat = true
            }
        }
    }
    return true
}
