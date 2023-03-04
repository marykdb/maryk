package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.IsRootModel
import maryk.core.properties.ReferencesModel
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForValues
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues

/** Delete of a property referred by [references] */
data class Delete internal constructor(
    val references: List<AnyPropertyReference>
) : IsChange {
    override val changeType = ChangeType.Delete

    constructor(vararg reference: AnyPropertyReference) : this(reference.toList())

    override fun filterWithSelect(select: RootPropRefGraph<out IsRootModel>): Delete? {
        val filtered = references.filter {
            select.contains(it)
        }
        return if (filtered.isEmpty()) null else Delete(filtered)
    }

    override fun changeValues(objectChanger: (IsPropertyReferenceForValues<*, *, *, *>, (Any?, Any?) -> Any?) -> Unit) {
        val mutableReferenceList = mutableListOf<AnyPropertyReference>()

        for (reference in references) {
            reference.unwrap(mutableReferenceList)
            var referenceIndex = 0

            fun valueDeleter(originalValue: Any?, newValue: Any?): Any? {
                val currentRef = mutableReferenceList.getOrNull(referenceIndex++)

                return if (currentRef != null) {
                    try {
                        deepValueChanger(
                            originalValue,
                            newValue,
                            currentRef,
                            ::valueDeleter
                        )
                        null // Deeper change so no overwrite
                    } catch (_: SubObjectChangeException) {} // Ignore since there is nothing to delete
                } else Unit // Set the deletion as Unit
            }

            when (val ref = mutableReferenceList[referenceIndex++]) {
                is IsPropertyReferenceForValues<*, *, *, *> -> objectChanger(ref, ::valueDeleter)
                else -> throw RequestException("Unsupported reference type: $ref")
            }
        }
    }

    override fun toString() = "Delete[${references.joinToString()}]"

    companion object : ReferencesModel<Delete, Companion>(
        Delete::references
    ) {
        override val references by list(
            index = 1u,
            getter = Delete::references,
            valueDefinition = ContextualPropertyReferenceDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel?.properties as? AbstractPropertyDefinitions<*>?
                        ?: throw ContextNotFoundException()
                }
            )
        )

        override fun invoke(values: ObjectValues<Delete, Companion>) = Delete(
            references = values(1u)
        )
    }
}
