package maryk.core.aggregations.metric

import maryk.core.properties.definitions.DecimalDefinition
import maryk.core.properties.definitions.IsArithmeticDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.types.Decimal
import maryk.core.properties.types.numeric.Float32
import maryk.core.properties.types.numeric.Float64

/** Integral/decimal sums stay exact until division or conversion to the public result type. */
internal class ArithmeticAccumulator<T : Comparable<T>>(private val definition: IsArithmeticDefinition<T>) {
    private val exact = definition is DecimalDefinition || definition is NumberDefinition<*> &&
        definition.type != Float32 && definition.type != Float64
    private var exactSum: Decimal? = null
    private var sum: T? = null
    var count = 0L
        private set

    fun add(value: T) {
        if (count == Long.MAX_VALUE) throw ArithmeticException("Aggregation count exceeds Long.MAX_VALUE")
        if (exact) {
            val decimal = if (definition is DecimalDefinition) (value as Decimal).rescaleExact(definition.scale)
                else Decimal.fromUnscaled(value.toString(), 0u)
            exactSum = exactSum?.plus(decimal) ?: decimal
        } else {
            sum = sum?.let { definition.add(it, value) } ?: value
        }
        count++
    }

    fun sum(): T? = if (exact) exactSum?.let(::result) else sum

    fun average(): T? = if (exact) exactSum?.let {
        val average = if (definition is DecimalDefinition) {
            it.divideRounded(Decimal.fromUnscaled(count, 0u), definition.scale)
        } else it.divideAndRemainder(count).first
        result(average)
    } else sum?.let { definition.average(it, count) }

    @Suppress("UNCHECKED_CAST")
    private fun result(value: Decimal): T = when (definition) {
        is NumberDefinition<*> -> try {
            definition.type.ofString(value.toString()) as T
        } catch (_: NumberFormatException) {
            throw ArithmeticException("Aggregation result exceeds ${definition.type} range")
        }
        is DecimalDefinition -> {
            if (!value.fitsInSignedBytes(definition.byteSize)) {
                throw ArithmeticException("Aggregation result exceeds Decimal storage range")
            }
            value as T
        }
        else -> error("Exact aggregation requires an integral or Decimal definition")
    }
}
