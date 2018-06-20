package maryk.core.objects.graph

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.AbstractDataModel
import maryk.core.objects.ContextualDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.EmbeddedObjectPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.ContainsDataModelContext
import maryk.lib.exceptions.ParseException

/** Get a graph of an embedded object */
fun <PDO: Any, DO: Any> IsPropertyDefinitionWrapper<DO, DO, IsPropertyContext, PDO>.graph(
    vararg property: IsGraphable<DO>
) = Graph(this, property.toList())

/**
 * Represents a Graph branch below a [parent] with all [properties] to fetch
 */
data class Graph<PDO: Any, DO: Any> internal constructor(
    val parent: IsPropertyDefinitionWrapper<DO, DO, IsPropertyContext, PDO>,
    val properties: List<IsGraphable<DO>>
) : IsGraphable<PDO> {
    override val graphType = GraphType.Graph

    internal object Properties : PropertyDefinitions<Graph<*, *>>() {
        init {
            add(0, "parent",
                ContextualPropertyReferenceDefinition(
                    contextualResolver = { context: GraphContext? ->
                        context?.dataModel?.properties ?: throw ContextNotFoundException()
                    }
                ),
                capturer = { context, value ->
                    context.subDataModel = (value.propertyDefinition as EmbeddedObjectPropertyDefinitionWrapper<*, *, *, *, *, *, *>).dataModel
                },
                toSerializable = { value: IsPropertyDefinitionWrapper<*, *, *, *>?, _ ->
                    value?.getRef()
                },
                fromSerializable = { value ->
                    value?.propertyDefinition as IsPropertyDefinitionWrapper<*, *, *, *>?
                },
                getter = Graph<*, *>::parent
            )

            this.addProperties(1, Graph<*, *>::properties) { context: GraphContext? ->
                context?.subDataModel?.properties ?: throw ContextNotFoundException()
            }
        }
    }

    internal companion object : ContextualDataModel<Graph<*, *>, Properties, ContainsDataModelContext<*>, GraphContext>(
        properties = Properties,
        contextTransformer = {
            GraphContext(it?.dataModel)
        }
    ) {
        override fun invoke(map: Map<Int, *>) = Graph<Any, Any>(
            parent = map(0),
            properties = map(1)
        )
    }
}

fun <DO: Any> PropertyDefinitions<DO>.addProperties(
    index: Int,
    getter: (DO) -> List<IsGraphable<*>>,
    contextResolver: (GraphContext?) -> PropertyDefinitions<*>
) {
    this.add(index, "properties",
        ListDefinition(
            valueDefinition = MultiTypeDefinition(
                definitionMap = mapOf(
                    GraphType.Graph to EmbeddedObjectDefinition(
                        dataModel = { Graph }
                    ),
                    GraphType.PropRef to ContextualPropertyReferenceDefinition(
                        contextualResolver = contextResolver
                    )
                ),
                typeEnum = GraphType
            )
        ),
        toSerializable = { value: IsGraphable<*> ->
            value.let {
                when (it) {
                    is IsPropertyDefinitionWrapper<*, *, *, *> -> TypedValue(it.graphType, it.getRef())
                    is Graph<*, *> -> TypedValue(it.graphType, it)
                    else -> throw ParseException("Unknown GraphType ${it.graphType}")
                }
            }
        },
        fromSerializable = { value: TypedValue<GraphType, *> ->
            when (value.value) {
                is IsPropertyReference<*, *> -> value.value.propertyDefinition as IsPropertyDefinitionWrapper<*, *, *, *>
                is Graph<*, *> -> value.value
                else -> throw ParseException("Unknown GraphType ${value.type}")
            }
        },
        getter = getter
    )
}

class GraphContext(
    override var dataModel: AbstractDataModel<*, *, *, *>? = null,
    var subDataModel: AbstractDataModel<*, *, *, *>? = null
) : ContainsDataModelContext<AbstractDataModel<*, *, *, *>>
