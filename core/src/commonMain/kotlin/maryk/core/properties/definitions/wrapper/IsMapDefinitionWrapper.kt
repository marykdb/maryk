package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanContainMapItemReference
import maryk.core.properties.references.MapAnyKeyReference
import maryk.core.properties.references.MapAnyValueReference
import maryk.core.properties.references.MapKeyReference
import maryk.core.properties.references.MapValueReference

interface IsMapDefinitionWrapper<K : Any, V : Any, TO : Any, CX : IsPropertyContext, in DO : Any> :
    IsDefinitionWrapper<Map<K, V>, TO, CX, DO>,
    CacheableReferenceCreator {
    override val definition: IsMapDefinition<K, V, CX>

    /** Get a reference to a specific map [key] with optional [parentRef] */
    private fun keyRef(key: K, parentRef: AnyPropertyReference? = null) =
        this.ref(parentRef).let { ref ->
            this.definition.keyRef(key, ref as CanContainMapItemReference<*, *, *>)
        }

    /** Get a reference to a specific map value by [key] with optional [parentRef] */
    private fun valueRef(key: K, parentRef: AnyPropertyReference? = null) =
        this.ref(parentRef).let { ref ->
            this.definition.valueRef(key, ref as CanContainMapItemReference<*, *, *>)
        }

    /** Get a reference to any map key with optional [parentRef] */
    private fun anyKeyRef(parentRef: AnyPropertyReference? = null) = this.ref(parentRef).let { ref ->
        cacheRef(ref, "~") {
            this.definition.anyKeyRef(ref as CanContainMapItemReference<*, *, *>)
        }
    }

    /** Get a reference to any map value with optional [parentRef] */
    private fun anyValueRef(parentRef: AnyPropertyReference? = null) = this.ref(parentRef).let { ref ->
        cacheRef(ref, "*") {
            this.definition.anyValueRef(ref as CanContainMapItemReference<*, *, *>)
        }
    }

    /** Select a map key reference for [key]. */
    infix fun key(key: K): IsReferenceCreator<MapKeyReference<K, V, *>> =
        IsReferenceCreator { this.keyRef(key, it) }

    /** Select a map value reference at [key]. */
    infix fun at(key: K): IsReferenceCreator<MapValueReference<K, V, *>> =
        IsReferenceCreator { this.valueRef(key, it) }

    /** @deprecated Use [key]. */
    @Deprecated("Use key(key)", ReplaceWith("key(key)::ref"))
    infix fun refToKey(key: K): (AnyOutPropertyReference?) -> MapKeyReference<K, V, *> =
        { this.key(key).ref(it) }

    /** @deprecated Use [at]. */
    @Deprecated("Use at(key)", ReplaceWith("at(key)::ref"))
    infix fun refAt(key: K): (AnyOutPropertyReference?) -> MapValueReference<K, V, *> =
        { this.at(key).ref(it) }

    /** For quick notation to get a map key reference at any key. */
    fun anyKey(): (AnyOutPropertyReference?) -> MapAnyKeyReference<K, V, *> =
        this::anyKeyRef

    /** For quick notation to get a map value reference at any key. */
    fun anyValue(): (AnyOutPropertyReference?) -> MapAnyValueReference<K, V, *> =
        this::anyValueRef

    /** @deprecated Use [anyKey]. */
    @Deprecated("Use anyKey()", ReplaceWith("anyKey()"))
    fun refToAnyKey(): (AnyOutPropertyReference?) -> MapAnyKeyReference<K, V, *> = anyKey()

    /** @deprecated Use [anyValue]. */
    @Deprecated("Use anyValue()", ReplaceWith("anyValue()"))
    fun refToAnyValue(): (AnyOutPropertyReference?) -> MapAnyValueReference<K, V, *> = anyValue()

}
