package maryk.core.models

import maryk.core.models.definitions.ValueDataModelDefinition
import maryk.core.models.serializers.ValueDataModelSerializer
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.types.ValueDataObject
import maryk.core.values.ObjectValues
import kotlin.reflect.KClass

abstract class ValueDataModel<DO: ValueDataObject, P: IsValueDataModel<DO, *>>(
    objClass: KClass<DO>,
): ObjectDataModel<DO, P, IsPropertyContext, IsPropertyContext>(), IsValueDataModel<DO, P> {
    @Suppress("LeakingThis", "UNCHECKED_CAST")
    override val Serializer = object: ValueDataModelSerializer<DO, P>(this as P) {}

    abstract override fun invoke(values: ObjectValues<DO, P>): DO

    fun toBytes(vararg inputs: Any) =
        Serializer.toBytes(*inputs)

    @Suppress("UNCHECKED_CAST", "LeakingThis")
    override val Model = object: ValueDataModelDefinition<DO, P>(
        name = objClass.simpleName!!,
        properties = this@ValueDataModel as P,
    ) {}
}
