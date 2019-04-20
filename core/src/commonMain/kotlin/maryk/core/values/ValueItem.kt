package maryk.core.values

data class ValueItem(
    val index: UInt,
    val value: Any
) {
    override fun toString() = "$index=$value"
}
