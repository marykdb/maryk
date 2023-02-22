package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import kotlin.reflect.KProperty

/** Loads delegate for wrapper from [wrapperCreator] and sets it on [propertyDefinitions] */
class DefinitionWrapperDelegateLoader<W : IsDefinitionWrapper<*, *, *, Any>>(
    private val propertyDefinitions: IsValuesPropertyDefinitions,
    private val wrapperCreator: (String) -> W
) {
    operator fun provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>
    ) =
        wrapperCreator(prop.name).also(propertyDefinitions::addSingle)
}

/** Loads delegate for wrapper from [wrapperCreator] and sets it on object [propertyDefinitions] */
class ObjectDefinitionWrapperDelegateLoader<W : IsDefinitionWrapper<*, *, CX, DO>, DO: Any, CX: IsPropertyContext>(
    private val propertyDefinitions: ObjectPropertyDefinitions<DO>,
    private val wrapperCreator: (String) -> W
) {
    operator fun provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>
    ) =
        wrapperCreator(prop.name).also(propertyDefinitions::addSingle)
}
