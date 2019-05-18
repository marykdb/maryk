package maryk.core.properties.definitions.descriptors

import maryk.core.exceptions.TypeException
import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.IsUsableInMultiType
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.contextual.ContextCollectionTransformerDefinition
import maryk.core.properties.definitions.contextual.MultiTypeDefinitionContext
import maryk.core.properties.definitions.descriptors.MultiTypeDescriptor.Model
import maryk.core.properties.definitions.mapOfPropertyDefEmbeddedObjectDefinitions
import maryk.core.properties.definitions.wrapper.ContextualDefinitionWrapper
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.properties.references.IsPropertyReference
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
import maryk.json.JsonToken.EndArray
import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.StartArray
import maryk.json.JsonToken.StartObject
import maryk.lib.exceptions.ParseException
import maryk.yaml.IsYamlReader
import maryk.yaml.YamlWriter

/**
 * Class which describes a type definition for inside a multi typed definition.
 * It contains an [index] and [name] so it can be serialized either efficiently or readable.
 * It also contains a [definition] to describe this type of the multi type
 */
internal data class MultiTypeDescriptor(
    val index: UInt,
    val name: String,
    val definition: IsUsableInMultiType<out Any, ContainsDefinitionsContext>
) {
    internal object Properties : ObjectPropertyDefinitions<MultiTypeDescriptor>() {
        val index = add(
            1u, "index",
            NumberDefinition(type = UInt32),
            MultiTypeDescriptor::index
        )
        val name = add(2u, "name", StringDefinition(), MultiTypeDescriptor::name)

        val definition = add(
            3u, "definition",
            InternalMultiTypeDefinition(
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
            index = values(1u),
            name = values(2u),
            definition = values<TypedValue<*, IsUsableInMultiType<out Any, IsPropertyContext>>>(3u).value
        )

        override fun readJson(
            reader: IsJsonLikeReader,
            context: IsPropertyContext?
        ): ObjectValues<MultiTypeDescriptor, Properties> {
            // When writing YAML, use YAML optimized format with complex field names
            return if (reader is IsYamlReader) {
                this.values(context as? RequestContext) {
                    val valueMap = MutableValueItems()

                    reader.readNamedIndexField(valueMap, name, index)
                    valueMap += definition withNotNull definition.readJson(
                        reader,
                        context as ContainsDefinitionsContext?
                    )

                    valueMap
                }
            } else {
                super.readJson(reader, context)
            }
        }

        override fun writeJson(obj: MultiTypeDescriptor, writer: IsJsonLikeWriter, context: IsPropertyContext?) {
            // When writing YAML, use YAML optimized format with complex field names
            if (writer is YamlWriter) {
                val typedDefinition =
                    Properties.definition.getPropertyAndSerialize(obj, context as ContainsDefinitionsContext?)
                        ?: throw TypeException("Unknown type ${obj.definition} so cannot serialize contents")

                writer.writeNamedIndexField(obj.name, obj.index)

                Properties.definition.writeJsonValue(typedDefinition, writer, context)
            } else {
                super.writeJson(obj, writer, context)
            }
        }
    }
}

/**
 * Definition for Multi type descriptor List property.
 * Overrides ListDefinition so it can write special Yaml notation with complex field names.
 */
internal class MultiTypeDescriptorListDefinition : IsListDefinition<MultiTypeDescriptor, IsPropertyContext> {
    override val required: Boolean = true
    override val final: Boolean = false
    override val minSize: UInt? = null
    override val maxSize: UInt? = null
    override val default: List<MultiTypeDescriptor>? = null
    override val valueDefinition: IsValueDefinition<MultiTypeDescriptor, IsPropertyContext> = EmbeddedObjectDefinition(
        dataModel = { Model }
    )

    override fun newMutableCollection(context: IsPropertyContext?) = mutableListOf<MultiTypeDescriptor>()

    override fun validateCollectionForExceptions(
        refGetter: () -> IsPropertyReference<List<MultiTypeDescriptor>, IsPropertyDefinition<List<MultiTypeDescriptor>>, *>?,
        newValue: List<MultiTypeDescriptor>,
        validator: (item: MultiTypeDescriptor, itemRefFactory: () -> IsPropertyReference<MultiTypeDescriptor, IsPropertyDefinition<MultiTypeDescriptor>, *>?) -> Any
    ) {}

    /** Write [value] to JSON [writer] with [context] */
    override fun writeJsonValue(
        value: List<MultiTypeDescriptor>,
        writer: IsJsonLikeWriter,
        context: IsPropertyContext?
    ) {
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
        val collection: MutableList<MultiTypeDescriptor> = newMutableCollection(context)

        if (reader is IsYamlReader) {
            if (reader.currentToken !is StartObject) {
                throw ParseException("YAML definition map should be an Object")
            }

            while (reader.nextToken() !== EndObject) {
                collection.add(
                    valueDefinition.readJson(reader, context)
                )
            }
        } else {
            if (reader.currentToken !is StartArray) {
                throw ParseException("JSON value should be an Array")
            }
            while (reader.nextToken() !== EndArray) {
                collection.add(
                    valueDefinition.readJson(reader, context)
                )
            }
        }
        return collection
    }
}

/**
 * Add a descriptor of multi types to a MultiTypeDefinition ObjectPropertyDefinitions
 * Set [index] and [name] to append it to model
 */
internal fun ObjectPropertyDefinitions<MultiTypeEnumDefinition<MultiTypeEnum<*>>>.addDescriptorPropertyWrapperWrapper(
    index: UInt,
    name: String
) = ContextualDefinitionWrapper<List<MultiTypeDescriptor>, Array<MultiTypeEnum<*>>, MultiTypeDefinitionContext, ContextCollectionTransformerDefinition<MultiTypeDescriptor, List<MultiTypeDescriptor>, MultiTypeDefinitionContext, ContainsDefinitionsContext>, MultiTypeEnumDefinition<MultiTypeEnum<*>>>(
    index, name,
    definition = ContextCollectionTransformerDefinition(
        definition = MultiTypeDescriptorListDefinition(),
        contextTransformer = { context: MultiTypeDefinitionContext? ->
            context?.definitionsContext
        }
    ),
    toSerializable = { values: Array<MultiTypeEnum<*>>?, _ ->
        values?.map { entry ->
            @Suppress("UNCHECKED_CAST")
            MultiTypeDescriptor(
                index = entry.index,
                name = entry.name,
                definition = entry.definition as IsUsableInMultiType<out Any, ContainsDefinitionsContext>
            )
        }?.toList()
    },
    fromSerializable = { values ->
        values?.map {
            MultiTypeEnum(it.index, it.name, it.definition)
        }?.toTypedArray()
    },
    getter = {
        it.cases()
    }
).apply {
    addSingle(this)
}
