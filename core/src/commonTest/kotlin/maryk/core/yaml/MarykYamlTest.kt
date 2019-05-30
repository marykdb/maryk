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
import maryk.test.assertType
import kotlin.test.Test
import kotlin.test.expect

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
            assertType<StartArray>(nextToken())

            expect(PropertyDefinitionType.Boolean) {
                assertType<StartObject>(nextToken()).type
            }

            expect("k1") {
                assertType<FieldName>(nextToken()).value
            }

            expect("v1") {
                assertType<Value<*>>(nextToken()).value
            }

            assertType<EndObject>(nextToken())

            expect(PropertyDefinitionType.String) {
                assertType<StartObject>(nextToken()).type
            }

            expect("k2") {
                assertType<FieldName>(nextToken()).value
            }

            expect("v2") {
                assertType<Value<*>>(nextToken()).value
            }

            assertType<EndObject>(nextToken())

            assertType<Value<*>>(nextToken()).also {
                expect(IndexKeyPartType.UUID) { it.type }
                expect(null) { it.value }
            }

            assertType<Value<*>>(nextToken()).also {
                expect(IndexKeyPartType.Reference) { it.type }
                expect("test") { it.value }
            }

            assertType<EndArray>(nextToken())
            assertType<EndDocument>(nextToken())
        }
    }
}
