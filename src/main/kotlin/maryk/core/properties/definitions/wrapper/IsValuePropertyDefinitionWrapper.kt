package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext

/** Describes a Property Definition which contains an encodable value and not a complex type like list/set/map/submodel.
 * @param T: value type of property
 * @param CX: Context type for property
 * @param DM: Type of DataModel which contains this property
 */
interface IsValuePropertyDefinitionWrapper<T: Any, in CX:IsPropertyContext, in DM> : IsPropertyDefinitionWrapper<T, CX, DM>