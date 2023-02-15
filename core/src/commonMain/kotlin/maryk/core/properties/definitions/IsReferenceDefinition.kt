package maryk.core.properties.definitions

import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/** Interface for property definitions containing references to data objects of model [DM] and context [CX]. */
interface IsReferenceDefinition<DM : IsRootDataModel<*>, P: PropertyDefinitions, CX : IsPropertyContext> :
    IsComparableDefinition<Key<DM>, IsPropertyContext>,
    IsSerializableFixedBytesEncodable<Key<DM>, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<Key<DM>>,
    HasDefaultValueDefinition<Key<DM>> {
    val dataModel: DM

    override fun calculateStorageByteLength(value: Key<DM>)=
        super.calculateStorageByteLength(value)

    // Overridden because the compiler has issues finding this method in the override
    override fun calculateTransportByteLengthWithKey(
        index: UInt,
        value: Key<DM>,
        cacher: WriteCacheWriter,
        context: IsPropertyContext?,
    ): Int = super<IsComparableDefinition>.calculateTransportByteLengthWithKey(index, value, cacher, context)

    // Overridden because the compiler has issues finding this method in the override
    override fun writeTransportBytesWithKey(
        index: UInt,
        value: Key<DM>,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: IsPropertyContext?,
    ) {
        super<IsComparableDefinition>.writeTransportBytesWithKey(index, value, cacheGetter, writer, context)
    }

    override fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        addIncompatibilityReason: ((String) -> Unit)?
    ): Boolean {
        var compatible = super<IsComparableDefinition>.compatibleWith(definition, addIncompatibilityReason)

        (definition as? IsReferenceDefinition<*, *, *>)?.let {
            if (definition.dataModel.name != this.dataModel.name || definition.dataModel.keyDefinition != this.dataModel.keyDefinition) {
                addIncompatibilityReason?.invoke("Data models are not the same comparing reference properties: $dataModel != ${definition.dataModel}")
                compatible = false
            }
        }

        return compatible
    }
}
