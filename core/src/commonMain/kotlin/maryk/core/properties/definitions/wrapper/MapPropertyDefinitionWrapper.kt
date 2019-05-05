package maryk.core.properties.definitions.wrapper

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.enum.EmbedTypeCase
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.ListTypeCase
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ListItemReference
import maryk.core.properties.references.MapAnyValueReference
import maryk.core.properties.references.MapKeyReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.references.SetItemReference
import maryk.core.properties.types.TypedValue
import maryk.core.values.Values

/**
 * Contains a Map property [definition] which contains keys [K] and values [V]
 * It contains an [index] and [name] to which it is referred inside DataModel and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class MapPropertyDefinitionWrapper<K : Any, V : Any, TO : Any, CX : IsPropertyContext, in DO : Any> internal constructor(
    override val index: UInt,
    override val name: String,
    override val definition: MapDefinition<K, V, CX>,
    override val getter: (DO) -> TO? = { null },
    override val capturer: ((CX, Map<K, V>) -> Unit)? = null,
    override val toSerializable: ((TO?, CX?) -> Map<K, V>?)? = null,
    override val fromSerializable: ((Map<K, V>?) -> TO?)? = null,
    override val shouldSerialize: ((Any) -> Boolean)? = null
) :
    AbstractPropertyDefinitionWrapper(index, name),
    IsMapDefinition<K, V, CX> by definition,
    IsPropertyDefinitionWrapper<Map<K, V>, TO, CX, DO> {
    override val graphType = PropRef

    @Suppress("UNCHECKED_CAST")
    override fun ref(parentRef: AnyPropertyReference?): MapReference<K, V, CX> =
        MapReference(
            this as MapPropertyDefinitionWrapper<K, V, Any, CX, *>,
            parentRef as CanHaveComplexChildReference<*, *, *, *>?
        )

    /** Get a reference to a specific map [key] with optional [parentRef] */
    private fun keyRef(key: K, parentRef: AnyPropertyReference? = null) =
        this.definition.keyRef(key, this.ref(parentRef))

    /** Get a reference to a specific map value by [key] with optional [parentRef] */
    internal fun valueRef(key: K, parentRef: AnyPropertyReference? = null) =
        this.definition.valueRef(key, this.ref(parentRef))

    /** Get a reference to any map value with optional [parentRef] */
    internal fun anyValueRef(parentRef: AnyPropertyReference? = null) =
        this.definition.anyValueRef(this.ref(parentRef))

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

/** Specific extension to support fetching sub refs on Map values by [key] */
fun <K : Any, V : Values<*, P>, DM : IsValuesDataModel<P>, P : PropertyDefinitions, T : Any, W : IsPropertyDefinitionWrapper<T, *, *, *>> MapPropertyDefinitionWrapper<K, V, *, *, *>.refAtKey(
    key: K,
    propertyDefinitionGetter: P.() -> W
): (AnyOutPropertyReference?) -> IsPropertyReference<T, W, *> =
    {
        @Suppress("UNCHECKED_CAST")
        propertyDefinitionGetter(
            (this.definition.valueDefinition as EmbeddedValuesDefinition<DM, P>).dataModel.properties
        ).ref(
            this.valueRef(key, it)
        ) as IsPropertyReference<T, W, *>
    }

/** Specific extension to support fetching sub refs on Map values by any key */
@Suppress("UNCHECKED_CAST")
fun <K : Any, V : Values<*, P>, DM : IsValuesDataModel<P>, P : PropertyDefinitions, T : Any, W : IsPropertyDefinitionWrapper<T, *, *, *>> MapPropertyDefinitionWrapper<K, V, *, *, *>.refToAny(
    propertyDefinitionGetter: P.() -> W
): (AnyOutPropertyReference?) -> IsPropertyReference<T, W, *> =
    {
        @Suppress("UNCHECKED_CAST")
        propertyDefinitionGetter(
            (this.definition.valueDefinition as EmbeddedValuesDefinition<DM, P>).dataModel.properties
        ).ref(
            this.anyValueRef(it)
        ) as IsPropertyReference<T, W, *>
    }

