package maryk.core.models.serializers

import maryk.core.models.definitions.IsNamedDataModelDefinition
import maryk.core.models.IsDataModelWithPropertyDefinitions
import maryk.core.models.IsObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.AnyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.query.ContainsDefinitionsContext
import maryk.json.IsJsonLikeWriter
import maryk.yaml.YamlWriter

internal fun <
    DM : IsNamedDataModelDefinition<*>,
    P : IsDataModelWithPropertyDefinitions<DM, *>,
    P2 : IsObjectDataModel<DM>
> ObjectDataModelSerializer<DM, P2, ContainsDefinitionsContext, ContainsDefinitionsContext>.writeDataModelJson(
    writer: IsJsonLikeWriter,
    context: ContainsDefinitionsContext?,
    obj: DM,
    properties: P
) {
    writer.writeStartObject()
    for (def in this.model) {
        if (def == properties.properties) continue // skip properties to write last
        // Skip name if defined higher
        if (def == properties.name && context != null && context.currentDefinitionName == obj.name) {
            context.currentDefinitionName = "" // Reset after use
            continue
        }

        val value = def.getPropertyAndSerialize(obj, context) ?: continue
        this.writeJsonValue(def, writer, value, context)
    }
    if (writer is YamlWriter) {
        // Write optimized format when writing yaml
        @Suppress("UNCHECKED_CAST")
        for (property in obj.properties as Iterable<AnyDefinitionWrapper>) {
            properties.properties.valueDefinition.writeJsonValue(property, writer, context)
        }
    } else {
        @Suppress("UNCHECKED_CAST")
        this.writeJsonValue(
            properties.properties as IsDefinitionWrapper<in Any, in Any, IsPropertyContext, DM>,
            writer,
            obj.properties,
            context
        )
    }
    writer.writeEndObject()
}
