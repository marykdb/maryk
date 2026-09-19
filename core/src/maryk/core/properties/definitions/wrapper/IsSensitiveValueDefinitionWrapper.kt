package maryk.core.properties.definitions.wrapper

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsPropertyDefinition

/**
 * Wrapper for simple value definitions which can be marked as sensitive.
 */
interface IsSensitiveValueDefinitionWrapper<T : Any, TO : Any, in CX : IsPropertyContext, in DO> :
    IsValueDefinitionWrapper<T, TO, CX, DO> {
    val sensitive: Boolean

    override fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        checkedDataModelNames: MutableList<String>?,
        addIncompatibilityReason: ((String) -> Unit)?
    ): Boolean {
        var compatible = super<IsValueDefinitionWrapper>.compatibleWith(
            definition,
            checkedDataModelNames,
            addIncompatibilityReason,
        )

        if (definition !is IsSensitiveValueDefinitionWrapper<*, *, *, *> || sensitive != definition.sensitive) {
            addIncompatibilityReason?.invoke("Sensitive property layout changed")
            compatible = false
        }

        return compatible
    }

    override fun compatibleWith(
        wrapper: IsDefinitionWrapper<*, *, *, *>,
        checkedDataModelNames: MutableList<String>?,
        addIncompatibilityReason: ((String) -> Unit)?
    ): Boolean {
        var compatible = super<IsValueDefinitionWrapper>.compatibleWith(
            wrapper,
            checkedDataModelNames,
            addIncompatibilityReason,
        )

        if (wrapper !is IsSensitiveValueDefinitionWrapper<*, *, *, *> || sensitive != wrapper.sensitive) {
            addIncompatibilityReason?.invoke("Sensitive property layout changed")
            compatible = false
        }

        return compatible
    }
}
