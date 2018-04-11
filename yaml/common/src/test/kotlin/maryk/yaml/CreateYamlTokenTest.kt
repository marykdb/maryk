package maryk.yaml

import maryk.json.MapType
import maryk.json.ValueType
import maryk.test.shouldThrow
import maryk.yaml.InvalidYamlContent
import maryk.yaml.createYamlValueToken
import kotlin.test.Test

class CreateYamlTokenTest {
    @Test
    fun fail_on_null_input_on_not_null_type() {
        shouldThrow<InvalidYamlContent> {
            createYamlValueToken(null, ValueType.String, true)
        }
    }

    @Test
    fun fail_with_not_value_type() {
        shouldThrow<InvalidYamlContent> {
            createYamlValueToken("wrong", MapType.Map, true)
        }
    }

    @Test
    fun fail_on_not_an_int() {
        shouldThrow<InvalidYamlContent> {
            createYamlValueToken("1.5", ValueType.Int, true)
        }
    }
}
