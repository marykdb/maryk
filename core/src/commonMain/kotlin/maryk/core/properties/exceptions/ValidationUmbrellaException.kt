package maryk.core.properties.exceptions

import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.exceptions.ValidationExceptionType.UMBRELLA
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.values.SimpleObjectValues

/**
 * Umbrella for Validation Exception for properties
 * Contains a list of [exceptions] which where caught on property referred by [reference].
 */
data class ValidationUmbrellaException internal constructor(
    val reference: AnyPropertyReference?,
    val exceptions: List<ValidationException>
) : ValidationException(
    newMessage = createReason(reference, exceptions)
) {
    override val validationExceptionType = UMBRELLA

    @Suppress("unused")
    internal companion object : SimpleQueryDataModel<ValidationUmbrellaException>(
        properties = object : ObjectPropertyDefinitions<ValidationUmbrellaException>() {
            val reference by addReference(ValidationUmbrellaException::reference)
            val exceptions by list(
                index = 2u,
                default = emptyList(),
                valueDefinition = InternalMultiTypeDefinition(
                    typeEnum = ValidationExceptionType,
                    definitionMap = mapOfValidationExceptionDefinitions
                ),
                getter = ValidationUmbrellaException::exceptions,
                toSerializable = { TypedValue(it.validationExceptionType, it) },
                fromSerializable = { it.value }
            )
        }
    ) {
        override fun invoke(values: SimpleObjectValues<ValidationUmbrellaException>) = ValidationUmbrellaException(
            reference = values(1u),
            exceptions = values(2u)
        )
    }
}

private fun createReason(reference: AnyPropertyReference?, exceptions: List<ValidationException>): String {
    val property = if (reference != null) " in property «${reference.completeName}»" else ""

    var messages = "Umbrella exception$property: [\n"
    for (it in exceptions) {
        messages += "\t${it.message?.replace("\n", "\n\t")}\n"
    }
    return "$messages]"
}

/** Convenience method to create a new ValidationUmbrellaException */
fun createValidationUmbrellaException(
    refGetter: () -> AnyPropertyReference?,
    exceptionCollector: (exceptionAdder: (e: ValidationException) -> Unit) -> Unit
) {
    var exceptions: MutableList<ValidationException>? = null

    exceptionCollector {
        when (val ex = exceptions) {
            null -> exceptions = mutableListOf(it)
            else -> ex.add(it)
        }
    }

    // If set throw umbrella exception
    exceptions?.let {
        throw ValidationUmbrellaException(refGetter(), it)
    }
}
