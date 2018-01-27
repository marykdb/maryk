package maryk.core.properties.definitions

import maryk.core.properties.types.numeric.UInt32

/**
 * Interface which defines definition has min and max size definitions
 */
interface HasSizeDefinition {
    /** The min allowed size for defined property */
    val minSize: Int?

    /** The max allowed size for defined property */
    val maxSize: Int?

    /** Checks if given [newSize] is too small compared to defined minSize */
    fun isSizeToSmall(newSize: Int): Boolean = this.minSize?.let {
        newSize < it
    } ?: false

    /** Checks if given [newSize] is too big compared to defined maxSize */
    fun isSizeToBig(newSize: Int): Boolean = this.maxSize?.let {
        newSize > it
    } ?: false

    companion object {
        internal fun <DO: Any> addMinSize(index: Int, definitions: PropertyDefinitions<DO>, getter: (DO) -> UInt32?) {
            definitions.add(index, "minSize", NumberDefinition(type = UInt32), getter)
        }

        internal fun <DO: Any> addMaxSize(index: Int, definitions: PropertyDefinitions<DO>, getter: (DO) -> UInt32?) {
            definitions.add(index, "maxSize", NumberDefinition(type = UInt32), getter)
        }
    }
}
