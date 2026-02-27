package maryk.core.models.migration

enum class MigrationRuntimeState {
    Idle,
    Running,
    Failed,
}

data class MigrationRuntimeStatus(
    val state: MigrationRuntimeState,
    val message: String? = null,
)
