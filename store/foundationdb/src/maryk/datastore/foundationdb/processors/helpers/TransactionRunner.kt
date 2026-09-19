package maryk.datastore.foundationdb.processors.helpers

import maryk.foundationdb.Transaction

internal interface TransactionRunner {
    fun <T> run(block: (Transaction) -> T): T
}
