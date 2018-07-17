package maryk.core.objects

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.PropertyDefinitions

typealias ValuesImpl = Values<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/**
 * Contains a [map] with all values related to a DataObject of [dataModel] of type [DM]
 */
data class Values<DM: IsValuesDataModel<P>, P: PropertyDefinitions> internal constructor(
    override val dataModel: DM,
    override val map: Map<Int, Any?>
): AbstractValues<Any, DM, P>()