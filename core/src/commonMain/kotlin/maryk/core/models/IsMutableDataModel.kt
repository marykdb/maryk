package maryk.core.models

import maryk.core.properties.PropertyReferenceMarker
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper

@PropertyReferenceMarker
interface IsMutableDataModel<W : IsDefinitionWrapper<*, *, *, *>> :
    IsDataModel,
    MutableCollection<W>
