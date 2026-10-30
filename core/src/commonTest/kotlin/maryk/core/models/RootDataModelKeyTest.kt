package maryk.core.models

import maryk.core.exceptions.InvalidDefinitionException
import maryk.core.models.WrongProperties.boolean
import maryk.core.models.WrongProperties.dateTime
import maryk.core.models.WrongProperties.string
import maryk.core.models.definitions.RootDataModelDefinition
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.dateTime
import maryk.core.properties.definitions.fixedBytes
import maryk.core.properties.definitions.geoPoint
import maryk.core.properties.definitions.index.GeoHash
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.multiType
import maryk.test.models.MarykTypeEnum
import maryk.core.properties.exceptions.RequiredException
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.GeoPoint
import maryk.core.properties.types.geoHashBits
import maryk.lib.exceptions.ParseException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal object WrongProperties : DataModel<WrongProperties>() {
    val boolean by boolean(
        index = 1u,
        required = false,
        final = true
    )
    val dateTime by dateTime(2u)
    val string by string(3u)
    val optionalLocation by geoPoint(4u, required = false, final = true)
    val mutableLocation by geoPoint(5u)
    val optionalType by multiType(6u, required = false, typeEnum = MarykTypeEnum)
    val mutableType by multiType(7u, typeEnum = MarykTypeEnum, typeIsFinal = false)
    val immutableType by multiType(8u, typeEnum = MarykTypeEnum, typeIsFinal = true)
}

private object CompositeKeyModel : RootDataModel<CompositeKeyModel>(
    keyDefinition = {
        Multiple(
            CompositeKeyModel.first.ref(),
            CompositeKeyModel.second.ref()
        )
    }
) {
    val first by boolean(index = 1u, final = true)
    val second by boolean(index = 2u, final = true)
}

private object GeoHashCompositeKeyModel : RootDataModel<GeoHashCompositeKeyModel>(
    keyDefinition = {
        Multiple(
            GeoHash(GeoHashCompositeKeyModel.location.ref()),
            GeoHashCompositeKeyModel.category.ref()
        )
    }
) {
    val location by geoPoint(index = 1u, final = true)
    val category by boolean(index = 2u, final = true)
}

private object DirectGeoHashKeyModel : RootDataModel<DirectGeoHashKeyModel>(
    keyDefinition = {
        GeoHash(DirectGeoHashKeyModel.location.ref())
    }
) {
    val location by geoPoint(index = 1u, final = true)
}

private object DirectReversedKeyModel : RootDataModel<DirectReversedKeyModel>(
    keyDefinition = {
        Reversed(DirectReversedKeyModel.flag.ref())
    }
) {
    val flag by boolean(index = 1u, final = true)
}

private object FixedBytesKeyModel : RootDataModel<FixedBytesKeyModel>(
    keyDefinition = {
        FixedBytesKeyModel.payload.ref()
    }
) {
    val payload by fixedBytes(index = 1u, byteSize = 2, final = true)
}

class RootDataModelKeyTest {
    @Test
    fun rejectsOptionalOrMutableGeoHashPrimaryKeySources() {
        for (reference in listOf(WrongProperties.optionalLocation.ref(), WrongProperties.mutableLocation.ref())) {
            assertFailsWith<IllegalArgumentException> {
                RootDataModelDefinition(name = "UnsafeGeoKey", keyDefinition = GeoHash(reference))
            }
        }
    }

    @Test
    fun rejectsOptionalOrMutableMultiTypePrimaryKeyTypes() {
        for (reference in listOf(WrongProperties.optionalType.typeRef(), WrongProperties.mutableType.typeRef())) {
            assertFailsWith<IllegalArgumentException> {
                RootDataModelDefinition(name = "UnsafeTypeKey", keyDefinition = reference)
            }
        }
    }

