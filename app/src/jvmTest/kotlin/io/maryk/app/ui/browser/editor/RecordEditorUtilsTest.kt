package io.maryk.app.ui.browser.editor

import maryk.core.models.RootDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.ObjectDataModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.FixedBytesDefinition
import maryk.core.properties.definitions.FlexBytesDefinition
import maryk.core.properties.definitions.GeoPointDefinition
import maryk.core.properties.definitions.IsEmbeddedObjectDefinition
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.ReferenceDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.changes.IncMapChange
import maryk.core.query.pairs.with
import maryk.core.properties.types.GeoPoint
import maryk.core.values.ObjectValues
import maryk.core.values.Values
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RecordEditorUtilsTest {
    @Test
    fun defaultMapKeyReturnsNullForExhaustedEnumKeys() {
        val definition = EnumDefinition(enum = KeyOption)
        @Suppress("UNCHECKED_CAST")
        val result = defaultMapKey(
            definition = definition as IsSimpleValueDefinition<Any, *>,
            existingKeys = setOf(KeyOption.A, KeyOption.B),
        )

        assertNull(result)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun defaultMapKeyFindsNextDateTimeCandidates() {
        val dateDefinition = DateDefinition(
            minValue = LocalDate(2024, 1, 1),
            maxValue = LocalDate(2024, 1, 3),
        ) as IsSimpleValueDefinition<Any, *>
        val dateTimeSecondsDefinition = DateTimeDefinition(
            precision = TimePrecision.SECONDS,
            minValue = LocalDateTime(2024, 1, 1, 0, 0, 0),
            maxValue = LocalDateTime(2024, 1, 1, 0, 0, 2),
        ) as IsSimpleValueDefinition<Any, *>
        val dateTimeNanosDefinition = DateTimeDefinition(
            precision = TimePrecision.NANOS,
            minValue = LocalDateTime(2024, 1, 1, 0, 0, 0, 0),
            maxValue = LocalDateTime(2024, 1, 1, 0, 0, 0, 2),
        ) as IsSimpleValueDefinition<Any, *>

        assertEquals(
            LocalDate(2024, 1, 2),
            defaultMapKey(dateDefinition, setOf(LocalDate(2024, 1, 1))),
        )
        assertEquals(
            LocalDateTime(2024, 1, 1, 0, 0, 1),
            defaultMapKey(dateTimeSecondsDefinition, setOf(LocalDateTime(2024, 1, 1, 0, 0, 0))),
        )
        assertEquals(
            LocalDateTime(2024, 1, 1, 0, 0, 0, 1),
            defaultMapKey(dateTimeNanosDefinition, setOf(LocalDateTime(2024, 1, 1, 0, 0, 0, 0))),
        )
    }

    @Test
    fun defaultSetItemPicksUniqueAlternative() {
        val boolDefinition = BooleanDefinition()
        val stringDefinition = StringDefinition()

        assertEquals(true, defaultSetItem(boolDefinition, setOf(false)))
        assertNull(defaultSetItem(boolDefinition, setOf(false, true)))
        assertEquals("new", defaultSetItem(stringDefinition, setOf("")))
    }

    @Test
    fun defaultSetItemFindsNextDateTimeCandidates() {
        val dateDefinition = DateDefinition(
            minValue = LocalDate(2024, 1, 1),
            maxValue = LocalDate(2024, 1, 3),
        )
        val timeDefinition = TimeDefinition(
            precision = TimePrecision.SECONDS,
            minValue = LocalTime(0, 0, 0),
            maxValue = LocalTime(0, 0, 2),
        )
        val dateTimeDefinition = DateTimeDefinition(
            precision = TimePrecision.SECONDS,
            minValue = LocalDateTime(2024, 1, 1, 0, 0, 0),
            maxValue = LocalDateTime(2024, 1, 1, 0, 0, 2),
        )

        assertEquals(
            LocalDate(2024, 1, 2),
            defaultSetItem(dateDefinition, setOf(LocalDate(2024, 1, 1))),
        )
        assertEquals(
            LocalTime(0, 0, 1),
            defaultSetItem(timeDefinition, setOf(LocalTime(0, 0, 0))),
        )
        assertEquals(
            LocalDateTime(2024, 1, 1, 0, 0, 1),
            defaultSetItem(dateTimeDefinition, setOf(LocalDateTime(2024, 1, 1, 0, 0, 0))),
        )
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun defaultMapKeyReturnsNullWhenDateRangeExhausted() {
        val date = LocalDate(2024, 1, 1)
        val definition = DateDefinition(
            minValue = date,
            maxValue = date,
        ) as IsSimpleValueDefinition<Any, *>

        assertNull(defaultMapKey(definition, setOf(date)))
    }

    @Test
    fun defaultSetItemReturnsNullWhenTimeRangeExhausted() {
        val definition = TimeDefinition(
            precision = TimePrecision.SECONDS,
            minValue = LocalTime(0, 0, 0),
            maxValue = LocalTime(0, 0, 1),
        )

        assertNull(defaultSetItem(definition, setOf(LocalTime(0, 0, 0), LocalTime(0, 0, 1))))
    }

    @Test
    fun stripPropertyPrefixRemovesOnlyKnownPrefix() {
        assertEquals("Required.", stripPropertyPrefix("Property «field» Required."))
        assertEquals("Already clean", stripPropertyPrefix("Already clean"))
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun defaultMapKeySupportsReferenceFixedFlexAndGeoPointKeys() {
        val referenceDefinition = ReferenceDefinition(dataModel = { PathModel }) as IsSimpleValueDefinition<Any, *>
        val fixedBytesDefinition = FixedBytesDefinition(byteSize = 2) as IsSimpleValueDefinition<Any, *>
        val flexBytesDefinition = FlexBytesDefinition() as IsSimpleValueDefinition<Any, *>
        val geoPointDefinition = GeoPointDefinition() as IsSimpleValueDefinition<Any, *>

        val referenceKey = defaultMapKey(referenceDefinition, emptySet())
        val fixedBytesKey = defaultMapKey(fixedBytesDefinition, emptySet())
        val flexBytesKey = defaultMapKey(flexBytesDefinition, emptySet())
        val geoPointKey = defaultMapKey(geoPointDefinition, emptySet())

        assertNotNull(referenceKey)
        assertNotNull(fixedBytesKey)
        assertNotNull(flexBytesKey)
        assertEquals(GeoPoint(0, 0), geoPointKey)
    }

    @Test
    fun defaultValueForDefinitionSupportsReferenceFixedFlexAndGeoPoint() {
        val referenceDefinition = ReferenceDefinition(dataModel = { PathModel })
        val fixedBytesDefinition = FixedBytesDefinition(byteSize = 2)
        val flexBytesDefinition = FlexBytesDefinition()
        val geoPointDefinition = GeoPointDefinition()

        assertNotNull(defaultValueForDefinition(referenceDefinition))
        assertNotNull(defaultValueForDefinition(fixedBytesDefinition))
        assertNotNull(defaultValueForDefinition(flexBytesDefinition))
        assertEquals(GeoPoint(0, 0), defaultValueForDefinition(geoPointDefinition))
    }

    @Test
    fun mergeDateTimeSelectionPreservesSecondAndNanosecond() {
        val result = mergeDateTimeSelection(
            existing = LocalDateTime(2024, 1, 1, 10, 11, 12, 13),
            selectedDate = LocalDate(2024, 2, 3),
            selectedHour = 4,
            selectedMinute = 5,
        )

        assertEquals(LocalDateTime(2024, 2, 3, 4, 5, 12, 13), result)
    }

    @Test
    fun mergeDateTimeSelectionFallsBackToZeroSecondAndNanosecond() {
        val result = mergeDateTimeSelection(
            existing = null,
            selectedDate = LocalDate(2024, 2, 3),
            selectedHour = 4,
            selectedMinute = 5,
        )

        assertEquals(LocalDateTime(2024, 2, 3, 4, 5, 0, 0), result)
    }

    @Test
    fun updateValueClearsOnlyTargetPathErrors() {
        val initialValues = PathModel.create {
            id with 1u
        }
        @Suppress("UNCHECKED_CAST")
        val state = RecordEditState(
            PathModel,
            initialValues as Values<IsRootDataModel>,
            allowFinalEdit = true,
        )

        state.setError("foo", "Invalid format.")
        state.setError("foobar", "Invalid format.")

        state.updateValue(PathModel.foo.ref(), "updated")

        assertNull(state.errorFor("foo"))
        assertEquals("Invalid format.", state.errorFor("foobar"))
    }

    @Test
    fun validateAllIncludesPendingIncMapValues() {
        val initialValues = IncMapModel.create {
            id with 1u
        }
        @Suppress("UNCHECKED_CAST")
        val state = RecordEditState(
            IncMapModel,
            initialValues as Values<IsRootDataModel>,
            allowFinalEdit = true,
        )
        val ref = IncMapModel.incMap.ref(null)
        val path = ref.completeName

        @Suppress("UNCHECKED_CAST")
        state.addIncMapValue(path, ref as IncMapReference<Comparable<Any>, Any, *>, "")

        assertFalse(state.validateAll())
        assertNotNull(state.errorFor("$path.^0.value"))
    }

    @Test
    fun buildChangesKeepsPendingIncMapInsertionOrder() {
        val initialValues = IncMapModel.create {
            id with 1u
        }
        @Suppress("UNCHECKED_CAST")
        val state = RecordEditState(
            IncMapModel,
            initialValues as Values<IsRootDataModel>,
            allowFinalEdit = true,
        )
        val ref = IncMapModel.incMap.ref(null)
        val path = ref.completeName

        @Suppress("UNCHECKED_CAST")
        state.addIncMapValue(path, ref as IncMapReference<Comparable<Any>, Any, *>, "aa")
        state.addIncMapValue(path, ref, "bb")

        val incMapChange = state.buildChanges().filterIsInstance<IncMapChange>().single()
        assertEquals(listOf("aa", "bb"), incMapChange.valueChanges.single().addValues)
    }

    @Test
    fun buildChangesSanitizesPendingIncMapEmbeddedObjectValues() {
        val initialValues = IncMapObjectModel.create {
            id with 1u
        }
        @Suppress("UNCHECKED_CAST")
        val state = RecordEditState(
            IncMapObjectModel,
            initialValues as Values<IsRootDataModel>,
            allowFinalEdit = true,
        )
        val ref = IncMapObjectModel.incMap.ref(null)
        val path = ref.completeName
        @Suppress("UNCHECKED_CAST")
        val pendingValue = createDefaultObjectValues(
            IncMapObjectModel.incMap.definition.valueDefinition as IsEmbeddedObjectDefinition<Any, *, *, *>,
        )

        @Suppress("UNCHECKED_CAST")
        state.addIncMapValue(path, ref as IncMapReference<Comparable<Any>, Any, *>, pendingValue)

        val incMapChange = state.buildChanges().filterIsInstance<IncMapChange>().single()
        val addValue = incMapChange.valueChanges.single().addValues!!.single()

        assertFalse(addValue is ObjectValues<*, *>)
        assertEquals(PendingEntry(), addValue)
    }
}

private sealed class KeyOption(index: UInt) : IndexedEnumImpl<KeyOption>(index) {
    object A : KeyOption(1u)
    object B : KeyOption(2u)
    class Unknown(index: UInt, override val name: String) : KeyOption(index)

    companion object : IndexedEnumDefinition<KeyOption>(
        enumClass = KeyOption::class,
        values = { listOf(A, B) },
        unknownCreator = ::Unknown,
    )
}

private object PathModel : RootDataModel<PathModel>(
    keyDefinition = {
        PathModel.run { id.ref() }
    },
) {
    val id by number(index = 1u, type = UInt32, final = true)
    val foo by string(index = 2u, required = false)
    val foobar by string(index = 3u, required = false)
}

private object IncMapModel : RootDataModel<IncMapModel>(
    keyDefinition = {
        IncMapModel.run { id.ref() }
    },
) {
    val id by number(index = 1u, type = UInt32, final = true)
    val incMap by incrementingMap(
        index = 2u,
        keyNumberDescriptor = UInt32,
        valueDefinition = StringDefinition(minSize = 2u),
        required = false,
    )
}

private data class PendingEntry(
    val text: String = "init",
) {
    companion object : ObjectDataModel<PendingEntry, Companion>(PendingEntry::class) {
        val text by string(
            index = 1u,
            getter = PendingEntry::text,
            default = "init",
        )

        override fun invoke(values: ObjectValues<PendingEntry, Companion>) = PendingEntry(
            text = values(text.index),
        )
    }
}

private object IncMapObjectModel : RootDataModel<IncMapObjectModel>(
    keyDefinition = {
        IncMapObjectModel.run { id.ref() }
    },
) {
    val id by number(index = 1u, type = UInt32, final = true)
    val incMap by incrementingMap(
        index = 2u,
        keyNumberDescriptor = UInt32,
        valueDefinition = EmbeddedObjectDefinition(dataModel = { PendingEntry }),
        required = false,
    )
}
