package maryk.core.query.filters

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.ReferencesDataModel
import maryk.core.models.ReferencesObjectPropertyDefinitions
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceWithParent
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues
import maryk.json.IsJsonLikeWriter

/** Checks if [references] exist on DataModel */
data class Exists internal constructor(
    val references: List<AnyPropertyReference>
) : IsFilter {
    override val filterType = FilterType.Exists

    constructor(vararg reference: IsPropertyReference<*, IsSerializablePropertyDefinition<*, *>, *>) : this(
        reference.toList()
    )

    override fun singleReference(predicate: (IsPropertyReference<*, *, *>) -> Boolean): IsPropertyReference<*, *, *>? {
        var parentReference: AnyPropertyReference?
        for (reference in this.references) {
            parentReference = reference
            do {
                if (predicate(parentReference!!)) {
                    return reference
                }
                parentReference = (parentReference as? IsPropertyReferenceWithParent<*, *, *, *>)?.parentReference
            } while (parentReference != null)
        }
        return null
    }

    object Properties : ReferencesObjectPropertyDefinitions<Exists>() {
        override val references by list(
            index = 1u,
            getter = Exists::references,
            valueDefinition = ContextualPropertyReferenceDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel?.properties as? AbstractPropertyDefinitions<*>?
                        ?: throw ContextNotFoundException()
                }
            )
        )
    }

    companion object : ReferencesDataModel<Exists, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<Exists, Properties>) = Exists(
            references = values(1u)
        )

        override fun writeJson(obj: Exists, writer: IsJsonLikeWriter, context: RequestContext?) {
            writer.writeJsonReferences(obj.references, context)
        }
    }
}
