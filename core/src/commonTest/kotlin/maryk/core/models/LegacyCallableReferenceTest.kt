package maryk.core.models

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import maryk.core.properties.references.AnyPropertyReference
import maryk.test.models.SimpleMarykTypeEnum.S1
import maryk.test.models.TestMarykModel
import maryk.test.models.EmbeddedMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LegacyCallableReferenceTest {
    @Suppress("DEPRECATION")
    @Test
    fun deprecationReplacementsRemainCallableInLegacySelectors() {
        assertEquals(
            TestMarykModel { list.refAt(2u) },
            TestMarykModel { list.at(2u)::ref }
        )
        val date = LocalDate(2020, 1, 1)
        val time = LocalTime(12, 0)
        assertEquals(TestMarykModel { set.refAt(date) }, TestMarykModel { set.item(date)::ref })
        assertEquals(TestMarykModel { map.refAt(time) }, TestMarykModel { map.at(time)::ref })
        assertEquals(TestMarykModel { map.refToKey(time) }, TestMarykModel { map.key(time)::ref })
        assertEquals(TestMarykModel { multi.refAtType(S1) }, TestMarykModel { multi.atType(S1)::ref })
        assertEquals(TestMarykModel { multi.simpleRefAtType(S1) }, TestMarykModel { multi.simpleAtType(S1)::ref })
        assertEquals(TestMarykModel { multi.refToType() }, TestMarykModel { multi.type::ref })

        val parent = TestMarykModel.ref { embeddedValues { marykModel } }
        val getter = TestMarykModel.list.at(2u)::ref
        assertEquals(TestMarykModel.list.refAt(2u)(parent), getter(parent))
    }

    @Suppress("DEPRECATION")
    @Test
    fun terminalAliasesRetainNestedParentPathsAndStorageBytes() {
        val parent = TestMarykModel.ref { embeddedValues { marykModel } }
        val date = LocalDate(2020, 1, 1)
        val time = LocalTime(12, 0)
        val pairs: List<Pair<AnyPropertyReference, AnyPropertyReference>> = listOf(
            TestMarykModel(parent) { list.refAt(2u) } to TestMarykModel.ref(parent) { list.at(2u) },
            TestMarykModel(parent) { set.refAt(date) } to TestMarykModel.ref(parent) { set.item(date) },
            TestMarykModel(parent) { map.refAt(time) } to TestMarykModel.ref(parent) { map.at(time) },
            TestMarykModel(parent) { multi.refAtType(S1) } to TestMarykModel.ref(parent) { multi.atType(S1) },
            TestMarykModel(parent) { multi.simpleRefAtType(S1) } to TestMarykModel.ref(parent) { multi.simpleAtType(S1) },
            TestMarykModel(parent) { multi.refToType() } to TestMarykModel.ref(parent) { multi.type }
        )

        for ((legacy, selected) in pairs) {
            assertEquals(legacy.completeName, selected.completeName)
            assertEquals(legacy.toStorageByteArray().toList(), selected.toStorageByteArray().toList())
        }
        assertEquals(
            TestMarykModel(parent) { map.refToKey(time) },
            TestMarykModel.ref(parent) { map.key(time) }
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun callableReferenceSyntaxRemainsAvailable() {
        assertEquals("string", TestMarykModel { string::ref }.completeName)
        assertEquals("embeddedValues.value", TestMarykModel { embeddedValues { value::ref } }.completeName)
        assertEquals("string", TestMarykModel.ref { string::ref }.completeName)
        assertEquals("embeddedValues.value", TestMarykModel.ref { embeddedValues { value::ref } }.completeName)
        val parent = TestMarykModel.ref { embeddedValues }
        assertSame(
            EmbeddedMarykModel.ref(parent) { value },
            EmbeddedMarykModel(parent) { value::ref }
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun wildcardAliasesRemainAvailable() {
        assertSame(TestMarykModel.ref { list.any() }, TestMarykModel.ref { list.refToAny() })
        assertEquals(TestMarykModel.ref { set.any() }, TestMarykModel.ref { set.refToAny() })
        assertSame(TestMarykModel.ref { map.anyKey() }, TestMarykModel.ref { map.refToAnyKey() })
        assertSame(TestMarykModel.ref { map.anyValue() }, TestMarykModel.ref { map.refToAnyValue() })
    }
}
