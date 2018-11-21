package maryk.core.properties.definitions

import maryk.core.models.IsDataModel
import maryk.core.properties.AbstractPropertyDefinitions

typealias IsAnyEmbeddedDefinition = IsEmbeddedDefinition<IsDataModel<AbstractPropertyDefinitions<Any>>, AbstractPropertyDefinitions<Any>>

/** Interface for property definitions containing embedded DataObjects of [DO] and context [CX]. */
interface IsEmbeddedDefinition<out DM : IsDataModel<P>, P: AbstractPropertyDefinitions<*>> {
    val dataModel: DM
}
