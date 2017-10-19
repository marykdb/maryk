package maryk.core.properties.definitions

/**
 * Abstract Property Definition to define properties.
 * This is used for simple single value properties and not for lists and maps.
 * @param <T> Type of objects contained
 */
abstract class AbstractSubDefinition<T: Any>(
        name: String?,
        index: Int,
        indexed: Boolean,
        searchable: Boolean,
        required: Boolean,
        final: Boolean
) : AbstractPropertyDefinition<T>(
        name, index, indexed, searchable, required, final) {
    override fun writeTransportBytesWithKey(value: T, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit)
            = this.writeTransportBytesWithKey(this.index, value, reserver, writer)

    abstract fun writeTransportBytesWithKey(index: Int, value: T, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit)
}