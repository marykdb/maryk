package maryk.core.yaml

import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.index.IndexKeyPartType
import maryk.json.IsJsonLikeReader
import maryk.json.JsonToken.EndArray
import maryk.json.JsonToken.EndDocument
import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.StartArray
import maryk.json.JsonToken.StartObject
import maryk.json.JsonToken.Value
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

class MarykYamlTest {
    @Test
    fun readMarykTags() {
        createMarykYamlModelReader(
            """
        |    - !Boolean { k1: v1 }
        |    - !String { k2: v2 }
        |    - !UUID
        |    - !Ref test
        """.trimMargin()
        ).apply {
            shouldBeOfType<StartArray>(nextToken())

            shouldBeOfType<StartObject>(
                nextToken()
            ).type shouldBe PropertyDefinitionType.Boolean

            shouldBeOfType<FieldName>(
                nextToken()
            ).value shouldBe "k1"

            shouldBeOfType<Value<*>>(
                nextToken()
            ).value shouldBe "v1"

            shouldBeOfType<EndObject>(nextToken())

            shouldBeOfType<StartObject>(
                nextToken()
            ).type shouldBe PropertyDefinitionType.String

            shouldBeOfType<FieldName>(
                nextToken()
            ).value shouldBe "k2"

            shouldBeOfType<Value<*>>(
                nextToken()
            ).value shouldBe "v2"

            shouldBeOfType<EndObject>(
                nextToken()
            )

            shouldBeOfType<Value<*>>(
                nextToken()
            ).also {
                it.type shouldBe IndexKeyPartType.UUID
                it.value shouldBe null
            }

            shouldBeOfType<Value<*>>(
                nextToken()
            ).also {
                it.type shouldBe IndexKeyPartType.Reference
                it.value shouldBe "test"
            }

            shouldBeOfType<EndArray>(nextToken())
            shouldBeOfType<EndDocument>(nextToken())
        }
    }
}
