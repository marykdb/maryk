package maryk.datastore.foundationdb

import maryk.foundationdb.Database
import maryk.foundationdb.FDB

internal const val FOUNDATION_DB_API_VERSION = 730

internal val foundationDbApi: FDB by lazy {
    FDB.selectAPIVersion(FOUNDATION_DB_API_VERSION)
}

internal fun openFoundationDatabase(fdbClusterFilePath: String?): Database =
    if (fdbClusterFilePath != null) {
        foundationDbApi.open(fdbClusterFilePath)
    } else {
        foundationDbApi.open()
    }
