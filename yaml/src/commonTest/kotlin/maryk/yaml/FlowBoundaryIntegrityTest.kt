package maryk.yaml

import maryk.json.IsJsonLikeReader
import maryk.json.JsonToken.EndDocument
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.fail

class FlowBoundaryIntegrityTest {
    @Test
    fun anOpeningFlowDelimiterAtEndOfInputCannotBecomeAnEmptyDocumentOrValue() {
        for (document in listOf(
            "{", "[", "[{", "{inner: [", "- {", "value: [", "value: {", "[a, [", "{a: [b, {",
            "!!map {", "!!seq [", "[!!map {", "&entry {", "&entry [",
        )) {
            for (reader in readers(document)) {
                assertFailsWith<InvalidYamlContent>(document) { consumeDocument(reader) }
            }
        }
    }

    @Test
    fun completeFlowCollectionsStillCloseAtEndOfInput() {
        for (document in listOf(
            "{}", "[]", "[{}]", "{inner: []}", "- {}", "value: []", "value: {}", "[a, []]", "{a: [b, {}]}",
            "!!map {}", "!!seq []", "[!!map {}]", "&entry {}", "&entry []",
        )) {
            readers(document).forEach(::consumeDocument)
        }
    }

    private fun readers(document: String): List<IsJsonLikeReader> {
        var index = 0
        return listOf(YamlReader(document), YamlReader(reader = { document.getOrNull(index++) }))
    }

    private fun consumeDocument(reader: IsJsonLikeReader) {
        repeat(100) { if (reader.nextToken() == EndDocument) return }
        fail("Flow reader did not finish within the fixture's token bound")
    }
}
