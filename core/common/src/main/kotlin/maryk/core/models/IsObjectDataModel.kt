package maryk.core.models

import maryk.core.objects.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference

typealias IsSimpleObjectDataModel<DO> = IsObjectDataModel<DO, ObjectPropertyDefinitions<DO>>

/** A DataModel which holds properties and can be validated */
interface IsObjectDataModel<DO: Any, P: ObjectPropertyDefinitions<DO>>: IsDataModel<P> {
    /**
     * Validate a [dataObject] and get reference from [refGetter] if exception needs to be thrown
     * @throws ValidationUmbrellaException if input was invalid
     */
    fun validate(dataObject: DO, refGetter: () -> IsPropertyReference<DO, IsPropertyDefinition<DO>>? = { null })

    /** Creates a Data Object by [map] */
    operator fun invoke(map: ObjectValues<DO, P>): DO

    /** Create a ObjectValues with given [createMap] function */
    fun map(createMap: P.() -> Map<Int, Any?>) = ObjectValues(this, createMap(this.properties))
}