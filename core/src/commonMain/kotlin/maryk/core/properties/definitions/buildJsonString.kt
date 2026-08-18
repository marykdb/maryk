package maryk.core.properties.definitions

internal fun buildJsonString(write: ((String) -> Unit) -> Unit): String = buildString {
    write { append(it) }
}
