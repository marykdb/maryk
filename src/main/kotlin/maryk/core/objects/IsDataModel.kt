package maryk.core.objects

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference

interface IsDataModel<DO: Any> {
    /** Get the definition with a property name
     * @param name to get property of
     */
    fun getDefinition(name: String): IsPropertyDefinitionWrapper<*, *, DO>?

    /** Get the definition with a property index
     * @param index to get property of
     */
    fun getDefinition(index: Int): IsPropertyDefinitionWrapper<*, *, DO>?

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
     * @param refGetter reference factory to the model
     * @throws ValidationUmbrellaException if input was invalid
     */
    fun validate(dataObject: DO, refGetter: () -> IsPropertyReference<DO, IsPropertyDefinition<DO>>? = { null })

    /** Validate a map of values
     * @param map with values to validate
     * @param refGetter reference factory to the model
     * @throws ValidationUmbrellaException if input was invalid
     */
    fun validate(map: Map<Int, Any>, refGetter: () -> IsPropertyReference<DO, IsPropertyDefinition<DO>>? = { null })
}