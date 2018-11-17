package maryk.core.models

import maryk.core.objects.Values
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.createValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference

typealias SimpleDataModel<DM, P> = AbstractValuesDataModel<DM, P, IsPropertyContext>
typealias ValuesDataModelImpl<CX> = AbstractValuesDataModel<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions, CX>

/**
 * A Data Model for converting and validating DataObjects. The [properties] contain all the property definitions for
 * this Model of type [DM]. [CX] is the context to be used on the properties
 * to read and write. This can be different because the DataModel can create
 * its own context by transforming the given context.
 */
abstract class AbstractValuesDataModel<DM: IsValuesDataModel<P>, P: PropertyDefinitions, CX: IsPropertyContext> internal constructor(
    properties: P
) : IsTypedValuesDataModel<DM, P>, AbstractDataModel<Any, P, Values<DM, P>, CX, CX>(properties) {

    override fun validate(
        map: Values<DM, P>,
        refGetter: () -> IsPropertyReference<Values<DM, P>, IsPropertyDefinition<Values<DM, P>>, *>?
    ) {
        createValidationUmbrellaException(refGetter) { addException ->
            for ((key, orgValue) in map.map) {
                val definition = properties[key] ?: continue
                val value = map.process<Any?>(definition, orgValue) ?: continue // skip empty values
                try {
                    definition.validate(
                        newValue = value,
                        parentRefFactory = refGetter
                    )
                } catch (e: ValidationException) {
                    addException(e)
                }
            }
        }
    }
}
