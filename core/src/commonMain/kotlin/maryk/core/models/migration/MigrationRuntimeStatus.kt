package maryk.core.models.migration

enum class MigrationRuntimeState {
    Idle,
    Running,
    Paused,
    Canceled,
    Failed,
}

data class MigrationRuntimeStatus(
    val state: MigrationRuntimeState,
    val message: String? = null,
)
