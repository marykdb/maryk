package maryk.core.query.requests

import maryk.core.aggregations.Aggregations
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.internalMultiType
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.TypedValue
import maryk.core.query.filters.FilterType
import maryk.core.query.filters.IsFilter
import maryk.core.query.filters.mapOfFilterDefinitions
import maryk.core.query.responses.IsResponse

/** Defines a fetch. */
interface IsFetchRequest<DM : IsRootDataModel, RP : IsResponse> : IsStoreRequest<DM, RP> {
    val select: RootPropRefGraph<DM>?
    val where: IsFilter?
    val toVersion: ULong?
    val filterSoftDeleted: Boolean
    val aggregations: Aggregations?
}

internal fun <DM : IsFetchRequest<*, *>> IsObjectDataModel<DM>.addFilter(getter: (DM) -> IsFilter?) =
    this.internalMultiType(
        index = 4u,
        getter = getter,
        required = false,
        typeEnum = FilterType,
        definitionMap = mapOfFilterDefinitions,
        toSerializable = { filter: IsFilter?, _ ->
            filter?.let {
                TypedValue(filter.filterType, filter)
            }
        },
        fromSerializable = { typedValue: TypedValue<FilterType, IsFilter>? ->
            typedValue?.value
        }
    )
