package maryk.dataframe.models

import maryk.core.models.IsTypedDataModel
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.DateDefinition
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.FixedBytesDefinition
import maryk.core.properties.definitions.FlexBytesDefinition
import maryk.core.properties.definitions.GeoPointDefinition
import maryk.core.properties.definitions.IncrementingMapDefinition
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.IsEmbeddedValuesDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.ReferenceDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.ValueObjectDefinition
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.columnOf
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.emptyDataFrame

fun IsTypedDataModel<*>.toDataFrame(): AnyFrame {
    val headers = listOf(
        "index", "name", "type", "required", "final", "unique", "options",
    )

    val indices = ArrayList<String>(this.size)
    val names = ArrayList<String>(this.size)
    val types = ArrayList<String>(this.size)
    val requireds = ArrayList<String>(this.size)
    val finals = ArrayList<String>(this.size)
    val uniques = MutableList<Boolean?>(this.size) { null }
    val options = ArrayList<DataFrame<*>>(this.size)

    this.forEachIndexed { index, propertyDef ->
        indices.add(propertyDef.index.toString())
        names.add(propertyDef.name)
        types.add(when(val def = propertyDef.definition) {
            is IsTransportablePropertyDefinitionType<*> -> def.propertyDefinitionType.name
            else -> "Unknown"
        })
        requireds.add(propertyDef.required.toString())
        finals.add(propertyDef.final.toString())

        when (val def = propertyDef.definition) {
            is IsComparableDefinition -> {
                uniques[index] = def.unique
            }
        }

        options.add(propertyDef.definition.toDataFrame())
    }

    return dataFrameOf(headers)(
        columnOf(*indices.toTypedArray()),
        columnOf(*names.toTypedArray()),
        columnOf(*types.toTypedArray()),
        columnOf(*requireds.toTypedArray()),
        columnOf(*finals.toTypedArray()),
        columnOf(*uniques.toTypedArray()),
        columnOf(*options.toTypedArray()),
    )
}

fun IsPropertyDefinition<*>.toDataFrame(): AnyFrame =
    when(this) {
        is StringDefinition -> {
            dataFrameOf(
                "regEx" to listOf(this.regEx),
                "minValue" to listOf(this.minValue),
                "maxValue" to listOf(this.maxValue),
                "minSize" to listOf(this.minSize),
                "maxSize" to listOf(this.maxSize),
                "default" to listOf(this.default),
            )
        }
        is BooleanDefinition -> {
            dataFrameOf(
                "default" to listOf(this.default),
            )
        }
        is NumberDefinition<*> -> {
            dataFrameOf(
                "type" to listOf(this.type.type.name),
                "minValue" to listOf(this.minValue),
                "maxValue" to listOf(this.maxValue),
                "default" to listOf(this.default),
            )
        }
        is EnumDefinition<*> -> {
            dataFrameOf(
                "enum" to listOf(this.enum.name),
                "default" to listOf(this.default),
                "minValue" to listOf(this.minValue),
                "maxValue" to listOf(this.maxValue),
            )
        }
        is GeoPointDefinition -> {
            dataFrameOf(
                "default" to listOf(this.default),
            )
        }
        is DateDefinition -> {
            dataFrameOf(
                "minValue" to listOf(this.minValue),
                "maxValue" to listOf(this.maxValue),
                "default" to listOf(this.default),
            )
        }
        is TimeDefinition -> {
            dataFrameOf(
                "minValue" to listOf(this.minValue),
                "maxValue" to listOf(this.maxValue),
                "default" to listOf(this.default),
            )
        }
        is DateTimeDefinition -> {
            dataFrameOf(
                "minValue" to listOf(this.minValue),
                "maxValue" to listOf(this.maxValue),
                "default" to listOf(this.default),
            )
        }
        is ReferenceDefinition<*> -> {
            dataFrameOf(
                "dataModel" to listOf(this.dataModel.Meta.name),
                "minValue" to listOf(this.minValue),
                "maxValue" to listOf(this.maxValue),
            )
        }
        is FixedBytesDefinition -> {
            dataFrameOf(
                "byteSize" to listOf(this.byteSize),
                "default" to listOf(this.default),
                "minValue" to listOf(this.minValue),
                "maxValue" to listOf(this.maxValue),
            )
        }
        is FlexBytesDefinition -> {
            dataFrameOf(
                "default" to listOf(this.default),
                "minSize" to listOf(this.minSize),
                "maxSize" to listOf(this.maxSize),
                "minValue" to listOf(this.minValue),
                "maxValue" to listOf(this.maxValue),
            )
        }
        is MultiTypeDefinition<*, *> -> {
            dataFrameOf(
                "typeEnum" to listOf(this.typeEnum.name),
                "default" to listOf(this.default),
                "typeIsFinal" to listOf(this.typeIsFinal),
            )
        }
        is ListDefinition<*, *> -> {
            dataFrameOf(
                "minSize" to listOf(this.minSize),
                "maxSize" to listOf(this.maxSize),
                "default" to listOf(this.default),
                "valueDefinition" to listOf(this.valueDefinition.toDataFrame()),
            )
        }
        is SetDefinition<*, *> -> {
            dataFrameOf(
                "minSize" to listOf(this.minSize),
                "maxSize" to listOf(this.maxSize),
                "default" to listOf(this.default),
                "valueDefinition" to listOf(this.valueDefinition.toDataFrame()),
            )
        }
        is MapDefinition<*, *, *> -> {
            dataFrameOf(
                "minSize" to listOf(this.minSize),
                "maxSize" to listOf(this.maxSize),
                "default" to listOf(this.default),
                "keyDefinition" to listOf(this.keyDefinition.toDataFrame()),
                "valueDefinition" to listOf(this.valueDefinition.toDataFrame()),
            )
        }
        is IncrementingMapDefinition<*, *, *> -> {
            dataFrameOf(
                "minSize" to listOf(this.minSize),
                "maxSize" to listOf(this.maxSize),
                "keyDefinition" to listOf(this.keyDefinition.toDataFrame()),
                "valueDefinition" to listOf(this.valueDefinition.toDataFrame()),
            )
        }
        is IsEmbeddedValuesDefinition<*, *> -> {
            dataFrameOf(
                "default" to listOf(this.default),
                "dataModel" to listOf(this.dataModel.Meta.name),
            )
        }
        is ValueObjectDefinition<*, *> -> {
            dataFrameOf(
                "default" to listOf(this.default),
                "dataModel" to listOf(this.dataModel.Meta.name),
            )
        }
        else -> emptyDataFrame<Any>()
    }
