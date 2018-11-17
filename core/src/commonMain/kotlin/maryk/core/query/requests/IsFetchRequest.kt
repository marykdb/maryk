package maryk.core.query.requests

import maryk.core.models.IsRootDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.Order
import maryk.core.query.filters.FilterType
import maryk.core.query.filters.IsFilter
import maryk.core.query.filters.mapOfFilterDefinitions
import maryk.core.query.responses.IsResponse

/** Defines a fetch. */
@Suppress("EXPERIMENTAL_API_USAGE")
interface IsFetchRequest<DM: IsRootDataModel<*>, RP: IsResponse> : IsStoreRequest<DM, RP> {
    val select: RootPropRefGraph<DM>?
    val filter: IsFilter?
    val order: Order?
    val toVersion: ULong?
    val filterSoftDeleted: Boolean

    companion object {
        internal fun <DM: Any> addSelect(definitions: ObjectPropertyDefinitions<DM>, getter: (DM) -> RootPropRefGraph<*>?) =
            definitions.add(3, "select",
                EmbeddedObjectDefinition(
                    dataModel = { RootPropRefGraph }
                ),
                getter
            )

        @Suppress("UNCHECKED_CAST")
        internal fun <DM: Any> addFilter(definitions: ObjectPropertyDefinitions<DM>, getter: (DM) -> IsFilter?) =
            definitions.add(4, "filter",
                MultiTypeDefinition(
                    required = false,
                    typeEnum = FilterType,
                    definitionMap = mapOfFilterDefinitions
                ),
                toSerializable = { filter, _ ->
                    filter?.let {
                        TypedValue(filter.filterType, filter)
                    }
                },
                fromSerializable = { typedValue ->
                    typedValue?.value as IsFilter?
                },
                getter = getter
            )

        internal fun <DM: Any> addOrder(definitions: ObjectPropertyDefinitions<DM>, getter: (DM) -> Order?) =
            definitions.add(5, "order",
                EmbeddedObjectDefinition(
                    required = false,
                    dataModel = { Order }
                ),
                getter
            )

        internal fun <DM: Any> addToVersion(definitions: ObjectPropertyDefinitions<DM>, getter: (DM) -> ULong?) =
            definitions.add(6, "toVersion",
                NumberDefinition(
                    required = false,
                    type = UInt64
                ), getter)

        internal fun <DM: Any> addFilterSoftDeleted(definitions: ObjectPropertyDefinitions<DM>, getter: (DM) -> Boolean?) =
            definitions.add(7, "filterSoftDeleted",
                BooleanDefinition(
                    default = true
                ),
                getter
            )
    }
}
