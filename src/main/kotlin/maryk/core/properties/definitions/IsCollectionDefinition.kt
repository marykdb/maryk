package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext

interface IsCollectionDefinition<T: Collection<Any>, in CX: IsPropertyContext>
    : IsSerializablePropertyDefinition<T, CX>