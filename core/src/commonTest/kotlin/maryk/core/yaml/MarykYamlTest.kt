package maryk.core.yaml

import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.index.IndexKeyPartType
import maryk.json.IsJsonLikeReader
import maryk.json.JsonToken
import maryk.test.shouldBe
import maryk.test.shouldBeOfType
import kotlin.test.Test

fun createMarykYamlModelReader(yaml: String): IsJsonLikeReader {
    var index = 0

    var alreadyRead = ""

    return MarykYamlModelReader {
        val b = yaml[index].also {
            // JS platform returns a 0 control char when nothing can be read
            if (it == '\u0000') {
                throw Throwable("0 char encountered")
            }
        }
        alreadyRead += b
        index++
        b
    }
}

class MarykYamlTest{
    @Test
    fun readMarykTags() {
        createMarykYamlModelReader("""
        |    - !Boolean { k1: v1 }
        |    - !String { k2: v2 }
        |    - !UUID
        |    - !Ref test
        """.trimMargin()).apply {
            shouldBeOfType<JsonToken.StartArray>(nextToken())

            shouldBeOfType<JsonToken.StartObject>(
                nextToken()
            ).type shouldBe PropertyDefinitionType.Boolean

            shouldBeOfType<JsonToken.FieldName>(
                nextToken()
            ).value shouldBe "k1"

            shouldBeOfType<JsonToken.Value<*>>(
                nextToken()
            ).value shouldBe "v1"

            shouldBeOfType<JsonToken.EndObject>(nextToken())

            shouldBeOfType<JsonToken.StartObject>(
                nextToken()
            ).type shouldBe PropertyDefinitionType.String

            shouldBeOfType<JsonToken.FieldName>(
                nextToken()
            ).value shouldBe "k2"

            shouldBeOfType<JsonToken.Value<*>>(
                nextToken()
            ).value shouldBe "v2"

            shouldBeOfType<JsonToken.EndObject>(
                nextToken()
            )

            shouldBeOfType<JsonToken.Value<*>>(
                nextToken()
            ).also {
                it.type shouldBe IndexKeyPartType.UUID
                it.value shouldBe null
            }

            shouldBeOfType<JsonToken.Value<*>>(
                nextToken()
            ).also {
                it.type shouldBe IndexKeyPartType.Reference
                it.value shouldBe "test"
            }

            shouldBeOfType<JsonToken.EndArray>(nextToken())
            shouldBeOfType<JsonToken.EndDocument>(nextToken())
        }
    }
}
