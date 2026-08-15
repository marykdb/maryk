package maryk.yaml

import maryk.json.JsonToken.Stopped
import maryk.json.ValueType
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AnchorAndAliasReaderTest {
    @Test
    fun replaysNearLimitAnchorTokensWithQueueSemantics() {
        val itemCount = 20_000
        val yaml = buildString {
            append("[&items [")
            repeat(itemCount) { index ->
                if (index > 0) append(',')
                append("item")
            }
            append("], *items]")
        }
        val reader = YamlReader(
            yaml = yaml,
            aliasLimits = YamlAliasLimits(maxExpandedTokens = itemCount + 2)
        ) as YamlReaderImpl

        var tokenCount = 0
        while (reader.nextToken() !is Stopped) {
            tokenCount++
        }

        assertTrue(tokenCount > itemCount)
        assertTrue(reader.tokenQueueWorkUnits < itemCount * 6L)
    }

    @Test
    fun configuredAliasNameBudgetAcceptsNameAtLimit() {
        YamlReader(
            yaml = "&abc value\n*abc",
            aliasLimits = YamlAliasLimits(maxAliasNameLength = 3)
        ).apply {
            assertValue("value")
            assertValue("value")
            assertEndDocument()
        }
    }

    @Test
    fun configuredAliasNameBudgetRejectsOversizedAnchor() {
        val reader = YamlReader(
            yaml = "&oversized value",
            aliasLimits = YamlAliasLimits(maxAliasNameLength = 3)
        )

        val exception = assertFailsWith<InvalidYamlContent> {
            while (reader.currentToken !is Stopped) {
                reader.nextToken()
            }
        }

        assertContains(exception.message ?: "", "Anchor name length budget exceeded")
    }

    @Test
    fun configuredAliasNameBudgetRejectsOversizedAlias() {
        val reader = YamlReader(
            yaml = "&ok value\n*oversized",
            aliasLimits = YamlAliasLimits(maxAliasNameLength = 3)
        )

        val exception = assertFailsWith<InvalidYamlContent> {
            while (reader.currentToken !is Stopped) {
                reader.nextToken()
            }
        }

        assertContains(exception.message ?: "", "Alias name length budget exceeded")
    }

    @Test
    fun configuredAliasTokenBudgetRejectsLargeReplay() {
        val reader = YamlReader(
            yaml = "[&value [one, two], *value]",
            aliasLimits = YamlAliasLimits(maxExpandedTokens = 3)
        )

        val exception = assertFailsWith<InvalidYamlContent> {
            while (reader.currentToken !is Stopped) {
                reader.nextToken()
            }
        }

        assertContains(exception.message ?: "", "Alias expansion token budget exceeded")
    }

    @Test
    fun configuredAliasCountBudgetRejectsRepeatedAliases() {
        val reader = YamlReader(
            yaml = "[&value item, *value, *value, *value]",
            aliasLimits = YamlAliasLimits(maxAliasCount = 2)
        )

        val exception = assertFailsWith<InvalidYamlContent> {
            while (reader.currentToken !is Stopped) {
                reader.nextToken()
            }
        }

        assertContains(exception.message ?: "", "Alias expansion count budget exceeded")
    }

    @Test
    fun defaultAliasBudgetRejectsNestedAliasExpansion() {
        val yaml = buildString {
            appendLine("- &level0 [value, value]")
            for (level in 1..10) {
                appendLine("- &level$level [*level${level - 1}, *level${level - 1}]")
            }
            append("- *level10")
        }
        val reader = createYamlReader(yaml)

        val exception = assertFailsWith<InvalidYamlContent> {
            while (reader.currentToken !is Stopped) {
                reader.nextToken()
            }
        }

        assertContains(exception.message ?: "", "Alias expansion depth budget exceeded")
    }

    @Test
    fun readsAnchorBeforeFlowSequenceEnd() {
        createYamlReader("[&anchor, *anchor]").apply {
            assertStartArray()
            assertValue(null, ValueType.Null)
            assertValue(null, ValueType.Null)
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun doesNotResolveAnchorsFromPreviousDocument() {
        createYamlReader("--- &anchor value\n--- *anchor").apply {
            assertValue("value")
            assertStartDocument()
            assertInvalidYaml()
        }
    }

    @Test
    fun rejectsDuplicateAnchorsInDocument() {
        createYamlReader("[&anchor first, &anchor second]").apply {
            assertStartArray()
            assertValue("first")
            assertInvalidYaml()
        }
    }

    @Test
    fun anchorsWithValue() {
        createYamlReader("""
        |  - &array alfa
        |  - *array
        """.trimMargin()).apply {
            assertStartArray()
            assertValue("alfa")
            assertValue("alfa")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun anchorsInSequences() {
        createYamlReader("""
        |  - &array [a, b]
        |  - *array
        """.trimMargin()).apply {
            assertStartArray()
            assertStartArray()
            assertValue("a")
            assertValue("b")
            assertEndArray()
            assertStartArray()
            assertValue("a")
            assertValue("b")
            assertEndArray()
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun anchorsComplex() {
        createYamlReader("""
        |  - &complex
        |       test: {a: b}
        |       2: [1,2, {[a]}]
        |       3:
        |           - a
        |  - *complex
        |  - blaat
        """.trimMargin()).apply {

            assertStartArray()

            // twice the same
            for (it in 0..1) {
                assertStartObject()
                assertFieldName("test")
                assertStartObject()
                assertFieldName("a")
                assertValue("b")
                assertEndObject()
                assertFieldName("2")
                assertStartArray()
                assertValue(1, ValueType.Int)
                assertValue(2, ValueType.Int)
                assertStartObject()
                assertStartComplexFieldName()
                assertStartArray()
                assertValue("a")
                assertEndArray()
                assertEndComplexFieldName()
                assertValue(null, ValueType.Null)
                assertEndObject()
                assertEndArray()
                assertFieldName("3")
                assertStartArray()
                assertValue("a")
                assertEndArray()
                assertEndObject()
            }

            assertValue("blaat")
            assertEndArray()
            assertEndDocument()
        }
    }

    @Test
    fun failOnInvalidAnchor() {
        createYamlReader("""
        |  - & [a, b]
        """.trimMargin()).apply {
            assertStartArray()
            assertInvalidYaml()
        }
    }

    @Test
    fun failOnInvalidAlias() {
        createYamlReader("""
        |  - &array a
        |  - *
        """.trimMargin()).apply {
            assertStartArray()
            assertValue("a")
            assertInvalidYaml()
        }
    }

    @Test
    fun failOnUnknownAlias() {
        createYamlReader("""
        |  - &array a
        |  - *unknown
        """.trimMargin()).apply {
            assertStartArray()
            assertValue("a")
            assertInvalidYaml()
        }
    }

    @Test
    fun onlyAnchor() {
        createYamlReader("""
        |  - &anchor
        """.trimMargin()).apply {
            assertStartArray()
            assertValue(null, ValueType.Null)
            assertEndArray()
            assertEndDocument()
        }
    }
}
