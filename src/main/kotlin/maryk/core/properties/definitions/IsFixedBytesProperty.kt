package maryk.core.properties.definitions

import maryk.core.properties.definitions.key.KeyPartType

interface IsFixedBytesProperty<T: Any>: IsFixedBytesValueGetter<T>, IsFixedBytesEncodable<T> {
    val keyPartType: KeyPartType
}