/** Specific extension to support fetching sub refs on Map values by [key] and [type] */
@Suppress("UNCHECKED_CAST")
fun <K : Any, E : IndexedEnum, P : PropertyDefinitions, T : Any, W : IsPropertyDefinitionWrapper<T, *, *, *>> MapPropertyDefinitionWrapper<K, TypedValue<E, Any>, *, *, *>.refAtKeyAndType(
    key: K,
    type: E,
    @Suppress("UNUSED_PARAMETER") properties: P, // So it is not needed to pass in types
    propertyDefinitionGetter: P.() -> W
): (AnyOutPropertyReference?) -> IsPropertyReference<T, W, *> =
    {
        val multiTypeDef = (this.definition.valueDefinition as IsMultiTypeDefinition<E, IsPropertyContext>)
        val typedValueRef = multiTypeDef.typedValueRef(type, this.valueRef(key, it))
        propertyDefinitionGetter(
            (multiTypeDef.definitionMap[type] as EmbeddedValuesDefinition<IsValuesDataModel<P>, P>).dataModel.properties
        ).ref(
            typedValueRef
        ) as IsPropertyReference<T, W, *>
    }

/** Specific extension to support fetching sub refs on Map values by [key] and [type] */
@Suppress("UNCHECKED_CAST")
fun <K : Any, E : IndexedEnum, P : PropertyDefinitions, T : Any, W : IsPropertyDefinitionWrapper<T, *, *, *>> MapPropertyDefinitionWrapper<K, TypedValue<E, Any>, *, *, *>.refAtKeyAndType(
    key: K,
    type: EmbedTypeCase<E, P>,
    propertyDefinitionGetter: P.() -> W
): (AnyOutPropertyReference?) -> IsPropertyReference<T, W, *> =
    {
        val multiTypeDef = (this.definition.valueDefinition as IsMultiTypeDefinition<E, IsPropertyContext>)
        val typedValueRef = multiTypeDef.typedValueRef(type as E, this.valueRef(key, it))
        propertyDefinitionGetter(
            (multiTypeDef.definitionMap[type] as EmbeddedValuesDefinition<IsValuesDataModel<P>, P>).dataModel.properties
        ).ref(
            typedValueRef
        ) as IsPropertyReference<T, W, *>
    }

/** Specific extension to support fetching sub refs on Map values by [key] and [type] */
@Suppress("UNCHECKED_CAST")
fun <K : Any, E : IndexedEnum, T : Any> MapPropertyDefinitionWrapper<K, TypedValue<E, Any>, *, *, *>.refAtKeyTypeAndIndexWeak(
    key: K,
    type: E,
    listIndex: UInt
): (AnyOutPropertyReference?) -> ListItemReference<T, *> =
    {
        val multiTypeDef = (this.definition.valueDefinition as IsMultiTypeDefinition<E, IsPropertyContext>)
        val typedValueRef = multiTypeDef.typedValueRef(type, this.valueRef(key, it))
        (multiTypeDef.definitionMap[type] as ListDefinition<T, *>).itemRef(
            listIndex,
            typedValueRef
        )
    }

/** Specific extension to support fetching sub refs on Map values by [key] and [type] */
@Suppress("UNCHECKED_CAST")
fun <K : Any, E : IndexedEnum, T : Any> MapPropertyDefinitionWrapper<K, TypedValue<E, Any>, *, *, *>.refAtKeyTypeAndIndex(
    key: K,
    type: ListTypeCase<E, T>,
    listIndex: UInt
): (AnyOutPropertyReference?) -> ListItemReference<T, *> =
    {
        val multiTypeDef = (this.definition.valueDefinition as IsMultiTypeDefinition<E, IsPropertyContext>)
        val typedValueRef = multiTypeDef.typedValueRef(type as E, this.valueRef(key, it))
        (multiTypeDef.definitionMap[type] as ListDefinition<T, *>).itemRef(
            listIndex,
            typedValueRef
        )
    }

