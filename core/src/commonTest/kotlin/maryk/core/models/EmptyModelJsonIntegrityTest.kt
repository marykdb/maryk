package maryk.core.models

import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.string
import maryk.json.JsonReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

private object JsonEmptyRecord : DataModel<JsonEmptyRecord>()

private object EmptyJsonEnvelope : DataModel<EmptyJsonEnvelope>() {
    val empty by embed(1u, dataModel = { JsonEmptyRecord })
    val tail by string(2u)
}

class EmptyModelJsonIntegrityTest {
    @Test
    fun emptyModelsRequireAnObjectAndItsClosingToken() {
        for (json in listOf("null", "true", "42", "\"text\"", "[]", "[{}]", "{", "{\"extra\":1")) {
            assertFails(json) { JsonEmptyRecord.Serializer.readJson(json) }
            assertFails(json) { JsonEmptyRecord.Serializer.readJson(JsonReader(json)) }
        }
        assertEquals(0, JsonEmptyRecord.Serializer.readJson("{}").size)
    }

    @Test
    fun emptyModelsSkipUnknownFieldsWithTheSameRulesAsOtherModels() {
        for (json in listOf("{\"extra\":1}", "{\"extra\":[1,{\"nested\":true}]}", "{\"extra\":{},\"other\":[]} ")) {
            assertEquals(0, JsonEmptyRecord.Serializer.readJson(json).size)
        }
    }

    @Test
    fun emptyEmbeddedObjectsDoNotConsumeTheirFollowingSibling() {
        val decoded = EmptyJsonEnvelope.Serializer.readJson("""{"empty":{"unknown":[1,{"other":true}]},"tail":"after"}""")
        assertEquals(0, decoded.get { empty }!!.size)
        assertEquals("after", decoded.get { tail })
        for (value in listOf("42", "true", "[]", "[{}]", "\"text\"")) {
            assertFails(value) { EmptyJsonEnvelope.Serializer.readJson("""{"empty":$value,"tail":"after"}""") }
        }
    }
}
