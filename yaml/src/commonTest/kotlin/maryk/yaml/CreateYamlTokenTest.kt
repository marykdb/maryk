package maryk.yaml

import maryk.json.MapType
import maryk.json.ValueType
import maryk.test.shouldThrow
import kotlin.test.Test

class CreateYamlTokenTest {
    @Test
    fun failOnNullInputOnNotNullType() {
        shouldThrow<InvalidYamlContent> {
            createYamlValueToken(null, ValueType.String, true)
        }
    }

    @Test
    fun failWithNotValueType() {
        shouldThrow<InvalidYamlContent> {
            createYamlValueToken("wrong", MapType.Map, true)
        }
    }

    @Test
    fun failOnNotAnInt() {
        shouldThrow<InvalidYamlContent> {
            createYamlValueToken("1.5", ValueType.Int, true)
        }
    }
}
