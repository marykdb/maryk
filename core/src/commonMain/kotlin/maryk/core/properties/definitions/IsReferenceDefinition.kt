package maryk.core.properties.definitions

import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.types.Key

/** Interface for property definitions containing references to data objects of model [DM] and context [CX]. */
interface IsReferenceDefinition<DM : IsRootDataModel<*>, P: PropertyDefinitions, CX : IsPropertyContext> :
    IsComparableDefinition<Key<DM>, IsPropertyContext>,
    IsSerializableFixedBytesEncodable<Key<DM>, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<Key<DM>>,
    HasDefaultValueDefinition<Key<DM>> {
    val dataModel: DM

    override fun calculateStorageByteLength(value: Key<DM>)=
        super.calculateStorageByteLength(value)
}
