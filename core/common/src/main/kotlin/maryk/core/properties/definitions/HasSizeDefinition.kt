package maryk.core.properties.definitions

import maryk.core.properties.ObjectPropertyDefinitions
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
        @Suppress("EXPERIMENTAL_API_USAGE")
        internal fun <DO: Any> addMinSize(
            index: Int,
            definitions: ObjectPropertyDefinitions<DO>,
            getter: (DO) -> Int?
        ) {
            definitions.add(
                index = index,
                name = "minSize",
                definition = NumberDefinition(type = UInt32),
                getter = getter,
                toSerializable = { value, _ -> value?.toUInt() },
                fromSerializable = { it?.toInt() }
            )
        }

        @Suppress("EXPERIMENTAL_API_USAGE")
        internal fun <DO: Any> addMaxSize(
            index: Int,
            definitions: ObjectPropertyDefinitions<DO>,
            getter: (DO) -> Int?
        ) {
            definitions.add(
                index = index,
                name = "maxSize",
                definition = NumberDefinition(type = UInt32),
                getter = getter,
                toSerializable = { value, _ -> value?.toUInt() },
                fromSerializable = { it?.toInt() }
            )
        }
    }
}
