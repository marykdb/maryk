package maryk.core.properties.types

import maryk.core.models.IsDataModel
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.exceptions.InjectException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelContext

typealias AnyInject = Inject<*, *, *, *>

/**
 * To inject a variable into a request
 */
class Inject<T: Any, D: IsPropertyDefinition<T>, DM: IsDataModel<P>, P: AbstractPropertyDefinitions<Any>>(
    val collectionName: String,
    val dataModel: DM,
    val propertyReference: IsPropertyReference<T, D, *>? = null
) {
    fun resolve(context: DataModelContext): T? {
        val result = context.retrieveResult(collectionName)
            ?: throw InjectException(this.collectionName)

        @Suppress("UNCHECKED_CAST")
        return propertyReference?.let {
            result[propertyReference]
        } ?: result as T?
    }
}
