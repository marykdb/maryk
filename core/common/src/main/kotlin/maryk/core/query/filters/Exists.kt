package maryk.core.query.filters

import maryk.core.models.ReferencesDataModel
import maryk.core.models.ReferencesPropertyDefinitions
import maryk.core.objects.ValueMap
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.json.IsJsonLikeWriter

/** Checks if [references] exist on DataModel */
data class Exists internal constructor(
    val references: List<IsPropertyReference<*, IsValuePropertyDefinitionWrapper<*, *, IsPropertyContext, *>>>
) : IsFilter {
    override val filterType = FilterType.Exists

    @Suppress("UNCHECKED_CAST")
    constructor(vararg reference: IsPropertyReference<*, IsValuePropertyDefinitionWrapper<*, *, IsPropertyContext, *>>): this(reference.toList())

    internal object Properties : ReferencesPropertyDefinitions<Exists>() {
        override val references = addReferenceListPropertyDefinition(Exists::references)
    }

    internal companion object: ReferencesDataModel<Exists, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ValueMap<Exists, Properties>) = Exists(
            references = map(0)
        )

        override fun writeJson(obj: Exists, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonReferences(obj.references, context)
        }
    }
}
