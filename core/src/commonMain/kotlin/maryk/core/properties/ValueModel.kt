package maryk.core.properties

import maryk.core.models.ValueDataModel
import maryk.core.properties.types.ValueDataObject
import maryk.core.values.ObjectValues
import kotlin.reflect.KClass

interface IsValueModel<DO: ValueDataObject, P: IsObjectPropertyDefinitions<DO>>: IsBaseModel<DO, P, IsPropertyContext, IsPropertyContext> {
    @Suppress("PropertyName")
    override val Model: ValueDataModel<DO, P>
}

abstract class ValueModel<DO: ValueDataObject, P: ObjectPropertyDefinitions<DO>>(
    objClass: KClass<DO>,
): ObjectPropertyDefinitions<DO>(), IsValueModel<DO, P> {

    abstract fun invoke(values: ObjectValues<DO, P>): DO

    fun toBytes(vararg inputs: Any) = Model.toBytes(*inputs)

    @Suppress("UNCHECKED_CAST")
    override val Model = object: ValueDataModel<DO, P>(
        name = objClass.simpleName!!,
        properties = this@ValueModel as P,
    ) {
        override fun invoke(values: ObjectValues<DO, P>): DO =
            this@ValueModel.invoke(values)
    }
}