/** Specific extension to support fetching list item refs on Map values by [key] and [listIndex] */
@Suppress("UNCHECKED_CAST")
fun <K : Any, T : Any> MapPropertyDefinitionWrapper<K, List<T>, *, *, *>.refAtKeyAndIndex(
    key: K,
    listIndex: UInt
): (AnyOutPropertyReference?) -> ListItemReference<T, *> =
    {
        (this.valueDefinition as ListDefinition<T, *>).itemRef(
            listIndex,
            this.valueRef(key, it)
        )
    }

/** Specific extension to support fetching set item refs on Map values by [key] and [setItem] */
@Suppress("UNCHECKED_CAST")
fun <K : Any, T : Any> MapPropertyDefinitionWrapper<K, Set<T>, *, *, *>.refAtKeyAndIndex(
    key: K,
    setItem: T
): (AnyOutPropertyReference?) -> SetItemReference<T, *> =
    {
        (this.valueDefinition as SetDefinition<T, *>).itemRef(
            setItem,
            this.valueRef(key, it)
        )
    }

/** Specific extension to support fetching deeper references on Map values by any key */
fun <K : Any, V : Values<*, P>, DM : IsValuesDataModel<P>, P : PropertyDefinitions, T : Any, W : IsPropertyDefinitionWrapper<T, *, *, *>, R : IsPropertyReference<T, W, *>> MapPropertyDefinitionWrapper<K, V, *, *, *>.any(
    referenceGetter: P.() -> (AnyOutPropertyReference?) -> R
): (AnyOutPropertyReference?) -> R =
    {
        @Suppress("UNCHECKED_CAST")
        (this.definition.valueDefinition as EmbeddedValuesDefinition<DM, P>).dataModel(
            this.anyValueRef(it),
            referenceGetter
        )
    }

/** Specific extension to support fetching deeper references on Map values by [key] and [type] */
@Suppress("UNCHECKED_CAST")
fun <K : Any, E : IndexedEnum, P : PropertyDefinitions, T : Any, R : IsPropertyReference<T, IsPropertyDefinitionWrapper<T, *, *, *>, *>> MapPropertyDefinitionWrapper<K, TypedValue<E, Any>, *, *, *>.atKeyAndType(
    key: K,
    type: E,
    @Suppress("UNUSED_PARAMETER") properties: P, // So it is not needed to pass in types
    referenceGetter: P.() -> (AnyOutPropertyReference?) -> R
): (AnyOutPropertyReference?) -> R =
    {
        val multiTypeDef = (this.definition.valueDefinition as IsMultiTypeDefinition<E, IsPropertyContext>)
        val typedValueRef = multiTypeDef.typedValueRef(type, this.valueRef(key, it))
        (multiTypeDef.definitionMap[type] as EmbeddedValuesDefinition<IsValuesDataModel<P>, P>).dataModel(
            typedValueRef,
            referenceGetter
        )
    }

/** Specific extension to support fetching deeper references on Map values by [key] and [type] */
@Suppress("UNCHECKED_CAST")
fun <K : Any, E : IndexedEnum, P : PropertyDefinitions, T : Any, R : IsPropertyReference<T, IsPropertyDefinitionWrapper<T, *, *, *>, *>> MapPropertyDefinitionWrapper<K, TypedValue<E, Any>, *, *, *>.atKeyAndType(
    key: K,
    type: EmbedTypeCase<E, P>,
    referenceGetter: P.() -> (AnyOutPropertyReference?) -> R
): (AnyOutPropertyReference?) -> R =
    {
        val multiTypeDef = (this.definition.valueDefinition as IsMultiTypeDefinition<E, IsPropertyContext>)
        val typedValueRef = multiTypeDef.typedValueRef(type as E, this.valueRef(key, it))
        (multiTypeDef.definitionMap[type] as EmbeddedValuesDefinition<IsValuesDataModel<P>, P>).dataModel(
            typedValueRef,
            referenceGetter
        )
    }
