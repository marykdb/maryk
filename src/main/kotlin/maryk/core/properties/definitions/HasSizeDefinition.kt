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

    fun isSizeToSmall(newSize: Int): Boolean =
            this.minSize != null && newSize < this.minSize!!

    fun isSizeToBig(newSize: Int): Boolean =
            this.maxSize != null && newSize > this.maxSize!!

    companion object {
        fun <DO: Any> addMinSize(index: Int, definitions: PropertyDefinitions<DO>, getter: (DO) -> UInt32?) {
            definitions.add(index, "minSize", NumberDefinition(type = UInt32), getter)
        }

        fun <DO: Any> addMaxSize(index: Int, definitions: PropertyDefinitions<DO>, getter: (DO) -> UInt32?) {
            definitions.add(index, "maxSize", NumberDefinition(type = UInt32), getter)
        }
    }
}
