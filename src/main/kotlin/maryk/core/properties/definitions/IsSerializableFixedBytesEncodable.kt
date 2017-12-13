package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext

/** Combination of Is serializable property and fixed bytes property */
interface IsSerializableFixedBytesEncodable<T: Any, in CX: IsPropertyContext>
    : IsFixedBytesEncodable<T>, IsSerializablePropertyDefinition<T, CX>
