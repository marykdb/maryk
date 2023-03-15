package maryk.core.properties.references.dsl

import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.invoke
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanContainMapItemReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.Values

/** Specific extension to support fetching deeper references on Map values by any key */
fun <K : Any, V : Values<DM>, DM : IsValuesPropertyDefinitions, T : Any, R : IsPropertyReference<T, IsPropertyDefinition<T>, *>> IsMapDefinition<K, V, *>.any(
    referenceGetter: DM.() -> (AnyOutPropertyReference?) -> R
): (AnyOutPropertyReference?) -> R =
    {
        val parent = if (this is IsDefinitionWrapper<*, *, *, *>) {
            this.ref(it)
        } else it

        @Suppress("UNCHECKED_CAST")
        (this.valueDefinition as EmbeddedValuesDefinition<DM>).dataModel(
            this.anyValueRef(parent as CanContainMapItemReference<*, *, *>),
            referenceGetter
        )
    }
