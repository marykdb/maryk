package maryk.core.properties.definitions.index

fun splitStringForIndex(value: String, splitOn: SplitOn): List<String> = when (splitOn) {
    SplitOn.Whitespace -> value
        .splitToSequence(' ', '\t', '\n', '\r')
        .filter { it.isNotEmpty() }
        .toList()
}
