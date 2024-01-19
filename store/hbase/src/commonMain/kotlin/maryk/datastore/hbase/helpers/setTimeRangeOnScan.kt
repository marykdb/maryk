package maryk.datastore.hbase.helpers

import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.IsChangesRequest
import maryk.core.query.requests.IsFetchRequest
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.Scan

fun <DM : IsRootDataModel> Get.setTimeRange(scanRequest: IsFetchRequest<DM, *>) {
    if (scanRequest is IsChangesRequest && scanRequest.fromVersion != 0uL) {
        setTimeRange(
            scanRequest.fromVersion.toLong(),
            scanRequest.toVersion?.toLong()?.let { it + 1 } ?: Long.MAX_VALUE
        )
    } else if (scanRequest.toVersion != null) {
        setTimeRange(0, scanRequest.toVersion!!.toLong() + 1)
    }
}

fun <DM : IsRootDataModel> Scan.setTimeRange(scanRequest: IsFetchRequest<DM, *>) {
    if (scanRequest is IsChangesRequest && scanRequest.fromVersion != 0uL) {
        setTimeRange(
            scanRequest.fromVersion.toLong(),
            scanRequest.toVersion?.toLong()?.let { it + 1 } ?: Long.MAX_VALUE
        )
    } else if (scanRequest.toVersion != null) {
        setTimeRange(0, scanRequest.toVersion!!.toLong() + 1)
    }
}
