package maryk.core.properties.definitions

import maryk.core.json.JsonGenerator
import maryk.core.json.JsonParser
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.exceptions.PropertyValidationException
import maryk.core.properties.references.PropertyReference

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
     * @param generator: to write json to
     * @param value: value to write
     */
    fun writeJsonValue(generator: JsonGenerator, value: T)

    /** Reads JSON and returns values
     * @param parser: to parse JSON from
     */
    @Throws(ParseException::class)
    fun parseFromJson(parser: JsonParser): T
}