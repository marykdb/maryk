package maryk.yaml

import kotlin.test.Test

class PlainStringReaderTest {
    @Test
    fun readPlainString() {
        createYamlReader("test").apply {
            assertValue("test")
            assertEndDocument()
        }
    }

    @Test
    fun readPlainStringWithHash() {
        createYamlReader("test#").apply {
            assertValue("test#")
            assertEndDocument()
        }
    }

    @Test
    fun readPlainStringWithComment() {
        createYamlReader("test # ignore this").apply {
            assertValue("test")
            assertEndDocument()
        }
    }

    @Test
    fun readPlainStringWithSameLineBreaks() {
        createYamlReader("""
            |  test
            |  test
        """.trimMargin()).apply {
            assertValue("test test")
            assertEndDocument()
        }
    }

    @Test
    fun readPlainStringWithWrongLineBreaks() {
        createYamlReader("""
            |  test
            | test
        """.trimMargin()).apply {
            assertValue("test")
            assertInvalidYaml()
        }
    }

    @Test
    fun readPlainStringWithLineBreaks() {
        createYamlReader("""
            |  test
            |   test
        """.trimMargin()).apply {
            assertValue("test test")
            assertEndDocument()
        }
    }

    @Test
    fun readPlainStringWithQuestionMark() {
        createYamlReader("?test").apply {
            assertValue("?test")
            assertEndDocument()
        }
    }

    @Test
    fun readPlainStringWithColon() {
        createYamlReader(":test").apply {
            assertValue(":test")
            assertEndDocument()
        }
    }

    @Test
    fun readPlainStringWithDash() {
        createYamlReader("-test").apply {
            assertValue("-test")
            assertEndDocument()
        }
    }

    @Test
    fun readPlainStringInArray() {
        createYamlReader("""
            - test1
            - test2
            - -test3
            - :test4
            - ?test5
        """.trimIndent()).apply {
            assertStartArray()
            assertValue("test1")
            assertValue("test2")
            assertValue("-test3")
            assertValue(":test4")
            assertValue("?test5")
            assertEndArray()
            assertEndDocument()
        }
    }
}
