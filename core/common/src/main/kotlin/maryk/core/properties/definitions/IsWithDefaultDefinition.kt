package maryk.core.properties.definitions

/**
 * A definition which contains a default value
 */
interface IsWithDefaultDefinition<T: Any> {
    val default: T?
}
