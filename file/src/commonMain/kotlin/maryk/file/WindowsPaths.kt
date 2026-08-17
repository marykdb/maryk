package maryk.file

/** Returns each Windows parent directory that may be created, excluding drive and UNC share roots. */
internal fun windowsParentDirectories(path: String): List<String> {
    val separatorIndex = path.lastIndexOfAny(charArrayOf('\\', '/'))
    if (separatorIndex <= 0) return emptyList()
    val parent = path.substring(0, separatorIndex).replace('/', '\\')
    if (parent.isEmpty()) return emptyList()

    val root: String
    val remainder: String
    when {
        parent.startsWith("\\\\") -> {
            val rootSegments = parent.removePrefix("\\\\").split('\\').filter(String::isNotEmpty)
            if (rootSegments.size < 2) return emptyList()
            root = "\\\\${rootSegments[0]}\\${rootSegments[1]}"
            remainder = rootSegments.drop(2).joinToString("\\")
        }
        parent.length >= 2 && parent[1] == ':' -> {
            root = parent.substring(0, 2)
            remainder = parent.removePrefix(root).trimStart('\\')
        }
        parent.startsWith("\\") -> {
            root = "\\"
            remainder = parent.trimStart('\\')
        }
        else -> {
            root = ""
            remainder = parent
        }
    }

    val directories = mutableListOf<String>()
    var current = root
    for (segment in remainder.split('\\').filter(String::isNotEmpty)) {
        current = when {
            current.isEmpty() -> segment
            current == "\\" -> "\\$segment"
            current.endsWith("\\") -> "$current$segment"
            else -> "$current\\$segment"
        }
        directories += current
    }
    return directories
}
