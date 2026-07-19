package maryk.core.properties.definitions.index

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.calculateVarIntWithExtraInfoByteSize
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.extensions.bytes.writeVarIntWithExtraInfo
import maryk.core.models.BaseDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.TypedObjectDataModel
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.GeoPoint
import maryk.core.properties.types.geoHashBits
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.DefinitionsConversionContext
import maryk.core.values.IsValuesGetter
import maryk.core.values.ObjectValues

/** Stable geohash-prefix index transform for a [GeoPoint] [reference]. */
data class GeoHash(
    val reference: IsIndexablePropertyReference<GeoPoint>,
    val precisionBits: UInt = 32u,
) : IsIndexable {
    override val indexKeyPartType = IndexKeyPartType.GeoHash
    override val referenceStorageByteArray by lazy { Bytes(toReferenceStorageByteArray()) }

    init {
        require(precisionBits in 1u..52u) { "Geohash precision must be within 1..52 bits" }
    }

    override fun toStorageByteArrays(values: IsValuesGetter): List<ByteArray> =
        reference.getValueOrNull(values)?.let { listOf(it.geoHashBits(precisionBits)) } ?: emptyList()

    override fun calculateStorageByteLengthForIndex(values: IsValuesGetter, keySize: Int?): Int {
        if (reference.getValueOrNull(values) == null) return 0
        val valueLength = byteSize
        return valueLength + valueLength.calculateVarByteLength() + (keySize ?: 0)
    }

    override fun writeStorageBytesForIndex(values: IsValuesGetter, key: ByteArray?, writer: (Byte) -> Unit) {
        val bytes = toStorageByteArrays(values).firstOrNull() ?: return
        bytes.forEach(writer)
        bytes.size.writeVarBytes(writer)
        key?.forEach(writer)
    }

    override fun writeStorageBytes(values: IsValuesGetter, writer: (Byte) -> Unit) {
        toStorageByteArrays(values).firstOrNull()?.forEach(writer)
    }

    override fun calculateReferenceStorageByteLength() =
        reference.calculateReferenceStorageByteLength().let { referenceLength ->
            referenceLength.calculateVarIntWithExtraInfoByteSize() + referenceLength + 1
        }

    override fun writeReferenceStorageBytes(writer: (Byte) -> Unit) {
        reference.calculateReferenceStorageByteLength().writeVarIntWithExtraInfo(
            indexKeyPartType.index.toByte(),
            writer,
        )
        reference.writeReferenceStorageBytes(writer)
        writer(precisionBits.toByte())
    }

    override fun isCompatibleWithModel(dataModel: IsRootDataModel) =
        reference.isCompatibleWithModel(dataModel)

    val byteSize get() = (precisionBits.toInt() + 7) / 8

    internal object Model :
        TypedObjectDataModel<GeoHash, Model, DefinitionsConversionContext, DefinitionsConversionContext>() {
        val reference by contextual(
            index = 1u,
            getter = GeoHash::reference,
            definition = ContextualPropertyReferenceDefinition<DefinitionsConversionContext>(
                contextualResolver = {
                    it?.propertyDefinitions as? BaseDataModel<*>? ?: throw ContextNotFoundException()
                }
            ),
            fromSerializable = {
                @Suppress("UNCHECKED_CAST")
                it as IsIndexablePropertyReference<GeoPoint>
            },
        )
        val precisionBits by number(
            index = 2u,
            getter = GeoHash::precisionBits,
            type = UInt32,
            minValue = 1u,
            maxValue = 52u,
            default = 32u,
        )

        override fun invoke(values: ObjectValues<GeoHash, Model>) = GeoHash(
            reference = values(1u),
            precisionBits = values(2u),
        )
    }
}
