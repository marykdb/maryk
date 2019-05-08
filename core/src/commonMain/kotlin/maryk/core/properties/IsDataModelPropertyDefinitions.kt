package maryk.core.properties

import maryk.core.models.IsDataModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.wrapper.AnyPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.FlexBytesDefinitionWrapper

@PropertyReferenceMarker
internal interface IsDataModelPropertyDefinitions<
    DM : IsDataModel<*>,
    C : IsCollectionDefinition<AnyPropertyDefinitionWrapper, *, *, EmbeddedObjectDefinition<AnyPropertyDefinitionWrapper, ObjectPropertyDefinitions<AnyPropertyDefinitionWrapper>, *, IsPropertyContext, IsPropertyContext>>
> : IsPropertyDefinitions {
    val name: FlexBytesDefinitionWrapper<String, String, IsPropertyContext, StringDefinition, DM>
    val properties: C
}
