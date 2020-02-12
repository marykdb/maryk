package maryk.core.properties.definitions.wrapper

import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import kotlin.reflect.KProperty

/** Loads delegate for wrapper from [wrapperCreator] and sets it on [propertyDefinitions] */
class DefinitionWrapperDelegateLoader<W : IsDefinitionWrapper<*, *, *, Any>>(
    private val propertyDefinitions: PropertyDefinitions,
    private val wrapperCreator: (String) -> W
) {
    operator fun provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>
    ) =
        wrapperCreator(prop.name).also {
            propertyDefinitions.addSingle(it)
        }
}

/** Loads delegate for wrapper from [wrapperCreator] and sets it on object [propertyDefinitions] */
class ObjectDefinitionWrapperDelegateLoader<W : IsDefinitionWrapper<*, *, *, DO>, DO: Any>(
    private val propertyDefinitions: ObjectPropertyDefinitions<DO>,
    private val wrapperCreator: (String) -> W
) {
    operator fun provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>
    ) =
        wrapperCreator(prop.name).also {
            propertyDefinitions.addSingle(it)
        }
}
