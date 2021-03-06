package maryk.core.query.responses.updates

import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.wrapper.ObjectListDefinitionWrapper
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.responses.updates.UpdateResponseType.InitialValues
import maryk.core.values.SimpleObjectValues

/** Update containing the initial values for listeners which listen to a scan. */
data class InitialValuesUpdate<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    override val version: ULong,
    val values: List<ValuesWithMetaData<DM, P>>
): IsUpdateResponse<DM, P> {
    override val type = InitialValues

    @Suppress("unused")
    internal object Properties : ObjectPropertyDefinitions<InitialValuesUpdate<*, *>>() {
        val version by number(1u, getter = InitialValuesUpdate<*, *>::version, type = UInt64)
        val values = ObjectListDefinitionWrapper(
            2u, "values",
            properties = ValuesWithMetaData.Properties,
            definition = ListDefinition(
                valueDefinition = EmbeddedObjectDefinition(
                    dataModel = { ValuesWithMetaData }
                )
            ),
            getter = InitialValuesUpdate<*, *>::values
        ).also(::addSingle)
    }

    companion object : SimpleQueryDataModel<InitialValuesUpdate<*, *>>(
        properties = Properties
    ) {
        override fun invoke(values: SimpleObjectValues<InitialValuesUpdate<*, *>>) = InitialValuesUpdate<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(
            version = values(1u),
            values = values(2u)
        )
    }
}
