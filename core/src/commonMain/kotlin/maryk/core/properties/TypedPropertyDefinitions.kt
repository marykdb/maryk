package maryk.core.properties

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.exceptions.createValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.MutableValueItems
import maryk.core.values.ValueItem
import maryk.core.values.Values

interface IsTypedValuePropertyDefinitions<DM: IsValuesDataModel<P>, P: IsValuesPropertyDefinitions>: IsValuesPropertyDefinitions {
    /**
     * Validate a [map] with values and get reference from [refGetter] if exception needs to be thrown
     * @throws ValidationUmbrellaException if input was invalid
     */
    fun validate(
        values: Values<P>,
        refGetter: () -> IsPropertyReference<Values<P>, IsPropertyDefinition<Values<P>>, *>? = { null }
    )
}

abstract class TypedPropertyDefinitions<DM: IsValuesDataModel<P>, P: IsValuesPropertyDefinitions> : PropertyDefinitions(), IsTypedValuePropertyDefinitions<DM, P> {
    abstract override val Model : DM

    /**
     * Create a new [Values] object with [pairs] and set defaults if [setDefaults] is true
     */
    @Suppress("UNCHECKED_CAST")
    fun create(
        vararg pairs: ValueItem?,
        setDefaults: Boolean = true,
    ) = Values(
        this as P,
        MutableValueItems().apply {
            fillWithPairs(this@TypedPropertyDefinitions, pairs, setDefaults)
        }
    )

    override fun validate(
        values: Values<P>,
        refGetter: () -> IsPropertyReference<Values<P>, IsPropertyDefinition<Values<P>>, *>?
    ) {
        createValidationUmbrellaException(refGetter) { addException ->
            for ((index, orgValue) in values.values) {
                val definition = this[index] ?: continue
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
