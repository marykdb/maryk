package maryk.core.properties.definitions

/**
 * Interface which defines definition has min and max size definitions
 */
interface HasSizeDefinition {
    /** The min allowed size for defined property */
    val minSize: Int?

    /** The max allowed size for defined property */
    val maxSize: Int?
}

fun HasSizeDefinition.isSizeToSmall(newSize: Int): Boolean =
        this.minSize != null && newSize < this.minSize!!

fun HasSizeDefinition.isSizeToBig(newSize: Int): Boolean =
        this.maxSize != null && newSize > this.maxSize!!
