package maryk.core.properties.definitions

import maryk.core.objects.IsDataModel
import maryk.core.properties.exceptions.PropertyAlreadySetException
import maryk.core.properties.exceptions.PropertyRequiredException
import maryk.core.properties.exceptions.PropertyValidationException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.PropertyReference

/**
 * Abstract Property Definition to define properties
 * @param <T> Type defined by definition
 */
abstract class AbstractPropertyDefinition<T: Any>  (
        override final val name: String?,
        override final val index: Int,
        val indexed: Boolean,
        val searchable: Boolean,
        val required: Boolean,
        val final: Boolean
) : IsPropertyDefinition<T> {
    override fun getRef(parentRefFactory: () -> IsPropertyReference<*, *>?) =
            PropertyReference(this, parentRefFactory())

    @Throws(PropertyValidationException::class)
    override fun validate(previousValue: T?, newValue: T?, parentRefFactory: () -> IsPropertyReference<*, *>?) = when {
        this.final && previousValue != null -> throw PropertyAlreadySetException(this.getRef(parentRefFactory))
        this.required && newValue == null -> throw PropertyRequiredException(this.getRef(parentRefFactory))
        else -> {}
    }

    fun <DM : Any> getValue(dataModel: IsDataModel<DM>, dataObject: DM): T {
        @Suppress("UNCHECKED_CAST")
        return dataModel.getPropertyGetter(
                this.index
        )?.invoke(dataObject) as T
    }
}