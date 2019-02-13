package maryk.core.properties.references

import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.IsFixedBytesValueGetter
import maryk.core.properties.definitions.key.IsIndexable

interface IsFixedBytesPropertyReference<T: Any>: IsFixedBytesValueGetter<T>, IsIndexable, IsFixedBytesEncodable<T> {
    val propertyDefinition: IsFixedBytesEncodable<T>
}
