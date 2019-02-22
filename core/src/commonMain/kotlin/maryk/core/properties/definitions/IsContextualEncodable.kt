package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext

/** Combination of serializable property and flex bytes property of type [T] and context [CX] */
interface IsContextualEncodable<T: Any, in CX: IsPropertyContext>
    : IsSerializablePropertyDefinition<T, CX>, IsChangeableValueDefinition<T, CX>
