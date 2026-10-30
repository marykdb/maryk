package maryk.core.properties.definitions

import maryk.core.models.IsRootDataModel
import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.IsDataModelReference
import maryk.core.properties.types.Key

/** Interface for property definitions containing references to data objects of model [DM] and context [CX]. */
interface IsReferenceDefinition<DM : IsRootDataModel, CX : IsPropertyContext> :
    IsComparableDefinition<Key<DM>, IsPropertyContext>,
    IsSerializableFixedBytesEncodable<Key<DM>, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<Key<DM>>,
    HasDefaultValueDefinition<Key<DM>> {
    val dataModel: DM
    val internalDataModelReference: IsDataModelReference<DM>

    override fun calculateStorageByteLength(value: Key<DM>)=
        super.calculateStorageByteLength(value)

    override fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        checkedDataModelNames: MutableList<String>?,
        addIncompatibilityReason: ((String) -> Unit)?
    ): Boolean {
        var compatible = super<IsComparableDefinition>.compatibleWith(definition, checkedDataModelNames, addIncompatibilityReason)

        (definition as? IsReferenceDefinition<*, *>)?.let {
            if (definition.internalDataModelReference.name != this.internalDataModelReference.name) {
                addIncompatibilityReason?.invoke("Data models are not the same comparing reference properties: ${internalDataModelReference.name} != ${definition.internalDataModelReference.name}")
                compatible = false
            } else {
                try {
                    if (definition.byteSize != this.byteSize) {
                        addIncompatibilityReason?.invoke("Key byte size for reference properties should be equal. $byteSize != ${definition.byteSize}")
                        compatible = false
                    }
                } catch (_: DefNotFoundException) {
                    addIncompatibilityReason?.invoke("Cannot establish the stored reference key width for ${internalDataModelReference.name}; an authoritative source schema is required")
                    compatible = false
                }
            }
        }

        return compatible
    }
}
