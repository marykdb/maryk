package io.maryk.cli

import maryk.core.models.IsRootDataModel
import maryk.core.query.responses.DeleteResponse
import maryk.core.query.responses.statuses.DeleteSuccess
import maryk.core.query.responses.statuses.DoesNotExist
import maryk.core.query.responses.statuses.ServerFail

internal fun <DM : IsRootDataModel> formatDeleteResult(
    response: DeleteResponse<DM>,
    label: String,
    hardDelete: Boolean,
): List<String> {
    val status = response.statuses.firstOrNull()
        ?: return listOf("Delete failed: no response status for $label.")
    return when (status) {
        is DeleteSuccess -> listOf(if (hardDelete) "Hard deleted $label." else "Deleted $label.")
        is DoesNotExist -> listOf("Delete failed: $label does not exist.")
        is ServerFail -> listOf("Delete failed: ${status.reason}")
        else -> listOf("Delete failed: ${status.statusType}")
    }
}
