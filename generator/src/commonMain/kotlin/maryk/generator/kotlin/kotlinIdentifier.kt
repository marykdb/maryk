package maryk.generator.kotlin

private val kotlinKeywords = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null",
    "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "val", "var", "when", "while",
    "by", "catch", "constructor", "delegate", "dynamic", "field", "file", "finally", "get", "import", "init", "param", "property",
    "receiver", "setparam", "typeof", "where",
)

internal fun String.kotlinIdentifier(): String {
    require(isNotEmpty()) { "Kotlin identifiers cannot be empty" }
    require(none { it == '`' || it == '\r' || it == '\n' }) { "Kotlin identifiers cannot contain backticks or line breaks: $this" }
    return if (matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) && this !in kotlinKeywords) this else "`$this`"
}
