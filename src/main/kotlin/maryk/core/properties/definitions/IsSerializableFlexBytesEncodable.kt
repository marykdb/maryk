package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext

/** Combination of Is serializable property and fixed bytes property */
interface IsSerializableFlexBytesEncodable<T: Any, in CX: IsPropertyContext>
    : IsSerializablePropertyDefinition<T, CX>
