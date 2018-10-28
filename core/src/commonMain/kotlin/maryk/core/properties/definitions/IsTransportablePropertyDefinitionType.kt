package maryk.core.properties.definitions

/** Interface for transportable property definitions */
interface IsTransportablePropertyDefinitionType<T: Any>: IsPropertyDefinition<T> {
    val propertyDefinitionType: PropertyDefinitionType
}
