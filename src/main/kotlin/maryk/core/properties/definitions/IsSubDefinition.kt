package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext

/**
 * Abstract Property Definition to define properties.
 * This is used for simple single value properties and not for lists and maps.
 * @param <T> Type of objects contained
 */
interface IsSubDefinition<T: Any, in CX: IsPropertyContext>
    : IsSerializablePropertyDefinition<T, CX>, IsByteTransportableValue<T, CX>