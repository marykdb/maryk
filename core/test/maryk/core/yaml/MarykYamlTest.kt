package maryk.core.yaml

import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.index.IndexKeyPartType
import maryk.json.JsonToken.EndArray
import maryk.json.JsonToken.EndDocument
import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.StartArray
import maryk.json.JsonToken.StartObject
import maryk.json.JsonToken.Value
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.expect

class MarykYamlTest {
    @Test
    fun readMarykTags() {
        MarykYamlModelReader(
        """
         - !Boolean { k1: v1 }
         - !String { k2: v2 }
         - !UUIDv4
         - !UUIDv7
         - !Ref test
        """.trimIndent()
        ).apply {
            assertIs<StartArray>(nextToken())

            expect(PropertyDefinitionType.Boolean) {
                assertIs<StartObject>(nextToken()).type
            }

            expect("k1") {
                assertIs<FieldName>(nextToken()).value
            }

            expect("v1") {
                assertIs<Value<*>>(nextToken()).value
            }

            assertIs<EndObject>(nextToken())

            expect(PropertyDefinitionType.String) {
                assertIs<StartObject>(nextToken()).type
            }

            expect("k2") {
                assertIs<FieldName>(nextToken()).value
            }

            expect("v2") {
                assertIs<Value<*>>(nextToken()).value
            }

            assertIs<EndObject>(nextToken())

            assertIs<Value<*>>(nextToken()).also {
                expect(IndexKeyPartType.UUIDv4) { it.type }
                expect(null) { it.value }
            }

            assertIs<Value<*>>(nextToken()).also {
                expect(IndexKeyPartType.UUIDv7) { it.type }
                expect(null) { it.value }
            }

            assertIs<Value<*>>(nextToken()).also {
                expect(IndexKeyPartType.Reference) { it.type }
                expect("test") { it.value }
            }

            assertIs<EndArray>(nextToken())
            assertIs<EndDocument>(nextToken())
        }
    }
}
