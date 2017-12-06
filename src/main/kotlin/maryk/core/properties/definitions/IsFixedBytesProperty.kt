package maryk.core.properties.definitions

interface IsFixedBytesProperty<T: Any>: IsFixedBytesValueGetter<T>, IsFixedBytesEncodable<T>