package maryk.core.properties.references.dsl

import maryk.core.models.IsValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanContainMapItemReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.values.Values
import kotlin.jvm.JvmName

/** Specific extension to support fetching deeper references on Map values by [key] */
@JvmName("atEmbed")
fun <K : Any, V : Values<*, P>, DM : IsValuesDataModel<P>, P : PropertyDefinitions, T : Any, W : IsPropertyDefinitionWrapper<T, *, *, *>, R : IsPropertyReference<T, W, *>> IsSubDefinition<Map<K, V>, *>.at(
    key: K,
    referenceGetter: P.() -> (AnyOutPropertyReference?) -> R
): (AnyOutPropertyReference?) -> R =
    {
        val mapDefinition = this as IsMapDefinition<K, V, *>

        val parent = if (this is IsPropertyDefinitionWrapper<*, *, *, *>) {
            this.ref(it)
        } else it

        @Suppress("UNCHECKED_CAST")
        (mapDefinition.valueDefinition as EmbeddedValuesDefinition<DM, P>).dataModel(
            this.valueRef(key, parent as CanContainMapItemReference<*, *, *>),
            referenceGetter
        )
    }
