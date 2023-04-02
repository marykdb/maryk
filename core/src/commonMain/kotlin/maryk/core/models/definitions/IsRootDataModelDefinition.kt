package maryk.core.models.definitions

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.types.Version

interface IsRootDataModelDefinition<DM : IsValuesDataModel> : IsValuesDataModelDefinition<DM> {
    val keyDefinition: IsIndexable
    val indices: List<IsIndexable>?

    val version: Version

    val keyByteSize: Int
    val keyIndices: IntArray

    val orderedIndices: List<IsIndexable>?
}
