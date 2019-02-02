package maryk.core.properties.definitions.descriptors

import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsByteTransportableCollection
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.MultiTypeDefinitionContext
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.contextual.ContextCollectionTransformerDefinition
import maryk.core.properties.definitions.mapOfPropertyDefEmbeddedObjectDefinitions
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.graph.PropRefGraphType
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ValuePropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.core.yaml.readNamedIndexField
import maryk.core.yaml.writeNamedIndexField
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException
import maryk.yaml.IsYamlReader
import maryk.yaml.YamlWriter

/**
 * Class which describes a type definition for inside a multi typed definition.
 * It contains an [index] and [name] so it can be serialized either efficiently or readable.
 * It also contains a [definition] to describe this type of the multi type
 */
private data class MultiTypeDescriptor(
    val index: UInt,
    val name: String,
    val definition: IsSubDefinition<out Any, ContainsDefinitionsContext>
) {
    private object Properties: ObjectPropertyDefinitions<MultiTypeDescriptor>() {
        val index = add(1, "index",
            NumberDefinition(type = UInt32),
            MultiTypeDescriptor::index
        )
        val name = add(2, "name", StringDefinition(), MultiTypeDescriptor::name)

        val definition = add(
            3, "definition",
            MultiTypeDefinition(
                typeEnum = PropertyDefinitionType,
                definitionMap = mapOfPropertyDefEmbeddedObjectDefinitions
            ),
            getter = {
                val defType = it.definition as IsTransportablePropertyDefinitionType<*>
                TypedValue(defType.propertyDefinitionType, defType)
            }
        )
    }

    internal object Model : SimpleObjectDataModel<MultiTypeDescriptor, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<MultiTypeDescriptor, Properties>) = MultiTypeDescriptor(
            index = values(1),
            name = values(2),
            definition = values<TypedValue<IndexedEnum<Any>, IsSubDefinition<out Any, IsPropertyContext>>>(3).value
        )

        override fun readJson(reader: IsJsonLikeReader, context: IsPropertyContext?): ObjectValues<MultiTypeDescriptor, Properties> {
            // When writing YAML, use YAML optimized format with complex field names
            return if (reader is IsYamlReader) {
                this.values(context as? RequestContext) {
                    val valueMap = MutableValueItems()

                    reader.readNamedIndexField(valueMap, name, index)
                    valueMap += definition withNotNull definition.readJson(reader, context as ContainsDefinitionsContext?)

                    valueMap
                }
            } else {
                super.readJson(reader, context)
            }
        }

        override fun writeJson(obj: MultiTypeDescriptor, writer: IsJsonLikeWriter, context: IsPropertyContext?) {
            // When writing YAML, use YAML optimized format with complex field names
            if (writer is YamlWriter) {
                val typedDefinition = Properties.definition.getPropertyAndSerialize(obj, context as ContainsDefinitionsContext?)
                        ?: throw Exception("Unknown type ${obj.definition} so cannot serialize contents")

                writer.writeNamedIndexField(obj.name, obj.index)

                Properties.definition.writeJsonValue(typedDefinition, writer, context)
            } else {
                super.writeJson(obj, writer, context)
            }
        }
    }
}

/** Definition for Multi type descriptor List property */
private data class MultiTypeDescriptorListDefinition(
    override val valueDefinition: IsValueDefinition<MultiTypeDescriptor, IsPropertyContext>
) : IsCollectionDefinition<MultiTypeDescriptor, List<MultiTypeDescriptor>, IsPropertyContext, IsValueDefinition<MultiTypeDescriptor, IsPropertyContext>> {
    override val indexed: Boolean = false
    override val required: Boolean = true
    override val final: Boolean = false
    override val minSize: UInt? = null
    override val maxSize: UInt? = null

    override val propertyDefinitionType = PropertyDefinitionType.List

    override fun newMutableCollection(context: IsPropertyContext?) = mutableListOf<MultiTypeDescriptor>()

    override fun validateCollectionForExceptions(
        refGetter: () -> IsPropertyReference<List<MultiTypeDescriptor>, IsPropertyDefinition<List<MultiTypeDescriptor>>, *>?,
        newValue: List<MultiTypeDescriptor>,
        validator: (item: MultiTypeDescriptor, itemRefFactory: () -> IsPropertyReference<MultiTypeDescriptor, IsPropertyDefinition<MultiTypeDescriptor>, *>?) -> Any
    ) {}

    /** Write [value] to JSON [writer] with [context] */
    override fun writeJsonValue(value: List<MultiTypeDescriptor>, writer: IsJsonLikeWriter, context: IsPropertyContext?) {
        if (writer is YamlWriter) {
            writer.writeStartObject()
            for (it in value) {
                this.valueDefinition.writeJsonValue(it, writer, context)
            }
            writer.writeEndObject()
        } else {
            writer.writeStartArray()
            for (it in value) {
                this.valueDefinition.writeJsonValue(it, writer, context)
            }
            writer.writeEndArray()
        }
    }

    /** Read Collection from JSON [reader] within optional [context] */
    override fun readJson(reader: IsJsonLikeReader, context: IsPropertyContext?): List<MultiTypeDescriptor> {
        val collection: MutableCollection<MultiTypeDescriptor> = newMutableCollection(context)

        if(reader is IsYamlReader) {
            if (reader.currentToken !is JsonToken.StartObject) {
                throw ParseException("YAML definition map should be an Object")
            }

            while (reader.nextToken() !== JsonToken.EndObject) {
                collection.add(
                    valueDefinition.readJson(reader, context)
                )
            }
        } else {
            if (reader.currentToken !is JsonToken.StartArray) {
                throw ParseException("JSON value should be an Array")
            }
            while (reader.nextToken() !== JsonToken.EndArray) {
                collection.add(
                    valueDefinition.readJson(reader, context)
                )
            }
        }
        @Suppress("UNCHECKED_CAST")
        return collection as List<MultiTypeDescriptor>
    }
}

