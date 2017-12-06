package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.ByteLengthContainer

interface IsDataObjectProperty<T: Any, in CX:IsPropertyContext, in DM>
    : IsSerializablePropertyDefinition<T, CX> {
    val index: Int
    val name: String
    val property: IsSerializablePropertyDefinition<T, CX>
    val getter: (DM) -> T?

    /**
     * Get a reference to this definition
     * @param parentReference reference to parent property if present
     * @return Complete property reference
     */
    fun getRef(parentRefFactory: () -> IsPropertyReference<*, *>? = { null }): IsPropertyReference<T, *>

    /**
     * Validates the values on propertyDefinition
     * @param previousValue previous value for validation
     * @param newValue      new value for validation
     * @param parentRefFactory     for creating property reference to parent
     * @throws ValidationException when encountering invalid new value
     */
    fun validate(previousValue: T? = null, newValue: T?, parentRefFactory: () -> IsPropertyReference<*, *>? = { null }) {
        this.validateWithRef(previousValue, newValue, { this.getRef(parentRefFactory) })
    }

    /** Calculates the needed bytes to transport the value
     * @param value to get length of
     * @param lengthCacher to cache calculated lengths. Ordered so it can be read back in the same order
     * @param context with possible context values for Dynamic property writers
     * @return the total length
     */
    fun calculateTransportByteLengthWithKey(value: T, lengthCacher: (length: ByteLengthContainer) -> Unit, context: CX? = null)
            = this.calculateTransportByteLengthWithKey(this.index, value, lengthCacher, context)

    /** Convert a value to bytes for transportation and adds the key with tag and wiretype
     * @param value to write
     * @param lengthCacheGetter to fetch next cached length
     * @param writer to write bytes to
     * @param context (optional) with context parameters for conversion (for dynamically dependent properties)
     */
    fun writeTransportBytesWithKey(value: T, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit, context: CX? = null)
            = this.writeTransportBytesWithKey(this.index, value, lengthCacheGetter, writer, context)
}