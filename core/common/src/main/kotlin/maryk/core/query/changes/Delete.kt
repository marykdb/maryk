package maryk.core.query.changes

import maryk.core.models.ReferencesDataModel
import maryk.core.models.ReferencesObjectPropertyDefinitions
import maryk.core.objects.ObjectValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.RequestContext
import maryk.json.IsJsonLikeWriter

/** Delete of a property referred by [references] */
data class Delete internal constructor(
    val references: List<IsPropertyReference<*, IsValuePropertyDefinitionWrapper<*, *, IsPropertyContext, *>, *>>
) : IsChange {
    override val changeType = ChangeType.Delete

    @Suppress("UNCHECKED_CAST")
    constructor(vararg reference: IsPropertyReference<*, IsValuePropertyDefinitionWrapper<*, *, IsPropertyContext, *>, *>): this(reference.toList())

    object Properties : ReferencesObjectPropertyDefinitions<Delete>() {
        override val references = addReferenceListPropertyDefinition(Delete::references)
    }

    companion object: ReferencesDataModel<Delete, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<Delete, Properties>) = Delete(
            references = map(1)
        )

        override fun writeJson(obj: Delete, writer: IsJsonLikeWriter, context: RequestContext?) {
            writer.writeJsonReferences(obj.references, context)
        }
    }
}
