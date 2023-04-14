package maryk.core.models

import maryk.core.properties.PropertyReferenceMarker
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper

/**
 * Defines a mutable datamodel which can be filled after reading a serialized DataModel
 */
@PropertyReferenceMarker
interface IsMutableDataModel<W : IsDefinitionWrapper<*, *, *, *>> :
    IsDataModel,
    MutableCollection<W>
