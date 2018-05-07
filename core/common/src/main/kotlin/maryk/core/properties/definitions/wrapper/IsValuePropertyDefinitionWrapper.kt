package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext

/**
 * Describes a Property Definition of [T] which contains an encodable value and not
 * a complex type like list/set/map/subModel to be used in DataObject [DO] in context [CX]
 */
interface IsValuePropertyDefinitionWrapper<T: Any, TO: Any, in CX:IsPropertyContext, in DO> : IsPropertyDefinitionWrapper<T, TO, CX, DO>
