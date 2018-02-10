package maryk.core.json.yaml

import maryk.core.json.testForArrayEnd
import maryk.core.json.testForArrayStart
import maryk.core.json.testForArrayValue
import maryk.core.json.testForEndJson
import kotlin.test.Test

class ArrayItemsReaderTest {
    @Test
    fun read_array_items() {
        val reader = createYamlReader("""
            |     - 'test'
            |     - 'hoi'
            |     - "nog een"
        """.trimMargin())
        testForArrayStart(reader)
        testForArrayValue(reader, "test")
        testForArrayValue(reader, "hoi")
        testForArrayValue(reader, "nog een")
        testForArrayEnd(reader)
        testForEndJson(reader)
    }
}