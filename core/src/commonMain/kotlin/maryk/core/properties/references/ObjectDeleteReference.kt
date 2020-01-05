package maryk.core.properties.references

import maryk.core.properties.definitions.BooleanDefinition

/** Reference to use for references to a deletion of an object */
object ObjectDeleteReference : IsPropertyReferenceForCache<Boolean, BooleanDefinition> {
    override val propertyDefinition = BooleanDefinition()
}
