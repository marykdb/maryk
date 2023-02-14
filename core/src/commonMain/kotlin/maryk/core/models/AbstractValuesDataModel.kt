package maryk.core.models

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.createValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.Values

abstract class SimpleDataModel<DM : IsValuesDataModel<P>, P : PropertyDefinitions>(
    reservedIndices: List<UInt>? = null,
    reservedNames: List<String>? = null,
    properties: P
) : AbstractValuesDataModel<DM, P, IsPropertyContext>(reservedIndices, reservedNames, properties)
typealias ValuesDataModelImpl<CX> = AbstractValuesDataModel<IsValuesDataModel<PropertyDefinitions>, PropertyDefinitions, CX>

/**
 * A Data Model for converting and validating DataObjects. The [properties] contain all the property definitions for
 * this Model of type [DM]. [CX] is the context to be used on the properties
 * to read and write. This can be different because the DataModel can create
 * its own context by transforming the given context.
 */
abstract class AbstractValuesDataModel<DM : IsValuesDataModel<P>, P : PropertyDefinitions, CX : IsPropertyContext> internal constructor(
    final override val reservedIndices: List<UInt>? = null,
    final override val reservedNames: List<String>? = null,
    properties: P
) : IsTypedValuesDataModel<DM, P>, AbstractDataModel<Any, P, Values<DM, P>, CX, CX>(properties) {

    override fun validate(
        values: Values<DM, P>,
        refGetter: () -> IsPropertyReference<Values<DM, P>, IsPropertyDefinition<Values<DM, P>>, *>?
    ) {
        createValidationUmbrellaException(refGetter) { addException ->
            for ((index, orgValue) in values.values) {
                val definition = properties[index] ?: continue
                val value = values.process<Any?>(definition, orgValue, true) { true } ?: continue // skip empty values
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
