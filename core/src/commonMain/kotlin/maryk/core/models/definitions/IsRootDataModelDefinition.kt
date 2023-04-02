package maryk.core.models.definitions

import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.types.Version

interface IsRootDataModelDefinition<DM : IsValuesPropertyDefinitions> : IsValuesDataModel<DM> {
    val keyDefinition: IsIndexable
    val indices: List<IsIndexable>?

    val version: Version

    val keyByteSize: Int
    val keyIndices: IntArray

    val orderedIndices: List<IsIndexable>?
}
