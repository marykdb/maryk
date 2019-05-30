package maryk.yaml

import maryk.json.MapType
import maryk.json.ValueType
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CreateYamlTokenTest {
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
}
