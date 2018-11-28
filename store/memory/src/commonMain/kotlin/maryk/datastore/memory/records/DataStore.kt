package maryk.datastore.memory.records

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions

internal typealias AnyDataStore = DataStore<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/**
 * An in memory data store containing records and indices
 */
internal class DataStore<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> {
    val records: MutableList<DataRecord<DM, P>> = mutableListOf()
}
