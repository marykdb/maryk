package maryk.core.properties.definitions.wrapper

import maryk.core.exceptions.DefNotFoundException
import maryk.core.objects.SimpleDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitionType
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.mapOfPropertyDefSubModelDefinitions
import maryk.core.properties.definitions.mapOfPropertyDefWrappers
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.DataModelContext
import maryk.core.yaml.readNamedIndexField
import maryk.core.yaml.writeNamedIndexField
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.yaml.IsYamlReader
import maryk.yaml.YamlWriter

/**
 * Wraps a Property Definition of type [T] to give it more context [CX] about
 * DataObject [DO] which contains this Definition.
 */
interface IsPropertyDefinitionWrapper<T: Any, in CX:IsPropertyContext, in DO> : IsSerializablePropertyDefinition<T, CX> {
    val index: Int
    val name: String
    val definition: IsSerializablePropertyDefinition<T, CX>
    val getter: (DO) -> T?

    /** Get a reference to this definition inside [parentRef] */
    fun getRef(parentRef: IsPropertyReference<*, *>? = null): IsPropertyReference<T, *>

    /**
     * Validates [newValue] against [previousValue] on propertyDefinition and if fails creates
     * refernce with [parentRefFactory]
     * @throws ValidationException when encountering invalid new value
     */
    fun validate(previousValue: T? = null, newValue: T?, parentRefFactory: () -> IsPropertyReference<*, *>? = { null }) {
        this.validateWithRef(previousValue, newValue, { this.getRef(parentRefFactory()) })
    }

    /** Calculates the needed byte size to transport [value] within optional [context] and caches it with [cacher] */
    fun calculateTransportByteLengthWithKey(value: T, cacher: WriteCacheWriter, context: CX? = null) =
        this.calculateTransportByteLengthWithKey(this.index, value, cacher, context)

    /**
     * Writes [value] to bytes with [writer] for transportation and adds the key with tag and wire type.
     * Uses the [cacheGetter] to get length.
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    fun writeTransportBytesWithKey(value: T, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX? = null) =
        this.writeTransportBytesWithKey(this.index, value, cacheGetter, writer, context)

    companion object {
        private fun <DO:Any> addIndex(definitions: PropertyDefinitions<DO>, getter: (DO) -> Int) =
            definitions.add(0, "index", NumberDefinition(type = UInt32)) {
                getter(it).toUInt32()
            }

        private fun <DO:Any> addName(definitions: PropertyDefinitions<DO>, getter: (DO) -> String) =
            definitions.add(1, "name", StringDefinition(), getter)

        private fun <DO:Any> addDefinition(definitions: PropertyDefinitions<DO>, getter: (DO) -> IsSerializablePropertyDefinition<*, *>) =
            definitions.add(2, "definition", MultiTypeDefinition(
                definitionMap = mapOfPropertyDefSubModelDefinitions
            )) {
                val def = getter(it) as IsTransportablePropertyDefinitionType
                TypedValue(def.propertyDefinitionType, def)
            }
    }

    private object Properties: PropertyDefinitions<IsPropertyDefinitionWrapper<out Any, IsPropertyContext, Any>>() {
        val index = IsPropertyDefinitionWrapper.addIndex(this, IsPropertyDefinitionWrapper<*, *, *>::index)
        val name = IsPropertyDefinitionWrapper.addName(this, IsPropertyDefinitionWrapper<*, *, *>::name)
        val definition = IsPropertyDefinitionWrapper.addDefinition(this, IsPropertyDefinitionWrapper<*, *, *>::definition)
    }

    object Model : SimpleDataModel<IsPropertyDefinitionWrapper<out Any, IsPropertyContext, Any>, PropertyDefinitions<IsPropertyDefinitionWrapper<out Any, IsPropertyContext, Any>>>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>): IsPropertyDefinitionWrapper<out Any, IsPropertyContext, Any> {
            val typedDefinition =
                map<TypedValue<PropertyDefinitionType, IsPropertyDefinition<Any>>>(2)
            val type = typedDefinition.type

            return mapOfPropertyDefWrappers[type]?.invoke(
                map<UInt32>(0).toInt(),
                map(1),
                typedDefinition.value,
                { _: Any -> null }
            ) ?: throw DefNotFoundException("Property type $type not found")
        }

        override fun writeJson(
            obj: IsPropertyDefinitionWrapper<out Any, IsPropertyContext, Any>,
            writer: IsJsonLikeWriter,
            context: IsPropertyContext?
        ) {
            // When writing YAML, use YAML optimized format with complex field names
            if (writer is YamlWriter) {
                val typedDefinition = Properties.definition.getter(obj)
                        ?: throw Exception("Unknown type ${obj.definition} so cannot serialize contents")

                writer.writeNamedIndexField(obj.name, obj.index)

                Properties.definition.writeJsonValue(typedDefinition, writer, context as DataModelContext)
            } else {
                super.writeJson(obj, writer, context)
            }
        }

        override fun readJson(reader: IsJsonLikeReader, context: IsPropertyContext?): Map<Int, Any> {
            // When reading YAML, use YAML optimized format with complex field names
            return if (reader is IsYamlReader) {
                val valueMap: MutableMap<Int, Any> = mutableMapOf()

                reader.readNamedIndexField(valueMap, Properties.name, Properties.index)
                valueMap[Properties.definition.index] = Properties.definition.readJson(reader, context as DataModelContext)

                valueMap
            } else {
                super.readJson(reader, context)
            }
        }
    }
}
