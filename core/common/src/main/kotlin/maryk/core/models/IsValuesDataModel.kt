package maryk.core.models

import maryk.core.objects.Values
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference

interface IsValuesDataModel<P: PropertyDefinitions>: IsDataModel<P>, IsNamedDataModel<P>

/** A DataModel which holds properties and can be validated */
interface IsTypedValuesDataModel<DM: IsValuesDataModel<P>, P: PropertyDefinitions>: IsValuesDataModel<P> {
    /**
     * Validate a [map] with values and get reference from [refGetter] if exception needs to be thrown
     * @throws ValidationUmbrellaException if input was invalid
     */
    fun validate(map: Values<DM, P>, refGetter: () -> IsPropertyReference<Values<DM, P>, IsPropertyDefinition<Values<DM, P>>, *>? = { null })

    /** Create a ObjectValues with given [createMap] function */
    @Suppress("UNCHECKED_CAST")
    fun map(createMap: P.() -> Map<Int, Any?>) = Values(this as DM, createMap(this.properties))
}
