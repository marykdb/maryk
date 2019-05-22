package maryk.core.properties

import maryk.core.models.IsDataModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.wrapper.AnyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.FlexBytesDefinitionWrapper

@PropertyReferenceMarker
internal interface IsDataModelPropertyDefinitions<
    DM : IsDataModel<*>,
    C : IsCollectionDefinition<AnyDefinitionWrapper, *, *, EmbeddedObjectDefinition<AnyDefinitionWrapper, ObjectPropertyDefinitions<AnyDefinitionWrapper>, *, IsPropertyContext, IsPropertyContext>>
> : IsPropertyDefinitions {
    val name: FlexBytesDefinitionWrapper<String, String, IsPropertyContext, StringDefinition, DM>
    val properties: C
}
