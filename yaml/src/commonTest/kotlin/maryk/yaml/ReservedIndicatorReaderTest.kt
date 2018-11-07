package maryk.yaml

import kotlin.test.Test

class ReservedIndicatorReaderTest {
    @Test
    fun failOnReservedChars() {
        createYamlReader("`test").apply {
            assertInvalidYaml()
        }

        createYamlReader("@test").apply {
            assertInvalidYaml()
        }
    }

    @Test
    fun failOnReservedSignInMap() {
        createYamlReader("test: @test").apply {
            assertStartObject()
            assertFieldName("test")
            assertInvalidYaml()
        }
    }

    @Test
    fun failOnReservedSignInArray() {
        createYamlReader(" - `test").apply {
            assertStartArray()
            assertInvalidYaml()
        }
    }
}
