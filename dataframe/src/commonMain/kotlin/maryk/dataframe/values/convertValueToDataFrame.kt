package maryk.dataframe.values

import maryk.core.values.Values

fun convertValueToDataFrame(value: Any?): Any? = when (value) {
    is Values<*> -> value.toDataFrame()
    is List<*> -> value.map(::convertValueToDataFrame)
    else -> value
}
