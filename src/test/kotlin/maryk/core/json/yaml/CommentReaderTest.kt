package maryk.core.json.yaml

import maryk.core.json.testForEndJson
import kotlin.test.Test

class CommentReaderTest {
    @Test
    fun read_comment() {
        val reader = createYamlReader("# Comment")
        testForEndJson(reader)
    }

    @Test
    fun read_comment_with_directive() {
        val reader = createYamlReader("""
        |%YAML 1.2
        |# Comment
        |---
        """.trimMargin())
        testForEndJson(reader)
    }

    @Test
    fun read_multi_line_comment() {
        val reader = createYamlReader("""
        |  # Comment
        |  # Line 2
        """.trimMargin())
        testForEndJson(reader)
    }
}