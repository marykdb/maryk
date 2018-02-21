package maryk.core.json.yaml

import maryk.core.json.testForArrayStart
import maryk.core.json.testForFieldName
import maryk.core.json.testForObjectStart
import maryk.test.shouldThrow
import kotlin.test.Test

class ReservedIndicatorReaderTest {
    @Test
    fun fail_on_reserved_chars() {
        shouldThrow<InvalidYamlContent> {
            createYamlReader("`test").nextToken()
        }

        shouldThrow<InvalidYamlContent> {
            createYamlReader("@test").nextToken()
        }
    }

    @Test
    fun fail_on_reserved_sign_in_map() {
        val reader = createYamlReader("test: @test")

        testForObjectStart(reader)
        testForFieldName(reader, "test")
        shouldThrow<InvalidYamlContent> {
            reader.nextToken()
        }
    }

    @Test
    fun fail_on_reserved_sign_in_array() {
        val reader = createYamlReader(" - `test")

        testForArrayStart(reader)
        shouldThrow<InvalidYamlContent> {
            reader.nextToken()
        }
    }
}