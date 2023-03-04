package maryk.core.properties

import maryk.core.models.SingleValueDataModel
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.values.ObjectValues

interface IsSingleValueModel<T : Any, TO : Any, DO : Any, P : IsObjectPropertyDefinitions<DO>, CX : IsPropertyContext> {
    @Suppress("PropertyName")
    val Model: SingleValueDataModel<T, TO, DO, P, CX>
}

typealias SingleTypedValueModel<T, DO, P, CX> = SingleValueModel<T, T, DO, P, CX>

abstract class SingleValueModel<T : Any, TO : Any, DO : Any, P : IsObjectPropertyDefinitions<DO>, CX : IsPropertyContext>(
    singlePropertyDefinitionGetter: () -> IsDefinitionWrapper<T, out TO, CX, DO>,
): ObjectPropertyDefinitions<DO>(), IsSingleValueModel<T, TO, DO, P, CX> {
    abstract fun invoke(values: ObjectValues<DO, P>): DO

    @Suppress("UNCHECKED_CAST", "LeakingThis")
    override val Model = object: SingleValueDataModel<T, TO, DO, P, CX>(
        properties = this@SingleValueModel as P,
        singlePropertyDefinitionGetter = singlePropertyDefinitionGetter,
    ) {
        override fun invoke(values: ObjectValues<DO, P>): DO =
            this@SingleValueModel.invoke(values)
    }
}
