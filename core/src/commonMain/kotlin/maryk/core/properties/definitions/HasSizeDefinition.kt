package maryk.core.properties.definitions

import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.types.numeric.UInt32

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

    companion object {
        internal fun <DO : Any> addMinSize(
            index: Int,
            definitions: ObjectPropertyDefinitions<DO>,
            getter: (DO) -> UInt?
        ) {
            definitions.add(
                index = index,
                name = "minSize",
                definition = NumberDefinition(type = UInt32),
                getter = getter
            )
        }

        internal fun <DO : Any> addMaxSize(
            index: Int,
            definitions: ObjectPropertyDefinitions<DO>,
            getter: (DO) -> UInt?
        ) {
            definitions.add(
                index = index,
                name = "maxSize",
                definition = NumberDefinition(type = UInt32),
                getter = getter
            )
        }
    }
}
