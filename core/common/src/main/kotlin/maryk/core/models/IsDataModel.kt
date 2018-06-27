package maryk.core.models

import maryk.core.objects.DataObjectMap
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference

/** A DataModel which holds properties and can be validated */
interface IsDataModel<DO: Any> {
    /** Object which contains all property definitions. Can also be used to get property references. */
    val properties: PropertyDefinitions<DO>

    /**
     * Validate a [dataObject] and get reference from [refGetter] if exception needs to be thrown
     * @throws ValidationUmbrellaException if input was invalid
     */
    fun validate(dataObject: DO, refGetter: () -> IsPropertyReference<DO, IsPropertyDefinition<DO>>? = { null })

    /**
     * Validate a [map] with values and get reference from [refGetter] if exception needs to be thrown
     * @throws ValidationUmbrellaException if input was invalid
     */
    fun validate(map: DataObjectMap<DO>, refGetter: () -> IsPropertyReference<DO, IsPropertyDefinition<DO>>? = { null })

    /** Creates a Data Object by [map] */
    operator fun invoke(map: DataObjectMap<DO>): DO
}
