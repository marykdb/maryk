package maryk.core.properties.exceptions

import maryk.core.models.QueryDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue

/**
 * Umbrella for Validation Exception for properties
 * Contains a list of [exceptions] which where caught on property referred by [reference].
 */
data class ValidationUmbrellaException internal constructor(
    val reference: IsPropertyReference<*,*>?,
    val exceptions: List<ValidationException>
) : ValidationException(
    newMessage = createReason(reference, exceptions)
) {
    override val validationExceptionType = ValidationExceptionType.UMBRELLA

    internal companion object: QueryDataModel<ValidationUmbrellaException>(
        properties = object : PropertyDefinitions<ValidationUmbrellaException>() {
            init {
                ValidationException.addReference(this, ValidationUmbrellaException::reference)
                add(1, "exceptions",
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
        override fun invoke(map: Map<Int, *>) = ValidationUmbrellaException(
            reference = map(0),
            exceptions = map(1)
        )
    }
}

private fun createReason(reference: IsPropertyReference<*, *>?, exceptions: List<ValidationException>): String {
    val property = if (reference != null) " in property «${reference.completeName}»" else ""

    var messages = "Umbrella exception$property: [\n"
    for (it in exceptions) {
        messages += "\t${it.message?.replace("\n", "\n\t")}\n"
    }
    return "$messages]"
}

/** Convenience method to create a new ValidationUmbrellaException */
fun createValidationUmbrellaException(refGetter: () -> IsPropertyReference<*, *>?, exceptionCollector: (exceptionAdder: (e: ValidationException) -> Unit) -> Unit){
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
