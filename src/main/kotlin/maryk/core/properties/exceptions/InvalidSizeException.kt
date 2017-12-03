package maryk.core.properties.exceptions

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
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

    internal object Properties : PropertyDefinitions<InvalidSizeException>() {
        val min = NumberDefinition("min", 2, type = SInt32)
        val max = NumberDefinition("max", 3, type = SInt32)
    }

    companion object: QueryDataModel<InvalidSizeException>(
            properties = object: PropertyDefinitions<InvalidSizeException>() {
                init {
                    ValidationException.addReference(this, InvalidSizeException::reference)
                    ValidationException.addValue(this, InvalidSizeException::value)
                    add(2, "min", NumberDefinition(type = SInt32), InvalidSizeException::min)
                    add(3, "max", NumberDefinition(type = SInt32), InvalidSizeException::max)
                }
            },
            definitions = listOf(
                    Def(ValidationException.Properties.reference, InvalidSizeException::reference),
                    Def(ValidationException.Properties.value, InvalidSizeException::value),
                    Def(Properties.min, InvalidSizeException::min),
                    Def(Properties.max, InvalidSizeException::max)
            )
    ) {
        override fun invoke(map: Map<Int, *>) = InvalidSizeException(
                reference = map[0] as IsPropertyReference<*, *>,
                value = map[1] as String,
                min = map[2] as Int?,
                max = map[3] as Int?
        )
    }
}
