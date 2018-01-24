package maryk.core.properties.definitions

import maryk.core.properties.definitions.key.KeyPartType

/** Interface for properties of type [T] which can be encoded to a fixed byte length */
interface IsFixedBytesProperty<T: Any>: IsFixedBytesValueGetter<T>, IsFixedBytesEncodable<T> {
    val keyPartType: KeyPartType
}