    @Test
    fun retainsMutableMultiTypeValuesWhenTheirPrimaryKeyTypeIsImmutable() {
        val metadata = RootDataModelDefinition(name = "SafeTypeKey", keyDefinition = WrongProperties.immutableType.typeRef())
        assertEquals(2, metadata.keyByteSize)
    }

    @Test
    fun notAcceptNonRequiredDefinitions() {
        assertFailsWith<IllegalArgumentException> {
            RootDataModelDefinition(
                name = "WrongModel",
                keyDefinition = boolean.ref(),
            )
        }
    }

    @Test
    fun notAcceptNonFinalDefinitions() {
        assertFailsWith<IllegalArgumentException> {
            RootDataModelDefinition(
                name = "WrongModel",
                keyDefinition = Multiple(
                    dateTime.ref()
                ),
            )
        }
    }

    @Test
    fun notAcceptFlexByteDefinitions() {
        assertFailsWith<InvalidDefinitionException> {
            RootDataModelDefinition(
                name = "WrongModel",
                keyDefinition = Multiple(
                    string.ref()
                ),
            )
        }
    }

    @Test
    fun rejectsKeyWithMissingCompositeComponent() {
        val values = CompositeKeyModel.create {
            first with true
        }

        assertFailsWith<RequiredException> {
            CompositeKeyModel.key(values)
        }
    }

    @Test
    fun rejectsKeyWithMissingGeoHashCompositeComponent() {
        val values = GeoHashCompositeKeyModel.create {
            category with true
        }

        assertFailsWith<RequiredException> {
            GeoHashCompositeKeyModel.key(values)
        }
    }

    @Test
    fun encodesDirectGeoHashKey() {
        val point = GeoPoint(52.0907, 5.1214)
        val values = DirectGeoHashKeyModel.create {
            location with point
        }

        assertContentEquals(point.geoHashBits(), DirectGeoHashKeyModel.key(values).bytes)
    }

    @Test
    fun encodesDirectReversedKey() {
        val values = DirectReversedKeyModel.create {
            flag with true
        }

        assertContentEquals(byteArrayOf(0xFE.toByte()), DirectReversedKeyModel.key(values).bytes)
    }

    @Test
    fun rejectsKeyWithMissingDirectGeoHashComponent() {
        val values = DirectGeoHashKeyModel.create {}

        assertFailsWith<RequiredException> {
            DirectGeoHashKeyModel.key(values)
        }
    }

    @Test
    fun rejectsKeyWithMissingDirectReversedComponent() {
        val values = DirectReversedKeyModel.create {}

        val exception = assertFailsWith<RequiredException> {
            DirectReversedKeyModel.key(values)
        }

        assertEquals(DirectReversedKeyModel.flag.ref(), exception.reference)
    }

    @Test
    fun rejectsShortFixedBytesKeyOutput() {
        val values = FixedBytesKeyModel.create {
            payload with Bytes(byteArrayOf(0x01))
        }

        assertFailsWith<ParseException> {
            FixedBytesKeyModel.key(values)
        }
    }

    @Test
    fun rejectsOverlongFixedBytesKeyOutput() {
        val values = FixedBytesKeyModel.create {
            payload with Bytes(byteArrayOf(0x01, 0x02, 0x03))
        }

        assertFailsWith<ParseException> {
            FixedBytesKeyModel.key(values)
        }
    }

    @Test
    fun encodesCompleteCompositeKey() {
        val values = CompositeKeyModel.create {
            first with true
            second with false
        }

        assertContentEquals(byteArrayOf(0x01, 0x00), CompositeKeyModel.key(values).bytes)
    }

    @Test
    fun encodesCompleteGeoHashCompositeKey() {
        val point = GeoPoint(52.0907, 5.1214)
        val values = GeoHashCompositeKeyModel.create {
            location with point
            category with true
        }

        assertContentEquals(
            point.geoHashBits() + byteArrayOf(0x01),
            GeoHashCompositeKeyModel.key(values).bytes,
        )
    }
}
