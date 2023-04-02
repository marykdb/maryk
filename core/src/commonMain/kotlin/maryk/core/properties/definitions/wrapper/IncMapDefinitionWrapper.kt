package maryk.core.properties.definitions.wrapper

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import maryk.core.models.AbstractDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IncrementingMapDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanContainMapItemReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IncMapAddIndexReference
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.references.IsPropertyReference
import kotlin.reflect.KProperty

/**
 * Contains an incrementing Map property [definition] which contains keys [K] and values [V]
 * It contains an [index] and [name] to which it is referred inside DataModel, and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class IncMapDefinitionWrapper<K : Comparable<K>, V : Any, TO : Any, CX : IsPropertyContext, DO : Any> internal constructor(
    override val index: UInt,
    override val name: String,
    override val definition: IncrementingMapDefinition<K, V, CX>,
    override val alternativeNames: Set<String>? = null,
    override val getter: (DO) -> TO? = { null },
    override val capturer: (Unit.(CX, Map<K, V>) -> Unit)? = null,
    override val toSerializable: (Unit.(TO?, CX?) -> Map<K, V>?)? = null,
    override val fromSerializable: (Unit.(Map<K, V>?) -> TO?)? = null,
    override val shouldSerialize: (Unit.(Any) -> Boolean)? = null
) :
    AbstractDefinitionWrapper(index, name),
    IsMapDefinition<K, V, CX> by definition,
    IsMapDefinitionWrapper<K, V, TO, CX, DO> {
    override val graphType = PropRef

    override val anyItemRefCache: AtomicRef<Array<IsPropertyReference<*, *, *>>?> =
        atomic(null)
    override val keyRefCache: AtomicRef<Array<IsPropertyReference<*, *, *>>?> =
        atomic(null)
    override val valueRefCache: AtomicRef<Array<IsPropertyReference<*, *, *>>?> =
        atomic(null)

    @Suppress("UNCHECKED_CAST")
    override fun ref(parentRef: AnyPropertyReference?): IncMapReference<K, V, CX> = cacheRef(parentRef) {
        IncMapReference(
            this as IncMapDefinitionWrapper<K, V, Any, CX, *>,
            parentRef as CanHaveComplexChildReference<*, *, *, *>?
        )
    }

    /** Create a reference which points to an [index] for an add of Incremental Map value */
    fun addIndexRef(index: Int, parentRef: AnyPropertyReference?) =
        IncMapAddIndexReference(
            index = index,
            mapDefinition = this.definition,
            parentReference = parentRef as CanContainMapItemReference<*, *, *>
        )

    // For delegation in definition
    operator fun getValue(thisRef: AbstractDataModel<DO>, property: KProperty<*>) = this
}
