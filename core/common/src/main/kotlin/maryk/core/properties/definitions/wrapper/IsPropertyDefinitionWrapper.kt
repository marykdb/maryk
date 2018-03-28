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
        private fun <DO:Any> addIndex(definitions: PropertyDefinitions<DO>, getter: (DO) -> Int) {
            definitions.add(0, "index", NumberDefinition(type = UInt32)) {
                getter(it).toUInt32()
            }
        }

        private fun <DO:Any> addName(definitions: PropertyDefinitions<DO>, getter: (DO) -> String) {
            definitions.add(1, "name", StringDefinition(), getter)
        }

        private fun <DO:Any> addDefinition(definitions: PropertyDefinitions<DO>, getter: (DO) -> IsSerializablePropertyDefinition<*, *>) {
            definitions.add(2, "definition", MultiTypeDefinition(
                definitionMap = mapOfPropertyDefSubModelDefinitions
            )) {
                val def = getter(it) as IsTransportablePropertyDefinitionType
                TypedValue(def.propertyDefinitionType, def)
            }
        }
    }

    object Model : SimpleDataModel<IsPropertyDefinitionWrapper<out Any, IsPropertyContext, Any>, PropertyDefinitions<IsPropertyDefinitionWrapper<out Any, IsPropertyContext, Any>>>(
        properties = object : PropertyDefinitions<IsPropertyDefinitionWrapper<out Any, IsPropertyContext, Any>>() {
            init {
                IsPropertyDefinitionWrapper.addIndex(this, IsPropertyDefinitionWrapper<*, *, *>::index)
                IsPropertyDefinitionWrapper.addName(this, IsPropertyDefinitionWrapper<*, *, *>::name)
                IsPropertyDefinitionWrapper.addDefinition(this, IsPropertyDefinitionWrapper<*, *, *>::definition)
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>): IsPropertyDefinitionWrapper<out Any, IsPropertyContext, Any> {
            val typedDefinition = map[2] as TypedValue<PropertyDefinitionType, IsPropertyDefinition<Any>>
            val type = typedDefinition.type

            return mapOfPropertyDefWrappers[type]?.invoke(
                (map[0] as UInt32).toInt(),
                map[1] as String,
                typedDefinition.value,
                { _: Any -> null }
            ) ?: throw DefNotFoundException("Property type $type not found")
        }
    }
}