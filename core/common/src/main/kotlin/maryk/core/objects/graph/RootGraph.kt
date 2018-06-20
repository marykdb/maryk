package maryk.core.objects.graph

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.ContextualDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.ContainsDataModelContext

/**
 * Create a Root graph with [properties]
 */
data class RootGraph<DO> internal constructor(
    val properties: List<IsGraphable<DO>>
) {
    constructor(vararg property: IsGraphable<DO>) : this(property.toList())

    internal object Properties : PropertyDefinitions<RootGraph<*>>() {
        init {
            this.addProperties(0, RootGraph<*>::properties)  { context: GraphContext? ->
                context?.dataModel?.properties ?: throw ContextNotFoundException()
            }
        }
    }

    internal companion object : ContextualDataModel<RootGraph<*>, Properties, ContainsDataModelContext<*>, GraphContext>(
        properties = Properties,
        contextTransformer = {
            GraphContext(it?.dataModel)
        }
    ) {
        override fun invoke(map: Map<Int, *>) = RootGraph<Any>(
            properties = map(0)
        )
    }
}
