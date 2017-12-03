package maryk.core.properties.exceptions

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.references.IsPropertyReference

/**
 * Exception for when properties are set with invalid input.
 */
data class InvalidValueException(
        val reference: IsPropertyReference<*, *>,
        val value: String
) : ValidationException(
        reference = reference,
        reason = "has invalid value: «$value»"
) {
    override val validationExceptionType = ValidationExceptionType.INVALID_VALUE

    companion object: QueryDataModel<InvalidValueException>(
            properties = object: PropertyDefinitions<InvalidValueException>() {
                init {
                    ValidationException.addReference(this, InvalidValueException::reference)
                    ValidationException.addValue(this, InvalidValueException::value)
                }
            },
            definitions = listOf(
                    Def(Properties.reference, InvalidValueException::reference),
                    Def(Properties.value, InvalidValueException::value)
            )
    ) {
        override fun invoke(map: Map<Int, *>) = InvalidValueException(
                reference = map[0] as IsPropertyReference<*, *>,
                value = map[1] as String
        )
    }
}
