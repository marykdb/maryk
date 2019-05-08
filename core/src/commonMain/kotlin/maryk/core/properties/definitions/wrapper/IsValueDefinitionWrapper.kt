package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition

/**
 * Describes a Property Definition of [T] which contains an encodable value and not
 * a complex type like list/set/map/subModel to be used in DataObject [DO] in context [CX]
 */
interface IsValueDefinitionWrapper<T : Any, TO : Any, in CX : IsPropertyContext, in DO> :
    IsDefinitionWrapper<T, TO, CX, DO>, IsChangeableValueDefinition<T, CX>
