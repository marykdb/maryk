package maryk.core.values

data class ValueItem(
    val index: Int,
    val value: Any
) {
    override fun toString() = "$index=$value"
}
