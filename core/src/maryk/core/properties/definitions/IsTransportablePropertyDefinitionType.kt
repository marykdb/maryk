package maryk.core.properties.definitions

/** Interface for which the property definition itself is serializable and can thus be transported */
interface IsTransportablePropertyDefinitionType<T : Any> : IsPropertyDefinition<T> {
    val propertyDefinitionType: PropertyDefinitionType
}
