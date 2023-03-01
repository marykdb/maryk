package maryk.core.properties

import maryk.core.models.ObjectDataModel
import maryk.core.properties.definitions.HasDefaultValueDefinition
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.core.values.ValueItem
import kotlin.reflect.KClass

abstract class ObjectModel<DO: Any, P: ObjectPropertyDefinitions<DO>>(
    objClass: KClass<DO>,
): InternalModel<DO, P, IsPropertyContext, IsPropertyContext>(), IsInternalModel<DO, P> {
    fun create(
        vararg pairs: ValueItem?,
        setDefaults: Boolean = true,
    ) = Model.values {
        MutableValueItems().also { items ->
            for (it in pairs) {
                if (it != null) items += it
            }
            if (setDefaults) {
                for (definition in this.allWithDefaults) {
                    val innerDef = definition.definition
                    if (items[definition.index] == null) {
                        items[definition.index] = (innerDef as HasDefaultValueDefinition<*>).default!!
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override val Model = object: ObjectDataModel<DO, P>(
        objClass.simpleName!!,
        this@ObjectModel as P,
    ) {
        override fun invoke(values: ObjectValues<DO, P>): DO =
            this@ObjectModel.invoke(values)
    }
}
