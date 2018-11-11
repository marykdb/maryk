package maryk.core.processors.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.memory.records.DataRecord
import maryk.core.processors.datastore.memory.records.DeleteState.Deleted
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.IsFetchRequest

internal fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> IsFetchRequest<DM, *>.filterData(
    dataRecord: DataRecord<DM, P>
) = when {
    this.filterSoftDeleted && dataRecord.isDeleted is Deleted -> true
    else -> false
}
