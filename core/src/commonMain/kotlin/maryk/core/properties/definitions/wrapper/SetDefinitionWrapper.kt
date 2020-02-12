package maryk.core.properties.definitions.wrapper

import co.touchlab.stately.concurrency.AtomicReference
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsSetDefinition
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.references.SetReference
import kotlin.reflect.KProperty

/**
 * Contains a Set property [definition] containing type [T]
 * It contains an [index] and [name] to which it is referred inside DataModel, and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class SetDefinitionWrapper<T : Any, CX : IsPropertyContext, DO : Any> internal constructor(
    override val index: UInt,
    override val name: String,
    override val definition: IsSetDefinition<T, CX>,
    override val alternativeNames: Set<String>? = null,
    override val getter: (DO) -> Set<T>? = { null },
    override val capturer: (Unit.(CX, Set<T>) -> Unit)? = null,
    override val toSerializable: (Unit.(Set<T>?, CX?) -> Set<T>?)? = null,
    override val fromSerializable: (Unit.(Set<T>?) -> Set<T>?)? = null,
    override val shouldSerialize: (Unit.(Any) -> Boolean)? = null
) :
    AbstractDefinitionWrapper(index, name),
    IsSetDefinition<T, CX> by definition,
    IsDefinitionWrapper<Set<T>, Set<T>, CX, DO> {
    override val graphType = PropRef

    private val setItemRefCache : AtomicReference<Array<IsPropertyReference<*, *, *>>?> =
        AtomicReference(null)

    override fun ref(parentRef: AnyPropertyReference?) = cacheRef(parentRef) {
        SetReference(this, parentRef as CanHaveComplexChildReference<*, *, *, *>?)
    }

    /** Get a reference to a specific set item by [value] with optional [parentRef] */
    private fun itemRef(value: T, parentRef: AnyPropertyReference? = null) = this.ref(parentRef).let { ref ->
        cacheRef(ref, setItemRefCache, { (it.parentReference as SetReference<*, *>).parentReference === parentRef && it.value == value}) {
            this.definition.itemRef(value, ref)
        }
    }

    /** For quick notation to get a set [item] reference */
    infix fun refAt(item: T): (AnyOutPropertyReference?) -> SetItemReference<T, *> {
        return { this.itemRef(item, it) }
    }

    // For delegation in definition
    operator fun getValue(thisRef: Any, property: KProperty<*>) = this
}
