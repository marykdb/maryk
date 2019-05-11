package maryk.generator.kotlin

/** Contains Kotlin code defining aspects of a property */
internal class KotlinForProperty(
    val name: String,
    val index: UInt,
    val altNames: Set<String>?,
    val value: String,
    val assign: String,
    val definition: String,
    val invoke: String
)
