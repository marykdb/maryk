package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext

/** Combination of serializable property and fixed bytes property of type [T] and context [CX] */
interface IsSerializableFixedBytesEncodable<T : Any, in CX : IsPropertyContext>
    : IsFixedStorageBytesEncodable<T>, IsChangeableValueDefinition<T, CX>
