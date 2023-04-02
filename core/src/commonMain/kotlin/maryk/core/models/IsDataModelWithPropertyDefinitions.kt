package maryk.core.models

import maryk.core.models.definitions.IsDataModelDefinition
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyReferenceMarker
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.wrapper.AnyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.FlexBytesDefinitionWrapper

@PropertyReferenceMarker
internal interface IsDataModelWithPropertyDefinitions<
    DM : IsDataModelDefinition<*>,
    C : IsCollectionDefinition<AnyDefinitionWrapper, *, *, EmbeddedObjectDefinition<AnyDefinitionWrapper, IsSimpleBaseObjectDataModel<AnyDefinitionWrapper, IsPropertyContext, IsPropertyContext>, IsPropertyContext, IsPropertyContext>>
> : IsDataModel {
    val name: FlexBytesDefinitionWrapper<String, String, IsPropertyContext, StringDefinition, DM>
    val properties: C
}
