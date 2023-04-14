package maryk.core.models

import maryk.core.models.definitions.ValueDataModelDefinition
import maryk.core.models.serializers.IsValueDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.types.ValueDataObject
import maryk.core.values.ObjectValues

/**
 * Interface for ValueDataModels.
 * Contains ValueDataModel specific Serializer and Meta definition.
 * Also contains method to create bytes from passed ObjectValues based on this ValueDataModel.
 */
interface IsValueDataModel<DO: ValueDataObject, DM: IsObjectDataModel<DO>>:
    IsTypedObjectDataModel<DO, DM, IsPropertyContext, IsPropertyContext>,
    IsStorableDataModel<DO> {
    override val Serializer: IsValueDataModelSerializer<DO, DM>
    override val Meta: ValueDataModelDefinition

    /** Creates bytes for given [values] */
    fun toBytes(values: ObjectValues<DO, DM>): ByteArray {
        val bytes = ByteArray(this.Serializer.byteSize)
        var offset = 0

        this.forEachIndexed { index, it ->
            @Suppress("UNCHECKED_CAST")
            val def = it as IsFixedStorageBytesEncodable<in Any>
            def.writeStorageBytes(values(index.toUInt() + 1u)) {
                bytes[offset++] = it
            }

            if (offset < bytes.size) {
                bytes[offset++] = 1 // separator byte
            }
        }

        return bytes
    }
}
