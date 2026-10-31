package maryk.test.models

import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.EmbeddedValuesDefinition
import maryk.core.properties.definitions.GeoPointDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.ValueObjectDefinition
import maryk.core.properties.definitions.embed
import maryk.core.properties.definitions.incrementingMap
import maryk.core.properties.definitions.map
import maryk.core.properties.definitions.multiType
import maryk.core.properties.definitions.string
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.properties.types.numeric.UInt32
import maryk.core.values.Values

object SelectionIntegrityLeaf : DataModel<SelectionIntegrityLeaf>() {
    val first by string(1u, required = false)
    val second by string(2u, required = false)
    val child by embed(3u, required = false, dataModel = { SelectionIntegrityLeaf })
    val children by map(4u, required = false, keyDefinition = StringDefinition(),
        valueDefinition = EmbeddedValuesDefinition(dataModel = { SelectionIntegrityLeaf }))
}

enum class SelectionIntegrityCase(override val index: UInt) :
    IndexedEnumComparable<SelectionIntegrityCase>, MultiTypeEnum<Values<SelectionIntegrityLeaf>> {
    A(1u), B(2u);
    override val alternativeNames: Set<String>? = null
    override val definition = EmbeddedValuesDefinition(dataModel = { SelectionIntegrityLeaf })
    companion object : MultiTypeEnumDefinition<SelectionIntegrityCase>(SelectionIntegrityCase::class, { entries })
}

object SelectionIntegrityModel : RootDataModel<SelectionIntegrityModel>() {
    val records by map(1u, required = false, keyDefinition = StringDefinition(), valueDefinition = EmbeddedValuesDefinition(dataModel = { SelectionIntegrityLeaf }))
    val points by map(2u, required = false, keyDefinition = GeoPointDefinition(), valueDefinition = StringDefinition())
    val valueKeys by map(3u, required = false, keyDefinition = ValueObjectDefinition(dataModel = TestValueObject), valueDefinition = StringDefinition())
    val details by embed(4u, required = false, dataModel = { SelectionIntegrityLeaf })
    val typed by multiType(5u, required = false, typeEnum = SelectionIntegrityCase)
    val allocated by incrementingMap(6u, required = false, keyNumberDescriptor = UInt32,
        valueDefinition = EmbeddedValuesDefinition(dataModel = { SelectionIntegrityLeaf }))
}
