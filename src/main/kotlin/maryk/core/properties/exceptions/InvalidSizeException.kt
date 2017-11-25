package maryk.core.properties.exceptions

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.numeric.SInt32

/** Exception for properties which got a wrong new size. */
data class InvalidSizeException(
        val reference: IsPropertyReference<*, *>,
        val value: String,
        val min: Int?,
        val max: Int?
) : ValidationException(
        reference = reference,
        reason = "has incorrect size: «$value» with size limits [$min,$max]"
) {
    override val validationExceptionType = ValidationExceptionType.INVALID_SIZE

    internal object Properties {
        val min = NumberDefinition("min", 2, type = SInt32)
        val max = NumberDefinition("max", 3, type = SInt32)
    }

    companion object: QueryDataModel<InvalidSizeException>(
            construct = {
                InvalidSizeException(
                        reference = it[0] as IsPropertyReference<*, *>,
                        value = it[1] as String,
                        min = it[2] as Int?,
                        max = it[3] as Int?
                )
            },
            definitions = listOf(
                    Def(ValidationException.Properties.reference, InvalidSizeException::reference),
                    Def(ValidationException.Properties.value, InvalidSizeException::value),
                    Def(Properties.min, InvalidSizeException::min),
                    Def(Properties.max, InvalidSizeException::max)
            )
    )
}
