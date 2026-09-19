package maryk.yaml

import kotlin.test.Test

class CommentReaderTest {
    @Test
    fun readComment() {
        createYamlReader("# Comment").apply {
            assertEndDocument()
        }
    }

    @Test
    fun readCommentWithDirective() {
        createYamlReader("""
        |%YAML 1.2
        |# Comment
        |---
        """.trimMargin()).apply {
            assertEndDocument()
        }
    }

    @Test
    fun readMultiLineComment() {
        createYamlReader("""
        |  # Comment
        |  # Line 2
        """.trimMargin()).apply {
            assertEndDocument()
        }
    }
}
