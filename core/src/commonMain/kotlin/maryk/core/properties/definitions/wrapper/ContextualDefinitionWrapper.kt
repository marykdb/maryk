package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsContextualEncodable
import maryk.core.properties.graph.PropRefGraphType.PropRef
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.PropertyReferenceForValues
import kotlin.reflect.KProperty

/**
 * Contains a Flex bytes property [definition] of type [T] which cannot be used in keys or ValueObjects
 * It contains an [index] and [name] to which it is referred inside DataModel, and a [getter]
 * function to retrieve value on dataObject of [DO] in context [CX]
 */
data class ContextualDefinitionWrapper<T : Any, TO : Any, CX : IsPropertyContext, D : IsContextualEncodable<T, CX>, DO : Any> internal constructor(
    override val index: UInt,
    override val name: String,
    override val definition: D,
    override val alternativeNames: Set<String>? = null,
    override val getter: (DO) -> TO? = { null },
    override val capturer: (Unit.(CX, T) -> Unit)? = null,
    override val toSerializable: (Unit.(TO?, CX?) -> T?)? = null,
    override val fromSerializable: (Unit.(T?) -> TO?)? = null,
    override val shouldSerialize: (Unit.(Any) -> Boolean)? = null
) :
    AbstractDefinitionWrapper(index, name),
    IsDefinitionWrapper<T, TO, CX, DO>,
    IsContextualEncodable<T, CX> by definition,
    IsValueDefinitionWrapper<T, TO, CX, DO> {
    override val graphType = PropRef

    override fun ref(parentRef: AnyPropertyReference?) =
        PropertyReferenceForValues(this, parentRef)

    // For delegation in definition
    operator fun getValue(thisRef: Any?, property: KProperty<*>) = this
}

fun <T : Any, TO : Any, CX : IsPropertyContext, D : IsContextualEncodable<T, CX>, DO : Any> ObjectPropertyDefinitions<DO>.contextual(
    index: UInt,
    name: String? = null,
    definition: D,
    alternativeNames: Set<String>? = null,
    getter: (DO) -> TO? = { null },
    capturer: (Unit.(CX, T) -> Unit)? = null,
    toSerializable: (Unit.(TO?, CX?) -> T?)? = null,
    fromSerializable: (Unit.(T?) -> TO?)? = null,
    shouldSerialize: (Unit.(Any) -> Boolean)? = null
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    ContextualDefinitionWrapper(
        index = index,
        name = name ?: propName,
        definition = definition,
        alternativeNames = alternativeNames,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
