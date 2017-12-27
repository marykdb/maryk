package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext

/** Describes a Property Definition which contains an encodable value and not a complex type like list/set/map/subModel.
 * @param T: value type of property
 * @param CX: Context type for property
 * @param DO: Type of DataObject which contains this property
 */
interface IsValuePropertyDefinitionWrapper<T: Any, in CX:IsPropertyContext, in DO> : IsPropertyDefinitionWrapper<T, CX, DO>