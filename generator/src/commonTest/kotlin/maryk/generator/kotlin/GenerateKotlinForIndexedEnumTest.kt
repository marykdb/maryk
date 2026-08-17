package maryk.generator.kotlin

import maryk.test.models.Option
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

val generatedKotlinForIndexedEnum = """
package maryk.test.models

import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl

sealed class Option(
    index: UInt,
    alternativeNames: Set<String>? = null
) : IndexedEnumImpl<Option>(index, alternativeNames) {
    object V1: Option(1u)
    object V2: Option(2u, setOf("VERSION2"))
    object V3: Option(3u, setOf("VERSION3"))

    class UnknownOption(index: UInt, override val name: String): Option(index)

    companion object : IndexedEnumDefinition<Option>(
        Option::class,
        values = { listOf(V1, V2, V3) },
        reservedIndices = listOf(4u),
        reservedNames = listOf("V4"),
        unknownCreator = ::UnknownOption
    )
}
""".trimIndent()

class GenerateKotlinForIndexedEnumTest {
    @Test
    fun generateKotlinForOption() {
        val output = buildString {
            Option.generateKotlin("maryk.test.models") {
                append(it)
            }
        }

        assertEquals(generatedKotlinForIndexedEnum, output)
    }

    @Test
    fun rejectsCasesCollidingWithGeneratedEnumHelpers() {
        for ((definition, caseName, expectedHelper) in listOf(
            Triple(CompanionCollision, "Companion", "Companion"),
            Triple(UnknownHelperCollision, "UnknownUnknownHelperCollision", "UnknownUnknownHelperCollision"),
        )) {
            val exception = assertFailsWith<IllegalArgumentException> {
                definition.generateKotlin("maryk.test.models") {}
            }

            assertEquals(
                "Kotlin enum ${definition.name} case $caseName collides with generated helper $expectedHelper",
                exception.message,
            )
        }
    }
}

private sealed class CompanionCollision(index: UInt, override val name: String) :
    IndexedEnumImpl<CompanionCollision>(index) {
    object Value : CompanionCollision(1u, "Companion")

    companion object : IndexedEnumDefinition<CompanionCollision>(
        CompanionCollision::class,
        values = { listOf(Value) },
    )
}

private sealed class UnknownHelperCollision(index: UInt, override val name: String) :
    IndexedEnumImpl<UnknownHelperCollision>(index) {
    object Value : UnknownHelperCollision(1u, "UnknownUnknownHelperCollision")

    companion object : IndexedEnumDefinition<UnknownHelperCollision>(
        UnknownHelperCollision::class,
        values = { listOf(Value) },
    )
}
