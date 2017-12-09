package maryk.core.properties.exceptions

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue

/** Umbrella for Validation Exception for properties
 * Contains a list of exceptions which where catched. */
data class ValidationUmbrellaException(
        val reference: IsPropertyReference<*,*>?,
        val exceptions: List<ValidationException>
) : ValidationException(
        newMessage = createReason(reference, exceptions)
) {
    override val validationExceptionType = ValidationExceptionType.UMBRELLA

    companion object: QueryDataModel<ValidationUmbrellaException>(
            properties = object : PropertyDefinitions<ValidationUmbrellaException>() {
                init {
                    ValidationException.addReference(this, ValidationUmbrellaException::reference)
                    add(1, "exceptions", ListDefinition(
                            valueDefinition = MultiTypeDefinition(
                                    definitionMap = mapOfValidationExceptionDefinitions
                            )
                    )) {
                        it.exceptions.map { TypedValue(it.validationExceptionType.index, it) }
                    }
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = ValidationUmbrellaException(
                reference = map[0] as IsPropertyReference<*, *>?,
                exceptions = (map[1] as List<TypedValue<ValidationException>>?)?.map { it.value } ?: emptyList()
        )
    }
}

private fun createReason(reference: IsPropertyReference<*, *>?, exceptions: List<ValidationException>): String {
    val property = if (reference != null) " in property «${reference.completeName}»" else ""

    var messages = "Umbrella exception$property: [\n"
    exceptions.forEach {
        messages += "\t${it.message?.replace("\n", "\n\t")}\n"
    }
    return messages + "]"
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
