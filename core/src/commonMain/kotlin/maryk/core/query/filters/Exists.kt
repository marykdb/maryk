package maryk.core.query.filters

import maryk.core.models.ReferencesDataModel
import maryk.core.models.ReferencesObjectPropertyDefinitions
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeWriter

/** Checks if [references] exist on DataModel */
data class Exists internal constructor(
    val references: List<IsPropertyReference<*, IsChangeableValueDefinition<*, *>, *>>
) : IsFilter {
    override val filterType = FilterType.Exists

    constructor(vararg reference: IsPropertyReference<*, IsChangeableValueDefinition<*, *>, *>) : this(
        reference.toList()
    )

    object Properties : ReferencesObjectPropertyDefinitions<Exists>() {
        override val references = addReferenceListPropertyDefinition(Exists::references)
    }

    companion object : ReferencesDataModel<Exists, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<Exists, Properties>) = Exists(
            references = values(1)
        )

        override fun writeJson(obj: Exists, writer: IsJsonLikeWriter, context: RequestContext?) {
            writer.writeJsonReferences(obj.references, context)
        }
    }
}
