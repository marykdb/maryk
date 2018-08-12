package maryk.core.objects

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.RequestContext

typealias ValuesImpl = Values<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/**
 * Contains a [map] with all values related to a DataObject of [dataModel] of type [DM]
 */
data class Values<DM: IsValuesDataModel<P>, P: PropertyDefinitions> internal constructor(
    override val dataModel: DM,
    override val map: Map<Int, Any?>,
    override val context: RequestContext? = null
): AbstractValues<Any, DM, P>() {
    fun copy(pairCreator: P.() -> Array<Pair<Int, Any>>) =
        Values(
            dataModel,
            map.plus(
                pairCreator(this.dataModel.properties)
            ),
            context
        )
}
