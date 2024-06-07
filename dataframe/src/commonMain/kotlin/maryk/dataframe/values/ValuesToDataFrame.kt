package maryk.dataframe.values

import maryk.core.values.Values
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.columnOf
import org.jetbrains.kotlinx.dataframe.api.concat
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.emptyDataFrame

fun Values<*>.toDataFrame(): AnyFrame {
    val headers = mutableMapOf<UInt, String>()
    val values = mutableMapOf<UInt, Any?>()

    this.forEach { (index, value) ->
        headers[index] = this.dataModel[index]?.name ?: "Unknown"
        values[index] = value.let(::convertValueToDataFrame)
    }

    return dataFrameOf(headers.values)(values.values.map {
        if (it is DataFrame<*>) {
            columnOf(it.columns())
        } else columnOf(it)
    })
}

fun List<Values<*>>.toDataFrame(): AnyFrame {
    if (this.isEmpty()) return emptyDataFrame<Any>()

    val dataModel = this.first().dataModel
    val dfValues = mutableMapOf<UInt, MutableList<Any?>>()

    this.forEachIndexed { i, values ->
        values.forEach { (index, value) ->
            dfValues.getOrPut(index) { MutableList(this.size) { null } }[i] = convertValueToDataFrame(value)
        }
    }

    val headers = dfValues.keys.map { dataModel[it]?.name ?: "" }

    return dataFrameOf(headers)(dfValues.values.map { value ->
        if (value.isNotEmpty() && value.first() is DataFrame<*>) {
            @Suppress("UNCHECKED_CAST")
            columnOf((value as List<DataFrame<*>>).concat().columns())
        } else {
            columnOf(*value.toTypedArray())
        }
    })
}
