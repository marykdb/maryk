package maryk.yaml

import maryk.json.MapType
import maryk.json.ValueType
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CreateYamlTokenTest {
    @Test
    fun rejectsMalformedIntegerUnderscores() {
        listOf("1__", "0_", "0x1_").forEach { value ->
            assertFailsWith<InvalidYamlContent> {
                createYamlValueToken(value, ValueType.Int, true)
            }
        }
    }

    @Test
    fun reportsInvalidCalendarDatesAsYamlErrors() {
        assertFailsWith<InvalidYamlContent> {
            createYamlValueToken("2024-02-30", null, true)
        }
    }

    @Test
    fun failOnNullInputOnNotNullType() {
        assertFailsWith<InvalidYamlContent> {
            createYamlValueToken(null, ValueType.String, true)
        }
    }

    @Test
    fun failWithNotValueType() {
        assertFailsWith<InvalidYamlContent> {
            createYamlValueToken("wrong", MapType.Map, true)
        }
    }

    @Test
    fun failOnNotAnInt() {
        assertFailsWith<InvalidYamlContent> {
            createYamlValueToken("1.5", ValueType.Int, true)
        }
    }

    @Test
    fun failOnOverflowingInt() {
        assertFailsWith<InvalidYamlContent> {
            createYamlValueToken("9223372036854775808", ValueType.Int, true)
        }
    }

    @Test
    fun failOnOverflowingFloat() {
        assertFailsWith<InvalidYamlContent> {
            createYamlValueToken("1e9999", ValueType.Float, true)
        }
    }
}
