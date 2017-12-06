package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext

interface IsCollectionDefinition<T: Any, C: Collection<T>, in CX: IsPropertyContext>
    : IsSerializablePropertyDefinition<C, CX>, IsByteTransportableCollection<T, C, CX>