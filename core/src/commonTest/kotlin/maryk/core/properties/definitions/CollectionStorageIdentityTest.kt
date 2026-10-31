package maryk.core.properties.definitions

import kotlinx.datetime.LocalTime
import maryk.core.models.RootDataModel
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.writeMapToStorage
import maryk.core.processors.datastore.writeSetToStorage
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.query.RequestContext
import maryk.core.query.changes.SetChange
import maryk.core.query.changes.change
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.types.GeoPoint
import maryk.core.properties.types.TimePrecision.SECONDS
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64
import maryk.core.protobuf.WriteCache
import maryk.lib.exceptions.ParseException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

private object CollectionIdentityModel : RootDataModel<CollectionIdentityModel>() {
    val doubles by map(1u, keyDefinition = NumberDefinition(type = Float64), valueDefinition = StringDefinition())
    val doubleSet by set(2u, valueDefinition = NumberDefinition(type = Float64))
    val floats by map(3u, keyDefinition = NumberDefinition(type = Float32), valueDefinition = StringDefinition())
    val floatSet by set(4u, valueDefinition = NumberDefinition(type = Float32))
    val first by string(5u)
    val times by set(7u, valueDefinition = TimeDefinition(precision = SECONDS))
    val nested by map(6u, keyDefinition = StringDefinition(), valueDefinition = MapDefinition(
        keyDefinition = TimeDefinition(precision = SECONDS), valueDefinition = StringDefinition(),
    ))
}

class CollectionStorageIdentityTest {
    @Test
    fun numericNanReferencesShareQualifiersWithoutChangingValuePayloads() {
        val double = Double.fromBits(0x7ff8000000000001L)
        val float = Float.fromBits(0x7fc00001)
        val references = listOf<Pair<AnyPropertyReference, AnyPropertyReference>>(
            CollectionIdentityModel.ref { doubles at double } to CollectionIdentityModel.ref { doubles at Double.NaN },
            CollectionIdentityModel.ref { doubleSet item double } to CollectionIdentityModel.ref { doubleSet item Double.NaN },
            CollectionIdentityModel.ref { floats at float } to CollectionIdentityModel.ref { floats at Float.NaN },
            CollectionIdentityModel.ref { floatSet item float } to CollectionIdentityModel.ref { floatSet item Float.NaN },
        )
        for ((reference, canonical) in references) {
            assertContentEquals(canonical.toStorageByteArray(), reference.toStorageByteArray())
            assertContentEquals(reference.toStorageByteArray(), CollectionIdentityModel.getPropertyReferenceByName(reference.completeName).toStorageByteArray())
            val cache = WriteCache()
            val bytes = ByteArray(reference.calculateTransportByteLength(cache))
            var offset = 0
            reference.writeTransportBytes(cache) { bytes[offset++] = it }
            offset = 0
            assertContentEquals(canonical.toStorageByteArray(), CollectionIdentityModel.getPropertyReferenceByBytes(bytes.size, { bytes[offset++] }).toStorageByteArray())
        }
        val values = CollectionIdentityModel.create(setDefaults = false) {
            doubles with mapOf(double to "double")
            doubleSet with setOf(double)
            floats with mapOf(float to "float")
            floatSet with setOf(float)
        }
        val qualifiers = mutableListOf<String>()
        values.writeToStorage { type, qualifier, _, _ -> if (type == Value) qualifiers += qualifier.toHexString() }
        assertEquals(references.map { it.second.toStorageByteArray().toHexString() }.sorted(), qualifiers.sorted())
        val doubleDefinition = NumberDefinition(type = Float64)
        val floatDefinition = NumberDefinition(type = Float32)
        assertEquals(double.toRawBits(), doubleDefinition.fromStorageBytes(doubleDefinition.toStorageBytes(double)).toRawBits())
        assertEquals(float.toRawBits(), floatDefinition.fromStorageBytes(floatDefinition.toStorageBytes(float)).toRawBits())
    }

    @Test
    fun roundedTimeAndGeoPointKeysCannotSilentlyCollapse() {
        val timeMap = MapDefinition(keyDefinition = TimeDefinition(precision = SECONDS), valueDefinition = StringDefinition())
        val times = mapOf(LocalTime(0, 0, 0, 1) to "one", LocalTime(0, 0, 0, 2) to "two")
        assertFailsWith<InvalidValueException> { timeMap.validateWithRef(newValue = times) }
        assertFailsWith<ParseException> { timeMap.calculateTransportByteLengthWithKey(1, times, WriteCache()) }
        var writes = 0
        assertFailsWith<ParseException> { writeMapToStorage(0, null, { _, _, _, _ -> writes++ }, timeMap, times) }
        assertEquals(0, writes)

        val timeSet = SetDefinition(valueDefinition = TimeDefinition(precision = SECONDS))
        assertFailsWith<InvalidValueException> { timeSet.validateWithRef(newValue = times.keys) }
        assertFailsWith<ParseException> { writeSetToStorage(0, {}, { _, _, _, _ -> writes++ }, timeSet, times.keys) }
        assertEquals(0, writes)

        val pointMap = MapDefinition(keyDefinition = GeoPointDefinition(), valueDefinition = StringDefinition())
        val points = mapOf(GeoPoint(0.00000001, 0.0) to "one", GeoPoint(0.00000002, 0.0) to "two")
        assertFailsWith<InvalidValueException> { pointMap.validateWithRef(newValue = points) }
        assertFailsWith<ParseException> { writeMapToStorage(0, null, { _, _, _, _ -> writes++ }, pointMap, points) }
        assertEquals(0, writes)
    }

    @Test
    fun contextualSetChangesStillRejectCollidingScalarMembers() {
        val changes = SetChange(CollectionIdentityModel.ref { times }.change(
            addValues = setOf(LocalTime(0, 0, 0, 1), LocalTime(0, 0, 0, 2)),
        ))
        val context = RequestContext(
            mapOf(CollectionIdentityModel.Meta.name to DataModelReference(CollectionIdentityModel)),
            dataModel = CollectionIdentityModel,
        )
        assertFailsWith<ParseException> { SetChange.Serializer.calculateObjectProtoBufLength(changes, WriteCache(), context) }
    }

    @Test
    fun invalidFloatingMemberTypesRetainStructuredValidationErrors() {
        @Suppress("UNCHECKED_CAST")
        val definition = SetDefinition(valueDefinition = NumberDefinition(type = Float64)) as IsSetDefinition<Any, IsPropertyContext>
        val error = assertFailsWith<ValidationUmbrellaException> {
            definition.validateWithRef(newValue = setOf("not a number"))
        }
        assertIs<InvalidValueException>(error.exceptions.single())
    }

    @Test
    fun nestedCollisionPreflightRunsBeforeAnyStorageCallback() {
        val values = CollectionIdentityModel.create(setDefaults = false) {
            first with "before nested collection"
            nested with mapOf("outer" to mapOf(LocalTime(0, 0, 0, 1) to "one", LocalTime(0, 0, 0, 2) to "two"))
        }
        var writes = 0
        assertFailsWith<ParseException> { values.writeToStorage { _, _, _, _ -> writes++ } }
        assertEquals(0, writes)
    }

    @Test
    fun geoPointSetsUseStorageOrderingWithoutComparableMembers() {
        val definition = SetDefinition(valueDefinition = GeoPointDefinition())
        val points = setOf(GeoPoint(1.0, 2.0), GeoPoint(-1.0, -2.0))
        var values = 0
        writeSetToStorage<IsPropertyDefinition<*>>(0, {}, { type, _, _, _ -> if (type == Value) values++ }, definition, points)
        assertEquals(2, values)
    }
}
