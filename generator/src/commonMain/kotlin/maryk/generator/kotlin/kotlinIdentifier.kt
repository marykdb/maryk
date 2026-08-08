package maryk.generator.kotlin

private val kotlinKeywords = setOf(
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is", "null",
    "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "val", "var", "when", "while"
)

internal fun String.kotlinIdentifier(): String {
    require('`' !in this) { "Kotlin identifiers cannot contain backticks: $this" }
    return if (matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) && this !in kotlinKeywords) this else "`$this`"
}
