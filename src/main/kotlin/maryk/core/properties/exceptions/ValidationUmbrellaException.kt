package maryk.core.properties.exceptions

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.DataModelPropertyContext

/** Umbrella for Validation Exception for properties
 * Contains a list of exceptions which where catched. */
data class ValidationUmbrellaException(
        val reference: IsPropertyReference<*,*>?,
        val exceptions: List<ValidationException>
) : ValidationException(
        newMessage = createReason(reference, exceptions)
) {
    override val validationExceptionType = ValidationExceptionType.UMBRELLA

    internal object Properties {
        val reference = ContextCaptureDefinition(
                ContextualPropertyReferenceDefinition<DataModelPropertyContext>(
                        name = "reference",
                        index = 0,
                        required = false,
                        contextualResolver = { it!!.dataModel!! }
                )
        ) { context, value ->
            @Suppress("UNCHECKED_CAST")
            context!!.reference = value as IsPropertyReference<Any, AbstractValueDefinition<Any, IsPropertyContext>>
        }

        val exceptions = ListDefinition(
                name = "exceptions",
                index = 1,
                required = true,
                valueDefinition = MultiTypeDefinition(
                        required = true,
                        getDefinition = { mapOfValidationExceptionDefinitions.get(it) }
                )
        )
    }

    companion object: QueryDataModel<ValidationUmbrellaException>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                ValidationUmbrellaException(
                        reference = it[0] as IsPropertyReference<*, *>?,
                        exceptions = (it[1] as List<TypedValue<ValidationException>>?)?.map { it.value } ?: emptyList()
                )
            },
            definitions = listOf(
                    Def(Properties.reference, ValidationUmbrellaException::reference),
                    Def(Properties.exceptions, { it.exceptions.map { TypedValue(it.validationExceptionType.index, it) } })
            )
    )
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
fun createValidationUmbrellaException(parentRefFactory: () -> IsPropertyReference<*, *>?, exceptionCollector: (exceptionAdder: (e: ValidationException) -> Unit) -> Unit){
    var hasExceptions = false
    val exceptions by lazy {
        hasExceptions = true
        mutableListOf<ValidationException>()
    }

    exceptionCollector {
        exceptions.add(it)
    }

    if (hasExceptions) {
        throw ValidationUmbrellaException(parentRefFactory(), exceptions)
    }
}
