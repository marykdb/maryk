package maryk.core.properties

import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper

@PropertyReferenceMarker
interface IsMutablePropertyDefinitions<W : IsDefinitionWrapper<*, *, *, *>> :
    IsPropertyDefinitions,
    MutableCollection<W>
