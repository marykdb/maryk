package maryk.core.properties.enum

import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.serializers.ObjectDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.SimpleObjectModel
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.IsUsableInMultiType
import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.internalMultiType
import maryk.core.properties.definitions.mapOfPropertyDefEmbeddedObjectDefinitions
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.values
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.core.values.SimpleObjectValues
import maryk.core.yaml.readNamedIndexField
import maryk.core.yaml.writeNamedIndexField
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.yaml.IsYamlReader
import maryk.yaml.YamlWriter

/** Interface for Enums used in types which contain a strong type */
interface MultiTypeEnum<T: Any>: TypeEnum<T> {
    val definition: IsUsableInMultiType<T, *>?

    companion object {
        internal operator fun invoke(index: UInt, name: String, definition: IsUsableInMultiType<out Any, *>?, alternativeNames: Set<String>? = null) = object : IndexedEnumImpl<IndexedEnumComparable<Any>>(index, alternativeNames), MultiTypeEnum<Any> {
            init {
                require(index > 0u) { "Only indices of 1 and higher are allowed" }
            }
            override val name = name
            @Suppress("UNCHECKED_CAST")
            override val definition = definition as IsUsableInMultiType<Any, IsPropertyContext>

            override fun equals(other: Any?) = other is MultiTypeEnum<*> && other.index == this.index && other.definition == this.definition
            override fun hashCode() = index.hashCode()

            override fun toString() = this.name
        } as MultiTypeEnum<Any>
    }

    object Model : SimpleObjectModel<MultiTypeEnum<*>, ObjectPropertyDefinitions<MultiTypeEnum<*>>>() {
        val index by number(1u, MultiTypeEnum<*>::index, UInt32)
        val name by string(2u, MultiTypeEnum<*>::name)
        val alternativeNames by set(
            index = 3u,
            valueDefinition = StringDefinition(),
            getter = MultiTypeEnum<*>::alternativeNames
        )
        val definition by internalMultiType(
            index = 4u,
            typeEnum = PropertyDefinitionType,
            definitionMap = mapOfPropertyDefEmbeddedObjectDefinitions,
            getter = {
                val def = it.definition as IsTransportablePropertyDefinitionType<*>
                TypedValue(def.propertyDefinitionType, def)
            }
        )

        override fun invoke(values: ObjectValues<MultiTypeEnum<*>, ObjectPropertyDefinitions<MultiTypeEnum<*>>>): MultiTypeEnum<*>  {
            val typedDefinition =
                values<TypedValue<PropertyDefinitionType, IsTransportablePropertyDefinitionType<*>>>(
                    definition.index
                )

            return invoke(
                values(index.index),
                values(name.index),
                typedDefinition.value as IsUsableInMultiType<out Any, *>,
                values(alternativeNames.index)
            )
        }

        override val Serializer = object: ObjectDataModelSerializer<MultiTypeEnum<*>, ObjectPropertyDefinitions<MultiTypeEnum<*>>, IsPropertyContext, IsPropertyContext>(this) {
            override fun writeObjectAsJson(
                obj: MultiTypeEnum<*>,
                writer: IsJsonLikeWriter,
                context: IsPropertyContext?,
                skip: List<IsDefinitionWrapper<*, *, *, MultiTypeEnum<*>>>?
            ) {
                // When writing YAML, use YAML optimized format with complex field names
                if (writer is YamlWriter) {
                    val typedDefinition =
                        definition.getPropertyAndSerialize(obj, context as ContainsDefinitionsContext)
                            ?: throw DefNotFoundException("Unknown type ${obj.definition} so cannot serialize contents")

                    writer.writeNamedIndexField(obj.name, obj.index, obj.alternativeNames)

                    definition.writeJsonValue(typedDefinition, writer, context)
                } else {
                    super.writeObjectAsJson(obj, writer, context, skip)
                }
            }

            override fun readJson(
                reader: IsJsonLikeReader,
                context: IsPropertyContext?
            ): SimpleObjectValues<MultiTypeEnum<*>> {
                // When reading YAML, use YAML optimized format with complex field names
                return if (reader is IsYamlReader) {
                    val valueMap = MutableValueItems()

                    reader.readNamedIndexField(valueMap, name, index, alternativeNames)
                    valueMap[definition.index] =
                        definition.readJson(reader, context as ContainsDefinitionsContext)

                    values(context as? RequestContext) {
                        valueMap
                    }
                } else {
                    super.readJson(reader, context)
                }
            }
        }
    }
}
