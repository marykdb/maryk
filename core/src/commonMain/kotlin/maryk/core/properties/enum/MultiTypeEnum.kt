package maryk.core.properties.enum

import maryk.core.exceptions.DefNotFoundException
import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.IsUsableInMultiType
import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.internalMultiType
import maryk.core.properties.definitions.mapOfPropertyDefEmbeddedObjectDefinitions
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.set
import maryk.core.properties.definitions.string
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.values.MutableValueItems
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
            override val definition: IsUsableInMultiType<Any, IsPropertyContext>? = definition as IsUsableInMultiType<Any, IsPropertyContext>

            override fun equals(other: Any?) = other is MultiTypeEnum<*> && other.index == this.index && other.definition == this.definition
            override fun hashCode() = index.hashCode()

            override fun toString() = this.name
        } as MultiTypeEnum<Any>
    }

    private object Properties :
        ObjectPropertyDefinitions<MultiTypeEnum<*>>() {
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
    }

    object Model :
        SimpleObjectDataModel<MultiTypeEnum<*>, ObjectPropertyDefinitions<MultiTypeEnum<*>>>(
            properties = Properties
        ) {
        override fun invoke(values: SimpleObjectValues<MultiTypeEnum<*>>): MultiTypeEnum<*> {
            val typedDefinition =
                values<TypedValue<PropertyDefinitionType, IsTransportablePropertyDefinitionType<*>>>(
                    Properties.definition.index
                )

            return invoke(
                values(Properties.index.index),
                values(Properties.name.index),
                typedDefinition.value as IsUsableInMultiType<out Any, *>,
                values(Properties.alternativeNames.index)
            )
        }

        override fun writeJson(
            obj: MultiTypeEnum<*>,
            writer: IsJsonLikeWriter,
            context: IsPropertyContext?
        ) {
            // When writing YAML, use YAML optimized format with complex field names
            if (writer is YamlWriter) {
                val typedDefinition =
                    Properties.definition.getPropertyAndSerialize(obj, context as ContainsDefinitionsContext)
                        ?: throw DefNotFoundException("Unknown type ${obj.definition} so cannot serialize contents")

                writer.writeNamedIndexField(obj.name, obj.index, obj.alternativeNames)

                Properties.definition.writeJsonValue(typedDefinition, writer, context)
            } else {
                super.writeJson(obj, writer, context)
            }
        }

        override fun readJson(
            reader: IsJsonLikeReader,
            context: IsPropertyContext?
        ): SimpleObjectValues<MultiTypeEnum<*>> {
            // When reading YAML, use YAML optimized format with complex field names
            return if (reader is IsYamlReader) {
                val valueMap = MutableValueItems()

                reader.readNamedIndexField(valueMap, Properties.name, Properties.index, Properties.alternativeNames)
                valueMap[Properties.definition.index] =
                    Properties.definition.readJson(reader, context as ContainsDefinitionsContext)

                this.values(context as? RequestContext) {
                    valueMap
                }
            } else {
                super.readJson(reader, context)
            }
        }
    }
}
