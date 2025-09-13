package maryk.core.values

import maryk.core.models.IsValuesDataModel
import maryk.core.models.TypedValuesDataModel
import maryk.core.models.validate
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsMultiTypeDefinition
import maryk.core.properties.definitions.wrapper.MultiTypeDefinitionWrapper
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.graph.IsPropRefGraph
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.query.RequestContext
import maryk.core.query.changes.Change
import maryk.core.query.changes.IsChange
import maryk.core.query.pairs.IsReferenceValueOrNullPair
import maryk.core.query.pairs.ReferenceValuePair

typealias ValuesImpl = Values<IsValuesDataModel>

/**
 * Contains a [map] with all values related to a DataObject of [dataModel]
 */
data class Values<DM : IsValuesDataModel> internal constructor(
    override val dataModel: DM,
    override val values: IsValueItems,
    override val context: RequestContext? = null
) : AbstractValues<Any, DM>() {
    /** make a copy of Values and add new pairs from [pairCreator] */
    fun copy(pairCreator: DM.() -> List<ValueItem>) =
        Values(dataModel, values.copyAdding(pairCreator(dataModel)), context)

    fun copy(values: IsValueItems) =
        Values(dataModel, values.copyAdding(values), context)

    fun filterWithSelect(select: IsPropRefGraph<*>?): Values<DM> {
        if (select == null) {
            return this
        }

        return Values(
            dataModel = dataModel,
            values = this.values.copySelecting(select),
            context = context,
        )
    }

    /** Change the Values with given [change] */
    fun change(vararg change: IsChange) = change(change.toList())

    fun change(changes: List<IsChange>): Values<DM> =
        if (changes.isEmpty()) this
        else {
            val valueItemsToChange = MutableValueItems(mutableListOf())
            changes.forEach { change ->
                change.changeValues { ref, valueChanger ->
                    valueItemsToChange.copyFromOriginalAndChange(values, ref.index, valueChanger)
                }
            }
            Values(dataModel, values.copyAdding(valueItemsToChange), context)
        }

    /** Convert these values to a list of [IsChange]s */
    fun toChanges(): Array<IsChange> {
        if (values.size == 0) return emptyArray()
        val referenceValuePairs = mutableListOf<IsReferenceValueOrNullPair<Any>>()

        fun addPairs(
            itemsDataModel: IsValuesDataModel,
            valueItems: IsValueItems,
            parentRef: IsPropertyReference<*, *, *>?
        ) {
            for (valueItem in valueItems) {
                val def = itemsDataModel[valueItem.index] ?: continue
                val value = valueItem.value
                val definition = def.definition

                when {
                    definition is IsEmbeddedValuesDefinition<*, *> -> {
                        val ref = def.ref(parentRef)
                        addPairs(definition.dataModel, (value as Values<*>).values, ref)
                    }
                    definition is IsMultiTypeDefinition<*, *, *> -> {
                        val typedValue = value as TypedValue<TypeEnum<Any>, Any>
                        @Suppress("UNCHECKED_CAST")
                        val multiDef = def as MultiTypeDefinitionWrapper<TypeEnum<Any>, Any, *, *, *>
                        val type = typedValue.type
                        val typeRef = multiDef.refAtType(type)(parentRef)

                        val subDef = multiDef.definition(type)
                        if (subDef is IsEmbeddedValuesDefinition<*, *>) {
                            addPairs(subDef.dataModel, (typedValue.value as Values<*>).values, typeRef)
                        } else {
                            @Suppress("UNCHECKED_CAST")
                            referenceValuePairs += ReferenceValuePair(
                                typeRef as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>,
                                typedValue.value
                            )
                        }
                    }
                    else -> {
                        val ref = def.ref(parentRef)
                        @Suppress("UNCHECKED_CAST")
                        referenceValuePairs += ReferenceValuePair(
                            ref as IsPropertyReference<Any, IsChangeableValueDefinition<Any, IsPropertyContext>, *>,
                            value
                        )
                    }
                }
            }
        }

        addPairs(dataModel, values, null)

        return arrayOf(Change(referenceValuePairs))
    }

    // ignore context
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is Values<*> -> false
        dataModel != other.dataModel -> false
        values != other.values -> false
        else -> true
    }

    // ignore context
    override fun hashCode() = 31 * dataModel.Meta.hashCode() + values.hashCode()

    override fun toString() = "Values<${dataModel.Meta.name}>${values.toString(dataModel)}"

    /**
     * Validates the contents of values
     */
    fun validate() = dataModel.validate(this)
}

/** Output values to a json string */
fun <V: Values<DM>, DM: TypedValuesDataModel<DM>> V.toJson(pretty: Boolean = false) =
    dataModel.Serializer.writeJson(this, pretty = pretty)
