package maryk.core.properties.definitions

import kotlin.test.Test
import kotlin.test.assertEquals

class JsonStringBuilderTest {
    @Test
    fun buildsJsonStringFromManyWriterChunks() {
        val chunks = List(10_000) { it.toString() }

        val result = buildJsonString { writer ->
            chunks.forEach(writer)
        }

        assertEquals(chunks.joinToString(""), result)
    }
}
