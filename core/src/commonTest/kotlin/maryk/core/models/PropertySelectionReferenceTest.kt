package maryk.core.models

import maryk.core.properties.references.dsl.item

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import maryk.core.properties.references.dsl.ref as extensionRef
import maryk.core.query.filters.Equals
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.test.models.TestMarykModel
import maryk.test.models.TestMarykObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class PropertySelectionReferenceTest {
    @Test
    fun interfaceTypedModelRetainsExtension() {
        val model: IsDataModel = TestMarykModel
        assertSame(TestMarykModel.ref { string }, model.extensionRef { TestMarykModel.string })
        assertSame(
            TestMarykModel.ref { embeddedValues { value } },
            model.extensionRef { TestMarykModel.embeddedValues { value } }
        )
    }

    @Test
    fun selectScalar() {
        val reference = TestMarykModel.ref { string }
        assertSame(TestMarykModel.string.ref(), reference)
        assertEquals(Equals(TestMarykModel.string.ref() with "value"), Equals(reference with "value"))
    }

    @Test
    fun selectEmbedded() {
        val reference = TestMarykModel.ref { embeddedValues { value } }
        assertEquals("embeddedValues.value", reference.completeName)
        assertSame(TestMarykModel.embeddedValues.definition.dataModel.value.ref(TestMarykModel.embeddedValues.ref()), reference)
    }

    @Test
    fun selectEmbeddedObject() {
        val reference = TestMarykObject.ref { embeddedObject { model { value } } }
        assertEquals("embeddedObject.model.value", reference.completeName)
        assertSame(TestMarykObject.getPropertyReferenceByName(reference.completeName), reference)
    }

    @Test
    fun selectWholeEmbeddedProperty() {
        assertEquals("embeddedValues", TestMarykModel.ref { embeddedValues }.completeName)
    }

    @Test
    fun selectDeeplyEmbedded() {
        val reference = TestMarykModel.ref { embeddedValues { model { model { value } } } }
        assertEquals("embeddedValues.model.model.value", reference.completeName)
    }

    @Test
    fun preservesIndexableAndChangeReferenceTypes() {
        val indexes: List<IsIndexable> = listOf(
            TestMarykModel.ref { string },
            TestMarykModel.ref { embeddedValues { value } }
        )
        assertEquals(2, indexes.size)

        val reference = TestMarykModel.ref { embeddedValues { marykModel { incMap } } }
        val change = reference.change(addValues = listOf("new"))
        assertSame(reference, change.reference)
        assertEquals(listOf("new"), change.addValues)
        assertEquals("embeddedValues.marykModel.incMap", reference.completeName)
    }

    @Test
    fun preservesTerminalNavigationReferenceTypes() {
        val listItem = TestMarykModel.ref { listOfString.at(2u) }
        val mapValue = TestMarykModel.ref { map.at(LocalTime(0, 0)) }
        val mapKey = TestMarykModel.ref { map.key(LocalTime(0, 0)) }
        val setItem = TestMarykModel.ref { set.item(LocalDate(2020, 1, 1)) }
        val typedValue = TestMarykModel.ref { multi.atType(maryk.test.models.SimpleMarykTypeEnum.S1) }
        val type = TestMarykModel.ref { multi.type }

        assertEquals("listOfString.@2", listItem.completeName)
        assertEquals("map.@00:00", mapValue.completeName)
        assertEquals("map.#00:00", mapKey.completeName)
        assertEquals("set.#2020-01-01", setItem.completeName)
        assertEquals("multi.*S1", typedValue.completeName)
        assertEquals("multi.*", type.completeName)
    }
}