/**
 * Describes the property wrapper for the List of type descriptors inside a Multi Typed property definition
 */
private data class MultiTypeDescriptorPropertyDefinitionWrapper internal constructor(
    override val index: Int,
    override val name: String,
    override val definition: ContextCollectionTransformerDefinition<MultiTypeDescriptor, List<MultiTypeDescriptor>, MultiTypeDefinitionContext, ContainsDefinitionsContext>,
    override val toSerializable: ((List<MultiTypeDescriptor>?, MultiTypeDefinitionContext?) -> List<MultiTypeDescriptor>?)? = null,
    override val fromSerializable: ((List<MultiTypeDescriptor>?) -> List<MultiTypeDescriptor>?)? = null,
    override val shouldSerialize: ((Any) -> Boolean)? = null,
    override val capturer: ((MultiTypeDefinitionContext, List<MultiTypeDescriptor>) -> Unit)? = null,
    override val getter: (MultiTypeDefinition<IndexedEnum<Any>, ContainsDefinitionsContext>) -> List<MultiTypeDescriptor>?
) :
    IsByteTransportableCollection<MultiTypeDescriptor, List<MultiTypeDescriptor>, MultiTypeDefinitionContext> by definition,
    IsPropertyDefinitionWrapper<List<MultiTypeDescriptor>, List<MultiTypeDescriptor>, MultiTypeDefinitionContext, MultiTypeDefinition<IndexedEnum<Any>, ContainsDefinitionsContext>>
{
    override val graphType = PropRefGraphType.PropRef

    override fun getRef(parentRef: AnyPropertyReference?) =
        ValuePropertyReference(this, parentRef)
}

/**
 * Add a descriptor of multi types to a MultiTypeDefinition ObjectPropertyDefinitions
 * Set [index] and [name] to append it to model
 */
internal fun ObjectPropertyDefinitions<MultiTypeDefinition<*, *>>.addDescriptorPropertyWrapperWrapper(
    index: Int,
    name: String
) {
    MultiTypeDescriptorPropertyDefinitionWrapper(
        index, name,
        definition = ContextCollectionTransformerDefinition(
            definition = MultiTypeDescriptorListDefinition(
                valueDefinition = EmbeddedObjectDefinition(
                    dataModel = { MultiTypeDescriptor.Model }
                )
            ),
            contextTransformer = {context: MultiTypeDefinitionContext? ->
                context?.definitionsContext
            }
        ),
        getter = {
            it.definitionMap.map { entry ->
                MultiTypeDescriptor(
                    index = entry.key.index,
                    name = entry.key.name,
                    definition = entry.value
                )
            }.toList()
        },
        capturer = { context: MultiTypeDefinitionContext, value ->
            context.definitionMap = convertMultiTypeDescriptors(value)
        }
    ).apply {
        @Suppress("UNCHECKED_CAST")
        addSingle(this as IsPropertyDefinitionWrapper<out Any, *, *, MultiTypeDefinition<*, *>>)
    }
}

/**
 * Convert multi type descriptors in a list in [value] to an indexed map of definitions.
 * Will throw an exception if it fails to convert
 */
@Suppress("UNCHECKED_CAST")
internal fun convertMultiTypeDescriptors(value: Any?): Map<IndexedEnum<Any>, IsSubDefinition<out Any, ContainsDefinitionsContext>> {
    val descriptorList = value as? List<MultiTypeDescriptor>
            ?: throw ParseException("Multi type definition descriptor cannot be empty")

    return descriptorList.map {
        Pair(
            IndexedEnum(it.index, it.name) as IndexedEnum<Any>,
            it.definition
        )
    }.toMap()
}
