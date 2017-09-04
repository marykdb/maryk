package maryk.core.properties.exceptions

import maryk.core.properties.references.PropertyReference

/** Umbrella for Validation Exception for properties
 * Contains a list of exceptions which where catched. */
class PropertyValidationUmbrellaException(
        reference: PropertyReference<*,*>?,
        internal val exceptions: List<PropertyValidationException>
) : PropertyValidationException(
        reference = reference,
        id = "UMBRELLA",
        newMessage = createReason(reference, exceptions)
)

private fun createReason(reference: PropertyReference<*,*>?, exceptions: List<PropertyValidationException>): String {
    val property = if (reference != null) " in property «${reference.name}»" else ""

    var messages = "Umbrella exception$property: [\n"
    exceptions.forEach {
        messages += "\t${it.message?.replace("\n", "\n\t")}\n"
    }
    return messages + "]"
}

/** Convenience method to create a new PropertyValidationUmbrellaException */
fun createPropertyValidationUmbrellaException(parentRefFactory: () -> PropertyReference<*, *>?, exceptionCollector: (exceptionAdder: (e: PropertyValidationException) -> Unit) -> Unit){
    var hasExceptions = false
    val exceptions by lazy {
        hasExceptions = true
        mutableListOf<PropertyValidationException>()
    }

    exceptionCollector {
        exceptions.add(it)
    }

    if (hasExceptions) {
        throw PropertyValidationUmbrellaException(parentRefFactory(), exceptions)
    }
}
