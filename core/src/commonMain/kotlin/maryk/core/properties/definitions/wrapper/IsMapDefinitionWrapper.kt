package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanContainMapItemReference
import maryk.core.properties.references.MapAnyValueReference
import maryk.core.properties.references.MapKeyReference
import maryk.core.properties.references.MapValueReference

interface IsMapDefinitionWrapper<K : Any, V : Any, TO : Any, CX : IsPropertyContext, in DO : Any> : IsDefinitionWrapper<Map<K, V>, TO, CX, DO> {
    override val definition: IsMapDefinition<K, V, CX>

    /** Get a reference to a specific map [key] with optional [parentRef] */
    private fun keyRef(key: K, parentRef: AnyPropertyReference? = null): MapKeyReference<K, V, CX> =
        this.definition.keyRef(key, this.ref(parentRef) as CanContainMapItemReference<*, *, *>)

    /** Get a reference to a specific map value by [key] with optional [parentRef] */
    private fun valueRef(key: K, parentRef: AnyPropertyReference? = null): MapValueReference<K, V, CX> =
        this.definition.valueRef(key, this.ref(parentRef) as CanContainMapItemReference<*, *, *>)

    /** Get a reference to any map value with optional [parentRef] */
    private fun anyValueRef(parentRef: AnyPropertyReference? = null): MapAnyValueReference<K, V, CX> =
        this.definition.anyValueRef(this.ref(parentRef) as CanContainMapItemReference<*, *, *>)

    /** For quick notation to get a map [key] reference */
    infix fun refToKey(key: K): (AnyOutPropertyReference?) -> MapKeyReference<K, V, *> {
        return { this.keyRef(key, it) }
    }

    /** For quick notation to get a map value reference at given [key] */
    infix fun refAt(key: K): (AnyOutPropertyReference?) -> MapValueReference<K, V, *> {
        return { this.valueRef(key, it) }
    }

    /** For quick notation to get a map value reference at any key */
    fun refToAny(): (AnyOutPropertyReference?) -> MapAnyValueReference<K, V, *> {
        return { this.anyValueRef(it) }
    }
}
