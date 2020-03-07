package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanContainMapItemReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MapAnyValueReference
import maryk.core.properties.references.MapKeyReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.references.PropertyReferenceForValues
import maryk.lib.concurrency.AtomicReference

interface IsMapDefinitionWrapper<K : Any, V : Any, TO : Any, CX : IsPropertyContext, in DO : Any> :
    IsDefinitionWrapper<Map<K, V>, TO, CX, DO>,
    CacheableReferenceCreator {
    override val definition: IsMapDefinition<K, V, CX>

    val anyItemRefCache: AtomicReference<Array<IsPropertyReference<*, *, *>>?>
    val keyRefCache: AtomicReference<Array<IsPropertyReference<*, *, *>>?>
    val valueRefCache: AtomicReference<Array<IsPropertyReference<*, *, *>>?>

    /** Get a reference to a specific map [key] with optional [parentRef] */
    private fun keyRef(key: K, parentRef: AnyPropertyReference? = null) = this.ref(parentRef).let { ref ->
        cacheRef(ref, keyRefCache, { (it.parentReference as PropertyReferenceForValues<*, *, *, *>).parentReference === parentRef && it.key == key }) {
            this.definition.keyRef(key, ref as CanContainMapItemReference<*, *, *>)
        }
    }

    /** Get a reference to a specific map value by [key] with optional [parentRef] */
    private fun valueRef(key: K, parentRef: AnyPropertyReference? = null) = this.ref(parentRef).let { ref ->
        cacheRef(ref, valueRefCache, { (it.parentReference as PropertyReferenceForValues<*, *, *, *>).parentReference === parentRef && it.key == key }) {
            this.definition.valueRef(key, ref as CanContainMapItemReference<*, *, *>)
        }
    }

    /** Get a reference to any map value with optional [parentRef] */
    private fun anyValueRef(parentRef: AnyPropertyReference? = null) = this.ref(parentRef).let { ref ->
        cacheRef(ref, anyItemRefCache) {
            this.definition.anyValueRef(ref as CanContainMapItemReference<*, *, *>)
        }
    }

    /** For quick notation to get a map [key] reference */
    infix fun refToKey(key: K): (AnyOutPropertyReference?) -> MapKeyReference<K, V, *> =
        { this.keyRef(key, it) }

    /** For quick notation to get a map value reference at given [key] */
    infix fun refAt(key: K): (AnyOutPropertyReference?) -> MapValueReference<K, V, *> =
        { this.valueRef(key, it) }

    /** For quick notation to get a map value reference at any key */
    fun refToAny(): (AnyOutPropertyReference?) -> MapAnyValueReference<K, V, *> =
        { this.anyValueRef(it) }
}
