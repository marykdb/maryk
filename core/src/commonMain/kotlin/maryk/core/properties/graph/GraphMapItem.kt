package maryk.core.properties.graph

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.ContextualDataModel
import maryk.core.models.IsDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.models.values
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.contextual.ContextualSubDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsMapDefinitionWrapper
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.graph.PropRefGraph.Companion.parent
import maryk.core.properties.graph.PropRefGraph.Companion.properties
import maryk.core.properties.references.IsMapReference
import maryk.core.query.ContainsDataModelContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException


operator fun <K: Any, DM: IsDataModel> IsMapDefinitionWrapper<K, *, *, *, *>.get(
    key: K
) = GraphMapItem<K, DM>(
    mapReference = this.ref() as IsMapReference<*, *, *, *>,
    key = key,
)

/**
 * Represents a Property Reference Graph branch below a [parent] with all [properties] to fetch
 * [properties] should always be sorted by index so processing graphs is a lot easier
 */
data class GraphMapItem<K: Any, DM: IsDataModel> internal constructor(
    val mapReference: IsMapReference<*, *, *, *>,
    val key: K,
) : IsPropRefGraphNode<DM>, IsTransportablePropRefGraphNode {
    override val index = mapReference.index
    override val graphType = PropRefGraphType.MapKey

    override fun toString() = "${this.mapReference.name}[$key]"

    companion object : ContextualDataModel<GraphMapItem<*, *>, Companion, ContainsDataModelContext<*>, GraphContext>(
        contextTransformer = {
            if (it is GraphContext && it.subDataModel != null) {
                GraphContext(it.subDataModel)
            } else {
                GraphContext(it?.dataModel)
            }
        }
    ) {
        val mapReference by contextual(
            index = 1u,
            getter = GraphMapItem<*, *>::mapReference,
            definition = ContextualPropertyReferenceDefinition(
                contextualResolver = { context: GraphContext? ->
                    (context?.subDataModel ?: context?.dataModel) as? IsValuesDataModel? ?: throw ContextNotFoundException()
                }
            ),
            capturer = { context, value ->
                @Suppress("UNCHECKED_CAST")
                context.reference = value as IsMapReference<Comparable<Any>, Any, IsPropertyContext, IsMapDefinitionWrapper<Comparable<Any>, Any, Any, IsPropertyContext, Any>>
            }
        )

        val key by contextual(
            index = 2u,
            getter = GraphMapItem<*, *>::key,
            definition = ContextualSubDefinition(
                contextualResolver = { context: GraphContext? ->
                    @Suppress("UNCHECKED_CAST")
                    (context?.reference as IsMapReference<Comparable<Any>, Any, IsPropertyContext, IsMapDefinitionWrapper<Comparable<Any>, Any, Any, IsPropertyContext, Any>>?)?.propertyDefinition?.definition?.keyDefinition
                        ?: throw ContextNotFoundException()
                }
            )
        )

        override fun invoke(values: ObjectValues<GraphMapItem<*, *>, Companion>): GraphMapItem<*, *> = GraphMapItem<Any, IsDataModel>(
            mapReference = values(1u),
            key = values(2u)
        )

        override val Serializer = object: ObjectDataModelSerializer<GraphMapItem<*, *>, Companion, ContainsDataModelContext<*>, GraphContext>(this) {
            override fun writeObjectAsJson(
                obj: GraphMapItem<*, *>,
                writer: IsJsonLikeWriter,
                context: GraphContext?,
                skip: List<IsDefinitionWrapper<*, *, *, GraphMapItem<*, *>>>?
            ) {
                writer.writeString("${obj.mapReference.name}[${obj.key}]")
            }

            override fun readJson(
                reader: IsJsonLikeReader,
                context: GraphContext?
            ): ObjectValues<GraphMapItem<*, *>, Companion> {
                val tokenToParse: JsonToken.Value<*> = reader.currentToken as? JsonToken.Value<*>
                    ?: throw ParseException("Expected value token")

                val (name, keyAsString) = tokenToParse.value.toString().split("[", "]")

                val mapReference = context?.dataModel?.getPropertyReferenceByName(name, context) as? IsMapReference<*, *, *, *>
                    ?: throw ParseException("Expected MapReference")

                @Suppress("UNCHECKED_CAST")
                val key = when (val mapDef = mapReference.comparablePropertyDefinition) {
                    is IsMapDefinition<*, *, *> -> mapDef.keyDefinition.fromString(keyAsString) as Comparable<Any>
                    else -> throw ParseException("Unknown MapReference type")
                }

                return values {
                    mapNonNulls(
                        this.mapReference withSerializable mapReference,
                        this.key withSerializable key
                    )
                }
            }
        }
    }
}

