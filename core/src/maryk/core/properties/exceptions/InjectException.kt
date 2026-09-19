package maryk.core.properties.exceptions

/**
 * Exception to be thrown if value cannot yet be resolved
 */
data class InjectException(
    val collectionName: String
) : Exception("Collection name $collectionName is not yet resolvable")
