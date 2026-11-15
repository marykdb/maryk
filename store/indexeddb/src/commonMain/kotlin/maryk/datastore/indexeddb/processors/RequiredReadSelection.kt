package maryk.datastore.indexeddb.processors

import maryk.core.aggregations.Aggregations
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.query.filters.IsFilter
import maryk.datastore.shared.requiredReadSelection as sharedRequiredReadSelection
import maryk.datastore.shared.requiredIndexReadSelection as sharedRequiredIndexReadSelection

internal fun <DM : IsRootDataModel> DM.requiredReadSelection(
    select: RootPropRefGraph<DM>?,
    filter: IsFilter?,
    aggregations: Aggregations?,
    index: IsIndexable? = null,
): RootPropRefGraph<DM>? = sharedRequiredReadSelection(select, filter, aggregations, index)

internal fun <DM : IsRootDataModel> DM.requiredIndexReadSelection(index: IsIndexable): RootPropRefGraph<DM>? =
    sharedRequiredIndexReadSelection(index)
