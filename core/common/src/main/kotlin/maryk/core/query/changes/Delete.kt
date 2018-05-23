package maryk.core.query.changes

import maryk.core.objects.ReferencesDataModel
import maryk.core.objects.ReferencesPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.json.IsJsonLikeWriter

/** Delete of a property of type [T] referred by [reference] */
data class Delete internal constructor(
    val references: List<IsPropertyReference<*, IsValuePropertyDefinitionWrapper<*, *, IsPropertyContext, *>>>
) : IsChange {
    override val changeType = ChangeType.Delete

    @Suppress("UNCHECKED_CAST")
    constructor(vararg reference: IsPropertyReference<*, IsValuePropertyDefinitionWrapper<*, *, IsPropertyContext, *>>): this(reference.toList())

    internal object Properties : ReferencesPropertyDefinitions<Delete>() {
        override val references = addReferenceListPropertyDefinition(Delete::references)
    }

    internal companion object: ReferencesDataModel<Delete>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = Delete(
            references = map(0)
        )

        override fun writeJson(obj: Delete, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonReferences(obj.references, context)
        }
    }
}
