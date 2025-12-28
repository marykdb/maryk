package io.maryk.cli

data class DeleteContext(
    val label: String,
    val onDelete: (hardDelete: Boolean) -> List<String>,
)
