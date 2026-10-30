package maryk.core.properties.references

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.json.JsonReader
import maryk.json.JsonWriter
import maryk.test.models.ComplexModel
import maryk.test.models.TestMarykModel
import maryk.yaml.YamlReader
import maryk.yaml.YamlWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class EmptyReferenceMemberTest {
    @Test
    fun emptyMapKeysAndSetItemsRetainTheirTextIdentity() {
        val mapValue = ComplexModel.ref { mapStringString at "" }
        val mapKey = ComplexModel.ref { mapStringString key "" }
        val setItem = TestMarykModel.ref { setOfString item "" }
        assertEquals("mapStringString.@", mapValue.completeName)
        assertEquals("mapStringString.#", mapKey.completeName)
        assertEquals("setOfString.#", setItem.completeName)
        assertNotEquals<AnyPropertyReference>(mapValue, mapKey)
        for ((model, reference) in listOf(ComplexModel to mapValue, ComplexModel to mapKey, TestMarykModel to setItem)) {
            assertEquals(reference, model.getPropertyReferenceByName(reference.completeName))
            val definition = ContextualPropertyReferenceDefinition<IsPropertyContext>(contextualResolver = { model })
            val json = buildString { definition.writeJsonValue(reference, JsonWriter(writer = ::append), null) }
            val jsonReader = JsonReader(json)
            jsonReader.nextToken()
            assertEquals(reference, definition.readJson(jsonReader, null))
            val yaml = buildString { definition.writeJsonValue(reference, YamlWriter(writer = ::append), null) }
            val yamlReader = YamlReader(yaml)
            yamlReader.nextToken()
            assertEquals(reference, definition.readJson(yamlReader, null))
        }
    }
}
