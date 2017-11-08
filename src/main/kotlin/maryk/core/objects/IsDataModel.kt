package maryk.core.objects

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.exceptions.PropertyValidationUmbrellaException
import maryk.core.properties.references.PropertyReference

interface IsDataModel<in DO: Any> {
    /** Get the definition with a property name
     * @param name to get property of
     */
    fun getDefinition(name: String): IsPropertyDefinition<out Any>?

    /** Get the definition with a property index
     * @param index to get property of
     */
    fun getDefinition(index: Int): IsPropertyDefinition<out Any>?

    /** Get a method to retrieve property from DataObject by name
     * @param name of value to retrieve
     */
    fun getPropertyGetter(name: String): ((DO) -> Any?)?

    /** Get a method to retrieve property from DataObject by index
     * @param index of value to retrieve
     */
    fun getPropertyGetter(index: Int): ((DO) -> Any?)?

    /** Validate a DataObject
     * @param dataObject to validate
     * @param parentRefFactory parent reference factory to the model
     * @throws PropertyValidationUmbrellaException if input was invalid
     */
    @Throws(PropertyValidationUmbrellaException::class)
    fun validate(dataObject: DO, parentRefFactory: () -> PropertyReference<*, *>? = { null })

    /** Validate a map of values
     * @param map with values to validate
     * @param parentRefFactory parent reference factory to the model
     * @throws PropertyValidationUmbrellaException if input was invalid
     */
    @Throws(PropertyValidationUmbrellaException::class)
    fun validate(map: Map<Int, Any>, parentRefFactory: () -> PropertyReference<*, *>? = { null })
}