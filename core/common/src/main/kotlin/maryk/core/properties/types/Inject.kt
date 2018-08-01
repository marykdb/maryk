package maryk.core.properties.types

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelContext

/**
 * To inject a variable into a request
 */
class Inject<T: Any, D: IsPropertyDefinition<T>>(
    val collectionName: String,
    val propertyReference: IsPropertyReference<T, D, Any>
) {
    fun resolve(context: DataModelContext): T? {
        val result = context.retrieveResult(collectionName)

        return result?.get(propertyReference)
    }
}
