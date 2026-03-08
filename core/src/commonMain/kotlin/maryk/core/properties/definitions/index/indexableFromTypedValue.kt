package maryk.core.properties.definitions.index

import maryk.core.properties.references.IsIndexablePropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.values.ObjectValues

@Suppress("UNCHECKED_CAST")
internal fun TypedValue<IndexKeyPartType<*>, *>.toIndexable(): IsIndexable = when (val typedValue = this.value) {
    is IsIndexable -> typedValue
    is ObjectValues<*, *> -> when (this.type) {
        IndexKeyPartType.Multiple -> Multiple.Model.invoke(typedValue as ObjectValues<Multiple, Multiple.Model>)
        IndexKeyPartType.Normalize -> Normalize.Model.invoke(typedValue as ObjectValues<Normalize, Normalize.Model>)
        IndexKeyPartType.Split -> Split.Model.invoke(typedValue as ObjectValues<Split, Split.Model>)
        IndexKeyPartType.AnyOf -> AnyOf.Model.invoke(typedValue as ObjectValues<AnyOf, AnyOf.Model>)
        IndexKeyPartType.ReferenceToMax -> ReferenceToMax.Model.invoke(
            typedValue as ObjectValues<ReferenceToMax<out Any>, ReferenceToMax.Model>
        )
        else -> throw IllegalStateException("Unexpected embedded values for index type ${this.type}")
    }
    else -> throw IllegalStateException("Unexpected indexable value ${typedValue::class}")
}

@Suppress("UNCHECKED_CAST")
internal fun TypedValue<IndexKeyPartType<*>, *>.toStringIndexablePropertyReference() =
    this.toIndexable() as IsIndexablePropertyReference<String>
