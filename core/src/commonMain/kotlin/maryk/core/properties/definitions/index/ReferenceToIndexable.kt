package maryk.core.properties.definitions.index

/** Maps [reference] as byte array to an array of [indexables] */
class ReferenceToIndexable(
    val reference: ByteArray,
    val indexables: Array<IsIndexable>
)
