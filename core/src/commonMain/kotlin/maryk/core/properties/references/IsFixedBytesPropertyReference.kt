package maryk.core.properties.references

import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.IsFixedBytesValueGetter
import maryk.core.properties.definitions.key.KeyPartType

interface IsFixedBytesPropertyReference<T: Any>: IsFixedBytesValueGetter<T> {
    val keyPartType: KeyPartType
    val propertyDefinition: IsFixedBytesEncodable<T>
}
