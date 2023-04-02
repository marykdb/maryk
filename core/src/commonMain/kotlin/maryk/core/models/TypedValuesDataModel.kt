package maryk.core.models

import maryk.core.models.serializers.DataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.createValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.MutableValueItems
import maryk.core.values.ValueItem
import maryk.core.values.Values

abstract class TypedValuesDataModel<DM: IsValuesDataModel> : ValuesDataModel(), IsTypedValuesDataModel<DM> {
    @Suppress("UNCHECKED_CAST", "LeakingThis")
    override val Serializer = DataModelSerializer<Any, Values<DM>, DM, IsPropertyContext>(this as DM)

    /**
     * Create a new [Values] object with [pairs] and set defaults if [setDefaults] is true
     */
    @Suppress("UNCHECKED_CAST")
    fun create(
        vararg pairs: ValueItem?,
        setDefaults: Boolean = true,
    ) = Values(
        this as DM,
        MutableValueItems().apply {
            fillWithPairs(this@TypedValuesDataModel, pairs, setDefaults)
        }
    )

    override fun validate(
        values: Values<DM>,
        refGetter: () -> IsPropertyReference<Values<DM>, IsPropertyDefinition<Values<DM>>, *>?
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
