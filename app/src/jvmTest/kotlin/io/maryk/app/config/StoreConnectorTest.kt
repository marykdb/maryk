package io.maryk.app.config

import kotlinx.coroutines.runBlocking
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.UUIDv7Key
import maryk.core.properties.definitions.multiType
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.test.models.SimpleMarykTypeEnum
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertIs

class StoreConnectorTest {
    @Test
    fun opensRocksDbUsingItsRecoveredMultiTypeKeyModel() = runBlocking {
        val dbFolder = Files.createTempDirectory("app-store-connector-custom-key")
        val dbPath = dbFolder.toString()
        try {
            RocksDBDataStore.open(
                relativePath = dbPath,
                dataModelsById = mapOf(1u to BrowserRecoveredUuidV7Model),
            ).close()

            val result = StoreConnector().connect(
                StoreDefinition(
                    id = "test",
                    name = "Custom key store",
                    type = StoreKind.ROCKS_DB,
                    directory = dbPath,
                )
            )

            assertIs<ConnectResult.Success>(result).connection.close()
        } finally {
            dbFolder.toFile().deleteRecursively()
        }
    }
}

private object BrowserRecoveredUuidV7Model : RootDataModel<BrowserRecoveredUuidV7Model>(
    keyDefinition = { Multiple(BrowserRecoveredUuidV7Model.data.typeRef(), UUIDv7Key) },
) {
    val data by multiType(index = 1u, typeEnum = SimpleMarykTypeEnum)
}
