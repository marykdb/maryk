package maryk.core.properties.definitions.wrapper

import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.definitions.IsWrappableDefinition
import kotlin.reflect.KProperty

/** Loads wrapper for [definition] and sets it on property definitions at [index] */
class WrapperLoader<T : Any, D : IsWrappableDefinition<T, *, W>, W: IsDefinitionWrapper<T, T, *, DO>, DO : Any>(
    val index: UInt,
    val definition: D,
    val alternativeNames: Set<String>? = null
) {
    operator fun provideDelegate(
        thisRef: AbstractPropertyDefinitions<DO>,
        prop: KProperty<*>
    ) =
        definition.wrap(index, prop.name, alternativeNames).also {
            thisRef.addSingle(it)
        }
}
