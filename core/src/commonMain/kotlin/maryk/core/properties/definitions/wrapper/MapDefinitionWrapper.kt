package maryk.core.properties.definitions.wrapper

import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MapReference
import maryk.lib.concurrency.AtomicReference
import kotlin.reflect.KProperty

/**
 * Contains a Map property [definition] which contains keys [K] and values [V]
 * It contains an [index] and [name] to which it is referred inside DataModel, and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class MapDefinitionWrapper<K : Any, V : Any, TO : Any, CX : IsPropertyContext, DO : Any> internal constructor(
    override val index: UInt,
    override val name: String,
    override val definition: IsMapDefinition<K, V, CX>,
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

    override val anyItemRefCache: AtomicReference<Array<IsPropertyReference<*, *, *>>?> =
        AtomicReference(null)
    override val keyRefCache: AtomicReference<Array<IsPropertyReference<*, *, *>>?> =
        AtomicReference(null)
    override val valueRefCache: AtomicReference<Array<IsPropertyReference<*, *, *>>?> =
        AtomicReference(null)

    @Suppress("UNCHECKED_CAST")
    override fun ref(parentRef: AnyPropertyReference?): MapReference<K, V, CX> = cacheRef(parentRef, anyItemRefCache) {
        MapReference(
            this as MapDefinitionWrapper<K, V, Any, CX, *>,
            parentRef as CanHaveComplexChildReference<*, *, *, *>?
        )
    }

    // For delegation in definition
    operator fun getValue(thisRef: AbstractPropertyDefinitions<DO>, property: KProperty<*>) = this
}
