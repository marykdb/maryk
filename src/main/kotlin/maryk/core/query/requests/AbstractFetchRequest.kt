package maryk.core.query.requests

import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.properties.types.UInt64
import maryk.core.query.Order
import maryk.core.query.filters.IsFilter
import maryk.core.query.filters.mapOfFilterDefinitions

/** Defines a fetch.
 * @param dataModel Root model of data to do operations on
 * @param filter to use to filter data
 * @param order to use for ordering the found data
 * @param toVersion until which version to retrieve data. (exclusive)
 */
abstract class AbstractFetchRequest<DO: Any, out DM: RootDataModel<DO>>(
        dataModel: DM,
        val filter: IsFilter?,
        val order: Order?,
        val toVersion: UInt64?,
        val filterSoftDeleted: Boolean = true
) : AbstractModelRequest<DO, DM>(dataModel) {
    object Properties {
        val filter = MultiTypeDefinition(
                name = "filter",
                index = 2,
                required = true,
                getDefinition = { mapOfFilterDefinitions[it] }
        )
        val order = SubModelDefinition(
                name = "order",
                index = 3,
                dataModel = Order
        )
        val toVersion = NumberDefinition(
                name = "toVersion",
                index = 4,
                type = UInt64
        )
        val filterSoftDeleted = BooleanDefinition(
                name = "filterSoftDeleted",
                index = 5
        )
    }
}