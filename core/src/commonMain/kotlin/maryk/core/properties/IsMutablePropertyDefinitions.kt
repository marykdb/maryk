package maryk.core.properties

import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper

@PropertyReferenceMarker
interface IsMutablePropertyDefinitions<W: IsPropertyDefinitionWrapper<*, *, *, *>>: IsPropertyDefinitions, MutableCollection<W>
