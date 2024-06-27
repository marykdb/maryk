package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.ListAnyItemReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.ListReference

/**
 * Contains a List property [definition] which contains items of type [T]
 * It contains an [index] and [name] to which it is referred inside DataModel, and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
interface IsListDefinitionWrapper<T : Any, TO : Any, LD : IsListDefinition<T, CX>, CX : IsPropertyContext, in DO : Any> :
    IsListDefinition<T, CX>,
    IsDefinitionWrapper<List<T>, List<TO>, CX, DO>,
    CacheableReferenceCreator {
    override val definition: LD

    @Suppress("UNCHECKED_CAST")
    override fun ref(parentRef: AnyPropertyReference?) = cacheRef(parentRef) {
        ListReference(
            this as IsListDefinitionWrapper<T, Any, ListDefinition<T, CX>, CX, *>,
            parentRef as CanHaveComplexChildReference<*, *, *, *>?
        )
    }

    /** Get a reference to a specific list item by [index] with optional [parentRef] */
    fun getItemRef(index: UInt, parentRef: AnyPropertyReference? = null) = cacheRef(parentRef, { "${it?.completeName}.@$index" }) {
        this.definition.itemRef(index, this.ref(parentRef))
    }

    /** Get a reference to a specific list item at any index with optional [parentRef] */
    fun getAnyItemRef(parentRef: AnyPropertyReference? = null): ListAnyItemReference<T, CX> = cacheRef(parentRef, { "${it?.completeName}.*" }) {
        this.definition.anyItemRef(this.ref(parentRef))
    }

    /** For quick notation to get any list item reference */
    fun refToAny(): (AnyOutPropertyReference?) -> ListAnyItemReference<T, CX> =
        this::getAnyItemRef

    /** For quick notation to get a list item reference by [index] */
    infix fun refAt(index: UInt): (AnyOutPropertyReference?) -> ListItemReference<T, CX> =
        { this.getItemRef(index, it) }
}
