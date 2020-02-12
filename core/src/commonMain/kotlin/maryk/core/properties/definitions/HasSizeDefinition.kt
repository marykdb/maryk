package maryk.core.properties.definitions

/**
 * Interface which defines definition has min and max size definitions
 */
interface HasSizeDefinition {
    /** The min allowed size for defined property */
    val minSize: UInt?

    /** The max allowed size for defined property */
    val maxSize: UInt?

    /** Checks if given [newSize] is too small compared to defined minSize */
    fun isSizeToSmall(newSize: UInt): Boolean = this.minSize?.let {
        newSize < it
    } ?: false

    /** Checks if given [newSize] is too big compared to defined maxSize */
    fun isSizeToBig(newSize: UInt): Boolean = this.maxSize?.let {
        newSize > it
    } ?: false
}
