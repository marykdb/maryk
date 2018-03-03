package maryk.core.json.yaml

import maryk.core.json.testForDocumentEnd
import maryk.core.json.testForDocumentStart
import maryk.core.json.testForObjectValue
import kotlin.test.Test

class DocumentReaderTest {
    @Test
    fun readDocument() {
        val reader = createYamlReader("""
        |%YAML 1.2
        |# Comment
        |---
        |  Test
        |---
        | Test2
        |---
        |    Hoho
        |...
        """.trimMargin())
        testForObjectValue(reader, "Test")
        testForDocumentStart(reader)
        testForObjectValue(reader, "Test2")
        testForDocumentStart(reader)
        testForObjectValue(reader, "Hoho")
        testForDocumentEnd(reader)
    }

    @Test
    fun read_comment_with_directive() {
        val reader = createYamlReader("""
        |%YAML 1.2
        |# Comment
        |---
        """.trimMargin())
        testForDocumentEnd(reader)
    }

    @Test
    fun read_multi_line_comment() {
        val reader = createYamlReader("""
        |  # Comment
        |  # Line 2
        """.trimMargin())
        testForDocumentEnd(reader)
    }
}