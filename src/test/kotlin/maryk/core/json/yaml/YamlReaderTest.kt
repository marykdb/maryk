package maryk.core.json.yaml

import maryk.core.json.JsonToken
import maryk.test.shouldBe
import kotlin.test.Test
import kotlin.test.fail

internal fun createYamlReader(yaml: String): YamlReader {
    val input = yaml
    var index = 0

    val reader = YamlReader {
        val b = input[index].also {
            // JS platform returns a 0 control char when nothing can be read
            if (it == '\u0000') {
                throw Throwable("0 char encountered")
            }
        }
        index++
        b
    }
    return reader
}

class YamlReaderTest {
    @Test
    fun read_single_quote() {
        val reader = createYamlReader("'te''st\"'")

        reader.nextToken().apply {
            if (this is JsonToken.ObjectValue) {
                this.value shouldBe "te'st\""
            } else { fail("$this should be object value") }
        }

        reader.nextToken().apply {
            if (this !is JsonToken.EndJSON) {
                fail("$this should be object value")
            }
        }
    }
}