package maryk.core.query.responses.updates

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.wrapper.ObjectListDefinitionWrapper
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.responses.updates.UpdateResponseType.InitialValues
import maryk.core.values.SimpleObjectValues

/** Update containing the initial values for listeners which listen to a scan. */
data class InitialValuesUpdate<DM: IsRootDataModel>(
    override val version: ULong,
    val values: List<ValuesWithMetaData<DM>>
): IsUpdateResponse<DM> {
    override val type = InitialValues

    companion object : SimpleQueryModel<InitialValuesUpdate<*>>() {
        val version by number(1u, getter = InitialValuesUpdate<*>::version, type = UInt64)
        val values = ObjectListDefinitionWrapper(
            2u, "values",
            properties = ValuesWithMetaData.Companion,
            definition = ListDefinition(
                valueDefinition = EmbeddedObjectDefinition(
                    dataModel = { ValuesWithMetaData }
                )
            ),
            getter = InitialValuesUpdate<*>::values
        ).also(::addSingle)

        override fun invoke(values: SimpleObjectValues<InitialValuesUpdate<*>>) = InitialValuesUpdate<IsRootDataModel>(
            version = values(1u),
            values = values(2u)
        )
    }
}
