package maryk.core.query.changes

import maryk.core.models.ReferencesDataModel
import maryk.core.models.ReferencesObjectPropertyDefinitions
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeWriter

/** Delete of a property referred by [references] */
data class Delete internal constructor(
    val references: List<AnyPropertyReference>
) : IsChange {
    override val changeType = ChangeType.Delete

    constructor(vararg reference: AnyPropertyReference) : this(reference.toList())

    override fun toString() = "Delete[${references.joinToString()}]"

    object Properties : ReferencesObjectPropertyDefinitions<Delete>() {
        override val references = addReferenceListPropertyDefinition(Delete::references)
    }

    companion object : ReferencesDataModel<Delete, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<Delete, Properties>) = Delete(
            references = values(1u)
        )

        override fun writeJson(obj: Delete, writer: IsJsonLikeWriter, context: RequestContext?) {
            writer.writeJsonReferences(obj.references, context)
        }
    }
}
