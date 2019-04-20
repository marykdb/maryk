package maryk.core.properties.definitions.wrapper

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListAnyItemReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.ListReference
import maryk.core.values.Values

/**
 * Contains a List property [definition] which contains items of type [T]
 * It contains an [index] and [name] to which it is referred inside DataModel and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
interface IsListPropertyDefinitionWrapper<T : Any, TO : Any, LD : ListDefinition<T, CX>, CX : IsPropertyContext, in DO : Any> :
    IsCollectionDefinition<T, List<T>, CX, IsValueDefinition<T, CX>>,
    IsPropertyDefinitionWrapper<List<T>, List<TO>, CX, DO> {
    override val definition: LD

    @Suppress("UNCHECKED_CAST")
    override fun ref(parentRef: AnyPropertyReference?) =
        ListReference(
            this as IsListPropertyDefinitionWrapper<T, Any, ListDefinition<T, CX>, CX, *>,
            parentRef as CanHaveComplexChildReference<*, *, *, *>?
        )

    /** Get a reference to a specific list item by [index] with optional [parentRef] */
    fun getItemRef(index: UInt, parentRef: AnyPropertyReference? = null) =
        this.definition.itemRef(index, this.ref(parentRef))

    /** Get a reference to a specific list item at any index with optional [parentRef] */
    fun getAnyItemRef(parentRef: AnyPropertyReference? = null) =
        this.definition.anyItemRef(this.ref(parentRef))

    /** For quick notation to get any list item reference */
    fun refToAny(): (AnyOutPropertyReference?) -> ListAnyItemReference<T, CX> {
        return { this.getAnyItemRef(it) }
    }

    /** For quick notation to get a list item reference by [index] */
    infix fun refAt(index: UInt): (AnyOutPropertyReference?) -> ListItemReference<T, CX> {
        return { this.getItemRef(index, it) }
    }
}

/** Specific extension to support fetching deeper references on List with values by [index] */
fun <V: Values<*, P>, DM : IsValuesDataModel<P>, P : PropertyDefinitions, T : Any, W : IsPropertyDefinitionWrapper<T, *, *, *>, R : IsPropertyReference<T, W, *>> IsListPropertyDefinitionWrapper<V, *, *, *, *>.at(
    index: UInt,
    referenceGetter: P.() -> (AnyOutPropertyReference?) -> R
): (AnyOutPropertyReference?) -> R =
    {
        @Suppress("UNCHECKED_CAST")
        val valueDefinition = (this.definition.valueDefinition as EmbeddedValuesDefinition<DM, P>)

        valueDefinition.dataModel(
            this.getItemRef(index, it),
            referenceGetter
        )
    }
