package maryk.datastore.memory

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.DecimalDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.dateTime
import maryk.core.properties.definitions.decimal
import maryk.core.properties.definitions.geoPoint
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.multiType
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.valueObject
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.time
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.types.Decimal
import maryk.core.properties.types.GeoPoint
import maryk.core.properties.types.TimePrecision
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.properties.types.invoke
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.changes.Change
import maryk.core.query.changes.IncMapChange
import maryk.core.query.changes.ListChange
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.change
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.core.query.responses.statuses.ValidationFail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import maryk.test.models.TestValueObject

private enum class ScalarTimeCase : MultiTypeEnum<LocalTime> {
    Clock;
    override val index = 1u
    override val alternativeNames: Set<String>? = null
    override val definition = TimeDefinition()
    companion object : MultiTypeEnumDefinition<ScalarTimeCase>(ScalarTimeCase::class, { entries })
}

private object ScalarNormalizationRecord : RootDataModel<ScalarNormalizationRecord>() {
    val clock by time(1u)
    val instant by dateTime(2u, precision = TimePrecision.MILLIS)
    val location by geoPoint(3u)
    val amount by decimal(4u, scale = 2u)
    val times by list(5u, valueDefinition = TimeDefinition())
    val timeSet by set(6u, valueDefinition = TimeDefinition())
    val amounts by map(7u, keyDefinition = StringDefinition(), valueDefinition = DecimalDefinition(scale = 2u))
    val precisionValue by number(8u, type = Float32)
    val typedClock by multiType(9u, typeEnum = ScalarTimeCase)
    val allocatedTimes by incrementingMap(10u, keyNumberDescriptor = UInt32, valueDefinition = TimeDefinition(), required = false)
    val packed by valueObject(11u, dataModel = TestValueObject)
}

class ScalarStorageNormalizationTest {
    @Test
    fun addsAndChangesHaveTheSamePrecisionAsStoredBytes() = runTest {
        for (history in listOf(false, true)) {
            val store = InMemoryDataStore.open(dataModelsById = mapOf(1u to ScalarNormalizationRecord), keepAllVersions = history)
            try {
                val original = ScalarNormalizationRecord.create {
                    clock with LocalTime(12, 30, 1, 123456789)
                    instant with LocalDateTime(2000, 2, 29, 12, 30, 1, 123456789)
                    location with GeoPoint(0.00000001, -0.00000001)
                    amount with Decimal.parse("1.2")
                    times with listOf(LocalTime(1, 2, 3, 999999999))
                    timeSet with setOf(LocalTime(2, 3, 4, 1))
                    amounts with mapOf("a" to Decimal.parse("3"))
                    precisionValue with 0.1f
                    typedClock with ScalarTimeCase.Clock(LocalTime(3, 4, 5, 123))
                    packed with TestValueObject(1, LocalDateTime(2000, 1, 1, 0, 0, 0, 123), true)
                }
                val added = assertIs<AddSuccess<ScalarNormalizationRecord>>(store.execute(ScalarNormalizationRecord.add(original)).statuses.single())
                assertIs<ValidationFail<ScalarNormalizationRecord>>(store.execute(ScalarNormalizationRecord.add(
                    original.copyWithValue(ScalarNormalizationRecord.precisionValue.index, "unparsed number"),
                )).statuses.single())
                val first = store.execute(ScalarNormalizationRecord.get(added.key)).values.single().values
                assertEquals(LocalTime(12, 30, 1), first { clock })
                assertEquals(LocalDateTime(2000, 2, 29, 12, 30, 1, 123000000), first { instant })
                assertEquals(GeoPoint(0.0, 0.0), first { location })
                assertEquals(2u, first { amount }!!.scale)
                assertEquals(listOf(LocalTime(1, 2, 3)), first { times })
                assertEquals(setOf(LocalTime(2, 3, 4)), first { timeSet })
                assertEquals(2u, first { amounts }!!.getValue("a").scale)
                assertEquals(0.10000000149011612, first { precisionValue }!!.toDouble())
                assertEquals(LocalTime(3, 4, 5), first { typedClock }?.value)
                assertEquals(LocalDateTime(2000, 1, 1, 0, 0), first { packed }!!.dateTime)

                val changedStatus = assertIs<ChangeSuccess<ScalarNormalizationRecord>>(store.execute(ScalarNormalizationRecord.change(added.key.change(
                    Change(ScalarNormalizationRecord.ref { clock } with LocalTime(6, 7, 8, 123)),
                    Change(ScalarNormalizationRecord.ref { amounts } with mapOf("b" to Decimal.parse("5"))),
                    ListChange(ScalarNormalizationRecord.ref { times }.change(addValuesToEnd = listOf(LocalTime(9, 10, 11, 123)))),
                    SetChange(ScalarNormalizationRecord.ref { timeSet }.change(addValues = setOf(LocalTime(10, 11, 12, 123)))),
                    Change(ScalarNormalizationRecord.ref { typedClock } with ScalarTimeCase.Clock(LocalTime(4, 5, 6, 123))),
                    IncMapChange(ScalarNormalizationRecord.ref { allocatedTimes }.change(addValues = listOf(LocalTime(5, 6, 7, 123)))),
                    Change(ScalarNormalizationRecord.ref { packed } with TestValueObject(2, LocalDateTime(2000, 1, 1, 0, 0, 1, 123), false)),
                ))).statuses.single())
                val changed = store.execute(ScalarNormalizationRecord.get(added.key)).values.single().values
                assertEquals(LocalTime(6, 7, 8), changed { clock })
                assertEquals(listOf(LocalTime(1, 2, 3), LocalTime(9, 10, 11)), changed { times })
                assertEquals(setOf(LocalTime(2, 3, 4), LocalTime(10, 11, 12)), changed { timeSet })
                assertEquals(2u, changed { amounts }!!.getValue("b").scale)
                assertEquals(LocalTime(4, 5, 6), changed { typedClock }?.value)
                assertEquals(listOf(LocalTime(5, 6, 7)), changed { allocatedTimes }!!.values.toList())
                assertEquals(LocalDateTime(2000, 1, 1, 0, 0, 1), changed { packed }!!.dateTime)
                val noOp = assertIs<ChangeSuccess<ScalarNormalizationRecord>>(store.execute(ScalarNormalizationRecord.change(added.key.change(
                    Change(ScalarNormalizationRecord.ref { clock } with LocalTime(6, 7, 8, 456)),
                    Change(ScalarNormalizationRecord.ref { times } with listOf(LocalTime(1, 2, 3, 456), LocalTime(9, 10, 11, 456))),
                    Change(ScalarNormalizationRecord.ref { timeSet } with setOf(LocalTime(2, 3, 4, 456), LocalTime(10, 11, 12, 456))),
                    Change(ScalarNormalizationRecord.ref { amounts } with mapOf("b" to Decimal.parse("5.0"))),
                ))).statuses.single())
                assertEquals(changedStatus.version, noOp.version)
                if (history) {
                    assertEquals(first, store.execute(ScalarNormalizationRecord.get(added.key, toVersion = added.version)).values.single().values)
                }
            } finally { store.close() }
        }
    }
}
