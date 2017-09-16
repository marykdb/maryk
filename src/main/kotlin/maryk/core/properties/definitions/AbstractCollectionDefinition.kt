package maryk.core.properties.definitions

import maryk.core.properties.exceptions.PropertyTooLittleItemsException
import maryk.core.properties.exceptions.PropertyTooMuchItemsException
import maryk.core.properties.exceptions.PropertyValidationException
import maryk.core.properties.exceptions.createPropertyValidationUmbrellaException
import maryk.core.properties.references.PropertyReference

abstract class AbstractCollectionDefinition<T: Any, C: Collection<T>>(
        name: String? = null,
        index: Short = -1,
        indexed: Boolean = true,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        override val minSize: Int? = null,
        override val maxSize: Int? = null,
        val valueDefinition: AbstractValueDefinition<T>
) : AbstractPropertyDefinition<C>(
        name, index, indexed, searchable, required, final
), HasSizeDefinition {
    init {
        assert(valueDefinition.required, { "Definition should have required=true on collection «$name»" })
    }

    override fun validate(previousValue: C?, newValue: C?, parentRefFactory: () -> PropertyReference<*, *>?) {
        super.validate(previousValue, newValue, parentRefFactory)

        if (newValue != null) {
            val size = getSize(newValue)
            if (isSizeToSmall(size)) {
                throw PropertyTooLittleItemsException(this.getRef(parentRefFactory), size, this.minSize!!)
            }
            if (isSizeToBig(size)) {
                throw PropertyTooMuchItemsException(this.getRef(parentRefFactory), size, this.maxSize!!)
            }

            createPropertyValidationUmbrellaException(parentRefFactory) { addException ->
                validateCollectionForExceptions(parentRefFactory, newValue) { item, refFactory ->
                    try {
                        this.valueDefinition.validate(null, item, refFactory)
                    } catch (e: PropertyValidationException) {
                        addException(e)
                    }
                }
            }
        }
    }

    /** Get the size of the collection object */
    abstract fun getSize(newValue: C): Int

    /** Validates the collection content */
    abstract internal fun validateCollectionForExceptions(parentRefFactory: () -> PropertyReference<*, *>?, newValue: C, validator: (item: T, parentRefFactory: () -> PropertyReference<*, *>?) -> Any)
}