package maryk.core.properties.definitions

import maryk.core.extensions.bytes.computeVarByteSize
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.objects.DataModel
import maryk.core.properties.exceptions.PropertyAlreadySetException
import maryk.core.properties.exceptions.PropertyRequiredException
import maryk.core.properties.exceptions.PropertyValidationException
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
    override fun getRef(parentRefFactory: () -> PropertyReference<*, *>?) =
            PropertyReference(this, parentRefFactory())

    @Throws(PropertyValidationException::class)
    override fun validate(previousValue: T?, newValue: T?, parentRefFactory: () -> PropertyReference<*, *>?) = when {
        this.final && previousValue != null -> throw PropertyAlreadySetException(this.getRef(parentRefFactory))
        this.required && newValue == null -> throw PropertyRequiredException(this.getRef(parentRefFactory))
        else -> {}
    }

    fun <DM : Any> getValue(dataModel: DataModel<DM>, dataObject: DM): T {
        @Suppress("UNCHECKED_CAST")
        return dataModel.getPropertyGetter(
                this.index
        )?.invoke(dataObject) as T
    }

    override fun readTransportBytes(length: Int, reader: () -> Byte): T {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    /** Adds length to written bytes
     * @param value to convert
     * @param reserver to reserve amount of bytes to write on
     * @param writer to write bytes to
     */
    protected fun writeTransportBytesWithLength(value: T, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
        this.writeTransportBytes(value, {
            reserver(it + it.computeVarByteSize())
            it.writeVarBytes(writer)
        }, writer)
    }

    override fun writeTransportBytes(value: T, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun writeTransportBytesWithKey(value: T, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}