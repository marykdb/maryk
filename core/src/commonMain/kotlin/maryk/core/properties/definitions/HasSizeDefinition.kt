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

/**
 * Checks if HasSizeDefinition is compatible. It is not if this definition
 * has more limiting sizes than the passed [definition].
 */
internal fun HasSizeDefinition.isCompatible(definition: HasSizeDefinition, addIncompatibilityReason: ((String) -> Unit)? = null): Boolean {
    var compatible = true
    if (minSize != null && (definition.minSize == null || minSize!! > definition.minSize!!)) {
        addIncompatibilityReason?.invoke("Minimum size is bigger than or newly set: ${definition.minSize} < $minSize")
        compatible = false
    }

    if (maxSize != null && (definition.maxSize == null || maxSize!! < definition.maxSize!!)) {
        addIncompatibilityReason?.invoke("Maximum size is smaller than or newly set: ${definition.maxSize} > $maxSize")
        compatible = false
    }
    return compatible
}
