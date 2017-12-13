package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.mapOfPropertyDefSubModelDefinitions
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/** Wraps a Property Definition to give it more context about DataObject which contains this Definition.
 * @param index: of definition to encode into protobuf
 * @param name: of definition to display in human readable format
 * @param definition: to be wrapped for DataObject
 * @param getter: to get property value on a DataObject
 *
 * @param T: value type of property
 * @param CX: Context type for property
 * @param DO: Type of DataObject which contains this property
 */
interface IsPropertyDefinitionWrapper<T: Any, in CX:IsPropertyContext, in DO>
    : IsSerializablePropertyDefinition<T, CX> {
    val index: Int
    val name: String
    val definition: IsSerializablePropertyDefinition<T, CX>
    val getter: (DO) -> T?

    /**
     * Get a reference to this definition
     * @param parentRef reference to parent property if present
     * @return Complete property reference
     */
    fun getRef(parentRef: IsPropertyReference<*, *>? = null): IsPropertyReference<T, *>

    /**
     * Validates the values on propertyDefinition
     * @param previousValue previous value for validation
     * @param newValue      new value for validation
     * @param parentRefFactory     for creating property reference to parent
     * @throws ValidationException when encountering invalid new value
     */
    fun validate(previousValue: T? = null, newValue: T?, parentRefFactory: () -> IsPropertyReference<*, *>? = { null }) {
        this.validateWithRef(previousValue, newValue, { this.getRef(parentRefFactory()) })
    }

    /** Calculates the needed bytes to transport the value
     * @param value to get length of
     * @param lengthCacher to cache calculated lengths. Ordered so it can be read back in the same order
     * @param context with possible context values for Dynamic property writers
     * @return the total length
     */
    fun calculateTransportByteLengthWithKey(value: T, cacher: WriteCacheWriter, context: CX? = null)
            = this.calculateTransportByteLengthWithKey(this.index, value, cacher, context)

    /** Convert a value to bytes for transportation and adds the key with tag and wiretype
     * @param value to write
     * @param lengthCacheGetter to fetch next cached length
     * @param writer to write bytes to
     * @param context (optional) with context parameters for conversion (for dynamically dependent properties)
     */
    fun writeTransportBytesWithKey(value: T, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX? = null)
            = this.writeTransportBytesWithKey(this.index, value, cacheGetter, writer, context)

    companion object {
        internal fun <DO:Any> addIndex(definitions: PropertyDefinitions<DO>, getter: (DO) -> Int) {
            definitions.add(0, "index", NumberDefinition(type = UInt32)) {
                getter(it).toUInt32()
            }
        }

        internal fun <DO:Any> addName(definitions: PropertyDefinitions<DO>, getter: (DO) -> String) {
            definitions.add(1, "name", StringDefinition(), getter)
        }

        internal fun <DO:Any> addDefinition(definitions: PropertyDefinitions<DO>, getter: (DO) -> IsSerializablePropertyDefinition<*, *>) {
            definitions.add(2, "definition", MultiTypeDefinition(
                    definitionMap = mapOfPropertyDefSubModelDefinitions
            )) {
                val def = getter(it) as IsTransportablePropertyDefinitionType
                TypedValue(def.propertyDefinitionType.index, def)
            }
        }
    }
}