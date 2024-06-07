package maryk.dataframe.values

import maryk.core.values.Values
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.columnOf
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
        columnOf(it)
    })
}

fun List<Values<*>>.toDataFrame(): AnyFrame {
    if (this.isEmpty()) return emptyDataFrame<Any>()

    val dataModel = this.first().dataModel
    val dfValues = mutableMapOf<UInt, MutableList<Any?>>()

    this.forEach { values ->
        values.forEach { (index, value) ->
            dfValues.getOrPut(index, ::mutableListOf).add(convertValueToDataFrame(value))
        }
    }

    val headers = dfValues.keys.map { dataModel[it]?.name ?: "" }

    return dataFrameOf(headers)(dfValues.values.map { columnOf(it) })
}
