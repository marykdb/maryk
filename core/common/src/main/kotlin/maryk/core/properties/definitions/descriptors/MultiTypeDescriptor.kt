package maryk.core.properties.definitions.descriptors

import maryk.core.objects.SimpleDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.properties.definitions.mapOfPropertyDefSubModelDefinitions
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ValuePropertyReference
import maryk.core.properties.types.IndexedEnum
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.query.DataModelContext
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
    val index: Int,
    val name: String,
    val definition: IsSubDefinition<out Any, IsPropertyContext>
) {
    private object Properties: PropertyDefinitions<MultiTypeDescriptor>() {
        val index = add(0, "index", NumberDefinition(type = UInt32), MultiTypeDescriptor::index, { it?.toUInt32() }, { it?.toInt() })
        val name = add(1, "name", StringDefinition(), MultiTypeDescriptor::name)

        val definition = add(
            2, "definition",
            MultiTypeDefinition(
                typeEnum = PropertyDefinitionType,
                definitionMap = mapOfPropertyDefSubModelDefinitions
            ),
            getter = {
                val defType = it.definition as IsTransportablePropertyDefinitionType<*>
                TypedValue(defType.propertyDefinitionType, defType)
            }
        )
    }

    internal object Model : SimpleDataModel<MultiTypeDescriptor, PropertyDefinitions<MultiTypeDescriptor>>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = MultiTypeDescriptor(
            index = map(0),
            name = map(1),
            definition = map<TypedValue<IndexedEnum<Any>, IsSubDefinition<out Any, IsPropertyContext>>>(2).value
        )

        override fun readJson(reader: IsJsonLikeReader, context: IsPropertyContext?): Map<Int, Any> {
            // When writing YAML, use YAML optimized format with complex field names
            return if (reader is IsYamlReader) {
                val valueMap: MutableMap<Int, Any> = mutableMapOf()
                reader.readNamedIndexField(valueMap, Properties.name, Properties.index)

                valueMap[Properties.definition.index] = Properties.definition.readJson(reader, context as DataModelContext?)
                valueMap
            } else {
                super.readJson(reader, context)
            }
        }

        override fun writeJson(obj: MultiTypeDescriptor, writer: IsJsonLikeWriter, context: IsPropertyContext?) {
            // When writing YAML, use YAML optimized format with complex field names
            if (writer is YamlWriter) {
                val typedDefinition = Properties.definition.getPropertyAndSerialize(obj)
                        ?: throw Exception("Unknown type ${obj.definition} so cannot serialize contents")

                writer.writeNamedIndexField(obj.name, obj.index)

                Properties.definition.writeJsonValue(typedDefinition, writer, context as DataModelContext?)
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
    override val searchable: Boolean = true
    override val required: Boolean = true
    override val final: Boolean = false
    override val minSize: Int? = null
    override val maxSize: Int? = null

    override val propertyDefinitionType = PropertyDefinitionType.List

    override fun newMutableCollection(context: IsPropertyContext?) = mutableListOf<MultiTypeDescriptor>()

    override fun validateCollectionForExceptions(
        refGetter: () -> IsPropertyReference<List<MultiTypeDescriptor>, IsPropertyDefinition<List<MultiTypeDescriptor>>>?,
        newValue: List<MultiTypeDescriptor>,
        validator: (item: MultiTypeDescriptor, itemRefFactory: () -> IsPropertyReference<MultiTypeDescriptor, IsPropertyDefinition<MultiTypeDescriptor>>?) -> Any
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
    override val definition: MultiTypeDescriptorListDefinition,
    override val toSerializable: ((List<MultiTypeDescriptor>?) -> List<MultiTypeDescriptor>?)? = null,
    override val fromSerializable: ((List<MultiTypeDescriptor>?) -> List<MultiTypeDescriptor>?)? = null,
    override val capturer: ((IsPropertyContext, List<MultiTypeDescriptor>) -> Unit)? = null,
    override val getter: (MultiTypeDefinition<IndexedEnum<Any>, IsPropertyContext>) -> List<MultiTypeDescriptor>?
) :
    IsCollectionDefinition<MultiTypeDescriptor, List<MultiTypeDescriptor>, IsPropertyContext, IsValueDefinition<MultiTypeDescriptor, IsPropertyContext>> by definition,
    IsPropertyDefinitionWrapper<List<MultiTypeDescriptor>, List<MultiTypeDescriptor>, IsPropertyContext, MultiTypeDefinition<IndexedEnum<Any>, IsPropertyContext>>
{
    override fun getRef(parentRef: IsPropertyReference<*, *>?) =
        ValuePropertyReference(this, parentRef)
}

/**
 * Add a descriptor of multi types to a MultiTypeDefinition PropertyDefinitions
 * Set [index] and [name] to append it to model
 */
internal fun PropertyDefinitions<MultiTypeDefinition<*, *>>.addDescriptorPropertyWrapperWrapper(
    index: Int,
    name: String
) {
    MultiTypeDescriptorPropertyDefinitionWrapper(index, name, MultiTypeDescriptorListDefinition(
        valueDefinition =  SubModelDefinition(
            dataModel = { MultiTypeDescriptor.Model }
        )
    )) {
        it.definitionMap.map {
            MultiTypeDescriptor(
                index = it.key.index,
                name = it.key.name,
                definition = it.value
            )
        }.toList()
    }.apply {
        @Suppress("UNCHECKED_CAST")
        addSingle(this as IsPropertyDefinitionWrapper<out Any, *, *, MultiTypeDefinition<*, *>>)
    }
}

/**
 * Convert multi type descriptors in a list in [value] to an indexed map of definitions.
 * Will throw an exception if it fails to convert
 */
@Suppress("UNCHECKED_CAST")
internal fun convertMultiTypeDescriptors(value: Any?): Map<IndexedEnum<Any>, IsSubDefinition<out Any, IsPropertyContext>> {
    val descriptorList = value as? List<MultiTypeDescriptor>
            ?: throw ParseException("Multi type definition descriptor cannot be empty")

    return descriptorList.map {
        Pair(
            IndexedEnum(it.index, it.name) as IndexedEnum<Any>,
            it.definition
        )
    }.toMap()
}
