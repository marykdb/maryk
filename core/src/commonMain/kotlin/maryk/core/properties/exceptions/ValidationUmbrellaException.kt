package maryk.core.properties.exceptions

import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.types.TypedValue

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
    override val validationExceptionType = ValidationExceptionType.UMBRELLA

    internal companion object: SimpleQueryDataModel<ValidationUmbrellaException>(
        properties = object : ObjectPropertyDefinitions<ValidationUmbrellaException>() {
            init {
                ValidationException.addReference(this, ValidationUmbrellaException::reference)
                add(2, "exceptions",
                    ListDefinition(
                        default = emptyList(),
                        valueDefinition = MultiTypeDefinition(
                            typeEnum = ValidationExceptionType,
                            definitionMap = mapOfValidationExceptionDefinitions
                        )
                    ),
                    getter = ValidationUmbrellaException::exceptions,
                    toSerializable = { TypedValue(it.validationExceptionType, it) },
                    fromSerializable = { it.value as ValidationException }
                )
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<ValidationUmbrellaException>) = ValidationUmbrellaException(
            reference = map(1),
            exceptions = map(2)
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
fun createValidationUmbrellaException(refGetter: () -> AnyPropertyReference?, exceptionCollector: (exceptionAdder: (e: ValidationException) -> Unit) -> Unit){
    var hasExceptions = false
    val exceptions by lazy {
        hasExceptions = true
        mutableListOf<ValidationException>()
    }

    exceptionCollector {
        exceptions.add(it)
    }

    if (hasExceptions) {
        throw ValidationUmbrellaException(refGetter(), exceptions)
    }
}
