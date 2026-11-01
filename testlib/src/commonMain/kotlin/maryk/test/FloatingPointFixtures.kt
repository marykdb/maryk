package maryk.test

/** JavaScript engines may canonicalize NaNs when a Number is boxed or returned. */
expect val supportsRawNaNPayloads: Boolean

/** Exercise signed zeros, canonical NaN and infinities everywhere, plus representable raw NaN payloads. */
val distinctFloat64Fixtures: List<List<Double>>
    get() = listOf(
        listOf(-0.0, 0.0, 1.5),
        listOf(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY),
    ) + if (supportsRawNaNPayloads) {
        listOf(listOf(0x7ff8000000000001L, 0x7ff8000000000002L, 0x7ff8000000000003L).map(Double::fromBits))
    } else emptyList()

val distinctFloat32Fixtures: List<List<Float>>
    get() = listOf(
        listOf(-0.0f, 0.0f, 1.5f),
        listOf(Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY),
    ) + if (supportsRawNaNPayloads) {
        listOf(listOf(0x7fc00001, 0x7fc00002, 0x7fc00003).map(Float::fromBits))
    } else emptyList()

/** A NaN whose value survives object/collection boundaries on the current target. */
val transportNaNFixture: Double
    get() = if (supportsRawNaNPayloads) Double.fromBits(0x7ff8000000000001L) else Double.NaN
