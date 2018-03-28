package maryk.core.query.requests

import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.Order
import maryk.core.query.filters.FilterType
import maryk.core.query.filters.IsFilter
import maryk.core.query.filters.mapOfFilterDefinitions

/** Defines a fetch. */
interface IsFetchRequest<DO: Any, out DM: RootDataModel<DO, *>> : IsObjectRequest<DO, DM> {
    val filter: IsFilter?
    val order: Order?
    val toVersion: UInt64?
    val filterSoftDeleted: Boolean

    companion object {
        internal fun <DM: Any> addFilter(definitions: PropertyDefinitions<DM>, getter: (DM) -> TypedValue<FilterType, Any>?) {
            definitions.add(2, "filter", MultiTypeDefinition(required = false, definitionMap = mapOfFilterDefinitions), getter)
        }

        internal fun <DM: Any> addOrder(definitions: PropertyDefinitions<DM>, getter: (DM) -> Order?) {
            definitions.add(3, "order", SubModelDefinition(required = false, dataModel = { Order }), getter)
        }

        internal fun <DM: Any> addToVersion(definitions: PropertyDefinitions<DM>, getter: (DM) -> UInt64?) {
            definitions.add(4, "toVersion", NumberDefinition(required = false, type = UInt64), getter)
        }

        internal fun <DM: Any> addFilterSoftDeleted(definitions: PropertyDefinitions<DM>, getter: (DM) -> Boolean?) {
            definitions.add(5, "filterSoftDeleted", BooleanDefinition(), getter)
        }
    }
}