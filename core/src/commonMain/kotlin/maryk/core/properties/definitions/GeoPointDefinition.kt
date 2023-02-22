package maryk.core.properties.definitions

import maryk.core.extensions.bytes.initInt
import maryk.core.extensions.bytes.initLongLittleEndian
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeLittleEndianBytes
import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.wrapper.DefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.properties.types.GeoPoint
import maryk.core.properties.types.toGeoPoint
import maryk.core.protobuf.WireType.BIT_64
import maryk.core.protobuf.WriteCacheReader
import maryk.core.values.SimpleObjectValues

/** Definition for Geographic coordinate properties */
data class GeoPointDefinition(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val default: GeoPoint? = null
) :
    IsSimpleValueDefinition<GeoPoint, IsPropertyContext>,
    IsSerializableFixedBytesEncodable<GeoPoint, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<GeoPoint>,
    HasDefaultValueDefinition<GeoPoint> {
    override val propertyDefinitionType = PropertyDefinitionType.GeoPoint
    override val wireType = BIT_64
    override val byteSize = 8

    override fun readStorageBytes(length: Int, reader: () -> Byte) =
        GeoPoint(
            latitude = initInt(reader),
            longitude = initInt(reader)
        )

    override fun calculateStorageByteLength(value: GeoPoint) = this.byteSize

    override fun writeStorageBytes(value: GeoPoint, writer: (byte: Byte) -> Unit) {
        value.latitudeAsInt().writeBytes(writer)
        value.longitudeAsInt().writeBytes(writer)
    }

    override fun writeTransportBytes(
        value: GeoPoint,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: IsPropertyContext?
    ) {
        value.asLong().writeLittleEndianBytes(writer)
    }

    override fun readTransportBytes(
        length: Int,
        reader: () -> Byte,
        context: IsPropertyContext?,
        earlierValue: GeoPoint?
    ) = initLongLittleEndian(reader).let {
        GeoPoint((it shr 32).toInt(), (it and 0xFFFFFFFFL).toInt())
    }

    override fun fromString(string: String) = string.toGeoPoint()

    override fun fromNativeType(value: Any) = when (value) {
        is GeoPoint -> value
        else -> null
    }

    @Suppress("unused")
    object Model : SimpleObjectDataModel<GeoPointDefinition, ObjectPropertyDefinitions<GeoPointDefinition>>(
        properties = object : ObjectPropertyDefinitions<GeoPointDefinition>() {
            val required by boolean(1u, GeoPointDefinition::required, default = true)
            val final by boolean(2u, GeoPointDefinition::final, default = false)
            val default by geoPoint(3u, GeoPointDefinition::default)
        }
    ) {
        override fun invoke(values: SimpleObjectValues<GeoPointDefinition>) = GeoPointDefinition(
            required = values(1u),
            final = values(2u),
            default = values(3u)
        )
    }
}

fun IsValuesPropertyDefinitions.geoPoint(
    index: UInt,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    default: GeoPoint? = null,
    alternativeNames: Set<String>? = null
) = DefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper<GeoPoint, GeoPoint, IsPropertyContext, GeoPointDefinition, Any>(
        index,
        name ?: propName,
        GeoPointDefinition(required, final, default),
        alternativeNames
    )
}

fun <TO: Any, DO: Any> ObjectPropertyDefinitions<DO>.geoPoint(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    default: GeoPoint? = null,
    alternativeNames: Set<String>? = null
): ObjectDefinitionWrapperDelegateLoader<FixedBytesDefinitionWrapper<GeoPoint, TO, IsPropertyContext, GeoPointDefinition, DO>, DO, IsPropertyContext> =
    geoPoint(index, getter, name, required, final, default, alternativeNames, toSerializable = null)

fun <TO: Any, DO: Any, CX: IsPropertyContext> ObjectPropertyDefinitions<DO>.geoPoint(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    default: GeoPoint? = null,
    alternativeNames: Set<String>? = null,
    toSerializable: (Unit.(TO?, CX?) -> GeoPoint?)? = null,
    fromSerializable: (Unit.(GeoPoint?) -> TO?)? = null,
    shouldSerialize: (Unit.(Any) -> Boolean)? = null,
    capturer: (Unit.(CX, GeoPoint) -> Unit)? = null
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    FixedBytesDefinitionWrapper(
        index,
        name ?: propName,
        GeoPointDefinition(required, final, default),
        alternativeNames,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
