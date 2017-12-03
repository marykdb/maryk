package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext

interface IsSubModelDefinition<DO : Any, in CX: IsPropertyContext>
    : IsSerializablePropertyDefinition<DO, CX>{
}