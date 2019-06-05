package maryk.core.query.requests

import maryk.core.aggregations.Aggregations
import maryk.core.models.IsRootDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.wrapper.EmbeddedObjectDefinitionWrapper
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.RequestContext
import maryk.core.query.filters.FilterType
import maryk.core.query.filters.IsFilter
import maryk.core.query.filters.mapOfFilterDefinitions
import maryk.core.query.responses.IsResponse

/** Defines a fetch. */
interface IsFetchRequest<DM : IsRootDataModel<P>, P : PropertyDefinitions, RP : IsResponse> : IsStoreRequest<DM, RP> {
    val select: RootPropRefGraph<P>?
    val where: IsFilter?
    val toVersion: ULong?
    val filterSoftDeleted: Boolean
    val aggregations: Aggregations?

    companion object {
        internal fun <DM : Any> addSelect(
            definitions: ObjectPropertyDefinitions<DM>,
            getter: (DM) -> RootPropRefGraph<*>?
        ) =
            definitions.add(3u, "select",
                EmbeddedObjectDefinition(
                    dataModel = { RootPropRefGraph }
                ),
                getter
            )

        internal fun <DM : Any> addFilter(definitions: ObjectPropertyDefinitions<DM>, getter: (DM) -> IsFilter?) =
            definitions.add(
                4u, "where",
                InternalMultiTypeDefinition(
                    required = false,
                    typeEnum = FilterType,
                    definitionMap = mapOfFilterDefinitions
                ),
                getter = getter,
                toSerializable = { filter, _ ->
                    filter?.let {
                        TypedValue(filter.filterType, filter)
                    }
                },
                fromSerializable = { typedValue ->
                    typedValue?.value
                }
            )

        internal fun <DM : Any> addToVersion(definitions: ObjectPropertyDefinitions<DM>, getter: (DM) -> ULong?) =
            definitions.add(
                5u, "toVersion",
                NumberDefinition(
                    required = false,
                    type = UInt64
                ),
                getter
            )

        internal fun <DM : Any> addFilterSoftDeleted(
            definitions: ObjectPropertyDefinitions<DM>,
            getter: (DM) -> Boolean?
        ) =
            definitions.add(
                6u, "filterSoftDeleted",
                BooleanDefinition(
                    default = true
                ),
                getter
            )

        @Suppress("UNCHECKED_CAST")
        internal fun <DM: Any> addAggregationsDefinition(definitions: ObjectPropertyDefinitions<*>, getter: (DM) -> Aggregations?): EmbeddedObjectDefinitionWrapper<Aggregations, Aggregations, *, *, RequestContext, RequestContext, Nothing> =
            definitions.add(
                7u,
                "aggregations",
                EmbeddedObjectDefinition(dataModel = { Aggregations }),
                getter as (Any) -> Aggregations?,
                alternativeNames = setOf("aggs")
            )
    }
}
