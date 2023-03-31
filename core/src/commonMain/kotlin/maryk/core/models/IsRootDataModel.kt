package maryk.core.models

import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.types.Version

interface IsRootDataModel<P : IsValuesPropertyDefinitions> : IsValuesDataModel<P> {
    val keyDefinition: IsIndexable
    val indices: List<IsIndexable>?

    val version: Version

    val keyByteSize: Int
    val keyIndices: IntArray

    val orderedIndices: List<IsIndexable>?
}
