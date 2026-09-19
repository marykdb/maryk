package io.maryk.cli

import maryk.core.models.IsRootDataModel
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.ServerFail

data class DeleteResult(
    val lines: List<String>,
    val isError: Boolean,
)

internal fun <DM : IsRootDataModel> formatDeleteResult(
    response: DeleteResponse<DM>,
    label: String,
    hardDelete: Boolean,
): DeleteResult {
    val status = response.statuses.firstOrNull()
        ?: return DeleteResult(
            lines = listOf("Delete failed: no response status for $label."),
            isError = true,
        )
    return when (status) {
        is DeleteSuccess -> DeleteResult(
            lines = listOf(if (hardDelete) "Hard deleted $label." else "Deleted $label."),
            isError = false,
        )
        is DoesNotExist -> DeleteResult(
            lines = listOf("Delete failed: $label does not exist."),
            isError = true,
        )
        is ServerFail -> DeleteResult(
            lines = listOf("Delete failed: ${status.reason}"),
            isError = true,
        )
        else -> DeleteResult(
            lines = listOf("Delete failed: ${status.statusType}"),
            isError = true,
        )
    }
}
