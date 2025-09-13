package maryk.core.properties.graph

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.ContextualDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.contextual.ContextualIndexedEnumDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ListDefinitionWrapper
import maryk.core.properties.definitions.wrapper.MultiTypeDefinitionWrapper
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.properties.types.TypedValue
import maryk.core.query.ContainsDataModelContext
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.core.values.Values
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.EndArray
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.StartArray
import maryk.json.JsonToken.StartDocument
import maryk.json.JsonToken.StartObject
import maryk.json.JsonToken.Stopped
import maryk.json.JsonToken.Value
import maryk.lib.exceptions.ParseException

/**
 * Represents a graph branch for a specific [type] within a MultiType [parent].
 * Allows querying strongly typed sub-properties when the value is an embedded [IsValuesDataModel].
 */
data class TypePropRefGraph<
    DM : IsValuesDataModel,
    DMS : IsValuesDataModel,
    E : TypeEnum<Values<DMS>>
> internal constructor(
    val parent: MultiTypeDefinitionWrapper<E, Any, Any, IsPropertyContext, DM>,
    val type: E,
    override val properties: List<IsPropRefGraphNode<DMS>>
) : IsPropRefGraphNode<DM>, IsTransportablePropRefGraphNode, IsPropRefGraph<DMS> {
    override val index = parent.index
    override val graphType = PropRefGraphType.TypeGraph

    override fun toString() = "${parent.name}.*${type.name} { ${renderPropsAsString()} }"

    companion object : ContextualDataModel<TypePropRefGraph<*, *, *>, Companion, ContainsDataModelContext<*>, GraphContext>(
        contextTransformer = {
            if (it is GraphContext && it.subDataModel != null) {
                GraphContext(it.subDataModel)
            } else {
                GraphContext(it?.dataModel)
            }
        }
    ) {
        val parent by contextual(
            index = 1u,
            definition = ContextualPropertyReferenceDefinition(
                contextualResolver = { context: GraphContext? ->
                    (context?.subDataModel ?: context?.dataModel) as? IsValuesDataModel?
                        ?: throw ContextNotFoundException()
                }
            ),
            capturer = { context, value ->
                @Suppress("UNCHECKED_CAST")
                context.reference = value as? IsPropertyReference<*, IsSerializablePropertyDefinition<*, *>, *>
            },
            toSerializable = { value: IsDefinitionWrapper<*, *, *, *>?, _ -> value?.ref() },
            fromSerializable = { value -> value?.propertyDefinition as IsDefinitionWrapper<*, *, *, *>? },
            getter = TypePropRefGraph<*, *, *>::parent
        )

        val type by contextual(
            index = 2u,
            getter = TypePropRefGraph<*, *, *>::type as (TypePropRefGraph<*, *, *>) -> IndexedEnum,
            definition = ContextualIndexedEnumDefinition<GraphContext, GraphContext, IndexedEnum, IsMultiTypeDefinition<TypeEnum<Any>, Any, RequestContext>>(
                contextualResolver = { context: GraphContext? ->
                    @Suppress("UNCHECKED_CAST")
                    (context?.reference?.propertyDefinition as? MultiTypeDefinitionWrapper<TypeEnum<Any>, Any, Any, IsPropertyContext, IsValuesDataModel>?)?.definition
                        as? IsMultiTypeDefinition<TypeEnum<Any>, Any, RequestContext>
                        ?: throw ContextNotFoundException()
                }
            ),
            capturer = { context, value ->
                @Suppress("UNCHECKED_CAST")
                val wrapper = context.reference?.propertyDefinition as MultiTypeDefinitionWrapper<TypeEnum<Any>, Any, Any, IsPropertyContext, IsValuesDataModel>
                @Suppress("UNCHECKED_CAST")
                val embedded = wrapper.definition(value as TypeEnum<Values<IsValuesDataModel>>) as EmbeddedValuesDefinition<IsValuesDataModel>
                context.subDataModel = embedded.dataModel
            }
        )

        val properties: ListDefinitionWrapper<TypedValue<PropRefGraphType, IsTransportablePropRefGraphNode>, IsPropRefGraphNode<Nothing>, GraphContext, TypePropRefGraph<*, *, *>> by list(
            index = 3u,
            valueDefinition = InternalMultiTypeDefinition(
                definitionMap = mapOf(
                    PropRefGraphType.Graph to EmbeddedObjectDefinition(
                        dataModel = { PropRefGraph }
                    ),
                    PropRefGraphType.MapKey to EmbeddedObjectDefinition(
                        dataModel = { GraphMapItem }
                    ),
                    PropRefGraphType.PropRef to ContextualPropertyReferenceDefinition(
                        contextualResolver = { context: GraphContext? ->
                            context?.subDataModel as? IsValuesDataModel? ?: throw ContextNotFoundException()
                        }
                    ),
                    PropRefGraphType.TypeGraph to EmbeddedObjectDefinition(
                        dataModel = { TypePropRefGraph }
                    )
                ),
                typeEnum = PropRefGraphType
            ),
            getter = TypePropRefGraph<*, *, *>::properties,
            toSerializable = { value: IsPropRefGraphNode<*> ->
                when (value) {
                    is IsDefinitionWrapper<*, *, *, *> -> TypedValue(value.graphType, value.ref() as IsTransportablePropRefGraphNode)
                    is PropRefGraph<*, *> -> TypedValue(value.graphType, value)
                    is GraphMapItem<*, *> -> TypedValue(value.graphType, value)
                    is TypePropRefGraph<*, *, *> -> TypedValue(value.graphType, value)
                    else -> throw ParseException("Unknown PropRefGraphType ${value.graphType}")
                }
            },
            fromSerializable = { value: TypedValue<PropRefGraphType, IsTransportablePropRefGraphNode> ->
                when (value.type) {
                    PropRefGraphType.PropRef -> (value.value as IsPropertyReferenceForValues<*, *, *, *>).propertyDefinition
                    PropRefGraphType.Graph -> value.value as IsPropRefGraphNode<*>
                    PropRefGraphType.MapKey -> value.value as GraphMapItem<*, *>
                    PropRefGraphType.TypeGraph -> value.value as TypePropRefGraph<*, *, *>
                }
            }
        )

        override fun invoke(values: ObjectValues<TypePropRefGraph<*, *, *>, Companion>): TypePropRefGraph<*, *, *> =
            TypePropRefGraph<IsValuesDataModel, IsValuesDataModel, TypeEnum<Values<IsValuesDataModel>>>(
                parent = values(1u),
                type = values(2u) as TypeEnum<Values<IsValuesDataModel>>,
                properties = values(3u)
            )

        override val Serializer = object: ObjectDataModelSerializer<TypePropRefGraph<*, *, *>, Companion, ContainsDataModelContext<*>, GraphContext>(this) {
            override fun writeObjectAsJson(
                obj: TypePropRefGraph<*, *, *>,
                writer: IsJsonLikeWriter,
                context: GraphContext?,
                skip: List<IsDefinitionWrapper<*, *, *, TypePropRefGraph<*, *, *>>>?
            ) {
                val newContext = transformContext(context)
                val parentRef = obj.parent.ref()
                parent.capture(newContext, parentRef)

                writer.writeStartObject()
                writer.writeFieldName(parentRef.completeName)
                writer.writeStartObject()
                val typeField = "*${obj.type.name}"
                if (writer is maryk.yaml.YamlWriter) {
                    writer.writeFieldName("'" + typeField + "'")
                } else {
                    writer.writeFieldName(typeField)
                }
                writePropertiesToJson(obj.properties, writer, newContext)
                writer.writeEndObject()
                writer.writeEndObject()
            }

            override fun readJson(
                reader: IsJsonLikeReader,
                context: GraphContext?
            ): ObjectValues<TypePropRefGraph<*, *, *>, Companion> {
                if (reader.currentToken == StartDocument) {
                    reader.nextToken()
                }

                if (reader.currentToken !is StartObject) {
                    throw ParseException("JSON value should be an Object")
                }

                reader.nextToken()

                val parentValue = reader.currentToken.let {
                    if (it !is FieldName) {
                        throw ParseException("JSON value should be a FieldName")
                    }

                    val value = it.value ?: throw ParseException("JSON value should not be null")

                    parent.definition.fromString(value, context)
                }
                parent.capture(context, parentValue)

                if (reader.nextToken() !is StartObject) {
                    throw ParseException("JSON value should be an Object")
                }

                reader.nextToken()

                val typeToken = reader.currentToken.let {
                    if (it !is FieldName) throw ParseException("JSON value should be a FieldName")
                    it.value ?: throw ParseException("JSON value should not be null")
                }

                val typeValue = type.definition.fromString(typeToken.removePrefix("*"), context)
                type.capture(context, typeValue)

                if (reader.nextToken() !is StartArray) {
                    throw ParseException("JSON value should be an Array")
                }

                val propertiesValue = mutableListOf<TypedValue<PropRefGraphType, IsTransportablePropRefGraphNode>>()

                var currentToken = reader.nextToken()
                val multiTypeDefinition = properties.valueDefinition as IsMultiTypeDefinition<PropRefGraphType, IsTransportablePropRefGraphNode, GraphContext>

                while (currentToken != EndArray && currentToken !is Stopped) {
                    when (currentToken) {
                        is StartObject -> {
                            val newContext = transformContext(context)
                            propertiesValue.add(readGraphNodeFromJson(reader, newContext))
                        }
                        is Value<*> -> {
                            val tokenValue = currentToken.value
                            val type = when {
                                tokenValue is String && tokenValue.contains('[') -> PropRefGraphType.MapKey
                                else -> PropRefGraphType.PropRef
                            }
                            propertiesValue.add(
                                TypedValue(
                                    type,
                                    multiTypeDefinition.definition(type)!!.readJson(reader, context)
                                )
                            )
                        }
                        else -> throw ParseException("JSON value should be a String or an Object")
                    }

                    currentToken = reader.nextToken()
                }

                reader.nextToken() // end type object
                reader.nextToken() // end outer object

                return create {
                    parent -= parentValue
                    type += typeValue
                    properties -= propertiesValue
                }
            }
        }
    }
}
