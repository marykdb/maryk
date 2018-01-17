package maryk.core.objects

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference

interface IsDataModel<DO: Any> {
    /** Object which contains all property definitions. Can also be used to get property references. */
    val properties: PropertyDefinitions<DO>

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