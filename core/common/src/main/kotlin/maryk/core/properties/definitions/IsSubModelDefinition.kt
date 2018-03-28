package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext

/** Interface for property definitions containing embedded DataObjects of [DO] and context [CX]. */
interface IsSubModelDefinition<DO : Any, in CX: IsPropertyContext>
    : IsSerializablePropertyDefinition<DO, CX>