@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.core.processors.datastore.memory.records

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.PropertyDefinitions

sealed class DataRecordNode {
    abstract val index: UShort
}

data class DataRecordValue<T: Any>(
    override val index: UShort,
    val value: T,
    val version: ULong
): DataRecordNode()

data class DataRecordHistoricValues<T: Any>(
    override val index: UShort,
    val history: List<DataRecordValue<T>>
): DataRecordNode()

internal data class DataRecordValueTreeNode<DM: IsValuesDataModel<P>, P: PropertyDefinitions>(
    override val index: UShort,
    val value: DataRecordValueTree<DM, P>
): DataRecordNode()
