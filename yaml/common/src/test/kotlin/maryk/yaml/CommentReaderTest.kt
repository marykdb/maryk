package maryk.yaml

import kotlin.test.Test

class CommentReaderTest {
    @Test
    fun read_comment() {
        createYamlReader("# Comment").apply {
            assertEndDocument()
        }
    }

    @Test
    fun read_comment_with_directive() {
        createYamlReader("""
        |%YAML 1.2
        |# Comment
        |---
        """.trimMargin()).apply {
            assertEndDocument()
        }
    }

    @Test
    fun read_multi_line_comment() {
        createYamlReader("""
        |  # Comment
        |  # Line 2
        """.trimMargin()).apply {
            assertEndDocument()
        }
    }
}
