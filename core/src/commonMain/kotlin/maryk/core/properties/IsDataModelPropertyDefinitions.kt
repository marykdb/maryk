package maryk.core.properties

import maryk.core.models.definitions.IsDataModelDefinition
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.wrapper.AnyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.FlexBytesDefinitionWrapper

@PropertyReferenceMarker
internal interface IsDataModelPropertyDefinitions<
    DM : IsDataModelDefinition<*>,
    C : IsCollectionDefinition<AnyDefinitionWrapper, *, *, EmbeddedObjectDefinition<AnyDefinitionWrapper, IsSimpleBaseModel<AnyDefinitionWrapper, IsPropertyContext, IsPropertyContext>, IsPropertyContext, IsPropertyContext>>
> : IsPropertyDefinitions {
    val name: FlexBytesDefinitionWrapper<String, String, IsPropertyContext, StringDefinition, DM>
    val properties: C
}
