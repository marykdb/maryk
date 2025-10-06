package maryk.datastore.terminal.driver

import maryk.datastore.terminal.StoreConnectionConfig

/**
 * Create a [StoreDriver] for the provided [config].
 */
fun createStoreDriver(config: StoreConnectionConfig): StoreDriver = when (config) {
    is StoreConnectionConfig.RocksDb -> RocksDbStoreDriver(config)
    is StoreConnectionConfig.FoundationDb -> FoundationDbStoreDriver(config)
}
