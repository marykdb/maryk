package maryk.core.properties.definitions

/**
 * Defines a definition which contains a default value
 */
interface HasDefaultValueDefinition<T : Any> {
    val default: T?
}
