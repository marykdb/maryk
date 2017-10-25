package maryk.core.properties.definitions

import maryk.core.json.JsonReader
import maryk.core.json.JsonWriter
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.exceptions.PropertyValidationException
import maryk.core.properties.references.PropertyReference
import maryk.core.protobuf.ByteSizeContainer

/**
 * Interface to define this is a property definition
 * @param <T> Type of Property contained in the definition
 */
interface IsPropertyDefinition<T: Any> {
    /** The name of the property definition */
    val name: String?

    /** The index position of this property definition */
    val index: Int

    /**
     * Get a reference to this definition
     * @param parentReference reference to parent property if present
     * @return Complete property reference
     */
    fun getRef(parentRefFactory: () -> PropertyReference<*, *>? = { null }):
            PropertyReference<T, IsPropertyDefinition<T>>

    /**
     * Validates the values on propertyDefinition
     * @param parentRefFactory     for creating property reference to parent
     * @param previousValue previous value for validation
     * @param newValue      new value for validation
     * @throws PropertyValidationException when property is invalid
     */
    @Throws(PropertyValidationException::class)
    fun validate(previousValue: T? = null, newValue: T?, parentRefFactory: () -> PropertyReference<*, *>? = { null })

    /** Writes a value to Json
     * @param writer: to write json to
     * @param value: value to write
     */
    fun writeJsonValue(writer: JsonWriter, value: T)

    /** Reads JSON and returns values
     * @param reader: to read JSON from
     */
    @Throws(ParseException::class)
    fun readJson(reader: JsonReader): T

    /** Calculates the needed bytes to transport the value
     * @param value to get size of
     * @param lengthCacher to cache calculated lengths. Ordered so it can be read back in the same order
     * @return the total size
     */
    fun calculateTransportByteLengthWithKey(value: T, lengthCacher: (size: ByteSizeContainer) -> Unit) : Int

    /** Convert a value to bytes for transportation and adds the key with tag and wiretype
     * @param value to write
     * @param lengthCacheGetter to get next cached length
     * @param writer to write bytes to
     */
    fun writeTransportBytesWithKey(value: T, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit)
}