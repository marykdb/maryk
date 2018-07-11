package maryk.core.objects

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions

/**
 * Contains a [map] with all values related to a DataObject of [dataModel] of type [DM]
 */
data class Values<DM: IsValuesDataModel<P>, P: PropertyDefinitions> internal constructor(
    val dataModel: DM,
    private val map: Map<Int, Any?>
)
