package maryk.core.properties.exceptions

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.references.IsPropertyReference

/** Exception for when a value was out of range.
 *
 * This can be both of value or for the size of value containers like List or
 * Map
 *
 * @param reference   of property
 * @param value which was invalid
 * @param min   minimum of range
 * @param max   maximum of range
 */
data class OutOfRangeException(
        val reference: IsPropertyReference<*, *>,
        val value: String,
        val min: String?,
        val max: String?
) : ValidationException(
        reference = reference,
        reason = "is out of range: «$value» with range [$min,$max]"
) {
    override val validationExceptionType = ValidationExceptionType.OUT_OF_RANGE

    internal object Properties {
        val min = StringDefinition("min", 2)
        val max = StringDefinition("max", 3)
    }

    companion object: QueryDataModel<OutOfRangeException>(
            construct = {
                OutOfRangeException(
                        reference = it[0] as IsPropertyReference<*, *>,
                        value = it[1] as String,
                        min = it[2] as String?,
                        max = it[3] as String?
                )
            },
            definitions = listOf(
                    Def(ValidationException.Properties.reference, OutOfRangeException::reference),
                    Def(ValidationException.Properties.value, OutOfRangeException::value),
                    Def(Properties.min, OutOfRangeException::min),
                    Def(Properties.max, OutOfRangeException::max)
            )
    )
}
