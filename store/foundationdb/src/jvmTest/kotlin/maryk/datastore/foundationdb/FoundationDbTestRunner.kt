package maryk.datastore.foundationdb

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.add
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AlreadyExists
import maryk.core.values.Values
import maryk.test.models.Log
import maryk.test.models.Person
import maryk.test.models.Severity
import maryk.test.models.SimpleMarykModel
import java.io.File
import java.nio.file.Paths
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

private const val RECORDS_PER_TABLE = 30

fun main() = runBlocking {
    val clusterFile = resolveClusterFile()

    val dataStore = FoundationDBDataStore.open(
        fdbClusterFilePath = clusterFile,
        directoryPath = listOf("maryk", "test", "runner"),
        dataModelsById = mapOf(
            1u to Person,
            2u to SimpleMarykModel,
            3u to Log
        ),
        keepAllVersions = false
    )

    seedPeople(dataStore)
    seedSimpleValues(dataStore)
    seedLogs(dataStore)

    println("Seeded $RECORDS_PER_TABLE records per table into FoundationDB at $clusterFile")
    println("Runner will stay alive for manual testing. Press Ctrl+C to stop.")

    keepRunning()
}

private suspend fun seedPeople(dataStore: FoundationDBDataStore) {
    val people = (1..RECORDS_PER_TABLE).map { index ->
        Person.create {
            firstName with "First$index"
            surname with "Last$index"
        }
    }
    addAll(dataStore, Person, people)
}

private suspend fun seedSimpleValues(dataStore: FoundationDBDataStore) {
    val simples = (1..RECORDS_PER_TABLE).map { index ->
        SimpleMarykModel.create {
            value with "ha-$index"
        }
    }
    addAll(dataStore, SimpleMarykModel, simples)
}

@OptIn(ExperimentalTime::class)
private suspend fun seedLogs(dataStore: FoundationDBDataStore) {
    val start = Clock.System.now()
    val logs = (1..RECORDS_PER_TABLE).map { index ->
        val severity = when {
            index % 5 == 0 -> Severity.ERROR
            index % 2 == 0 -> Severity.DEBUG
            else -> Severity.INFO
        }
        val timestamp = start.plus(index.minutes).toLocalDateTime(TimeZone.UTC)
        Log("Seed log $index", severity = severity, timestamp = timestamp)
    }
    addAll(dataStore, Log, logs)
}

private fun resolveClusterFile(): String {
    val candidates = listOfNotNull(
        System.getenv("FDB_CLUSTER_FILE")?.takeIf { it.isNotBlank() },
        "fdb.cluster",
        "store/foundationdb/fdb.cluster"
    )

    return candidates
        .map { Paths.get(it).toAbsolutePath().normalize() }
        .firstOrNull { path -> File(path.toString()).exists() }
        ?.toString()
        ?: Paths.get(candidates.first()).toAbsolutePath().normalize().toString()
}

private suspend fun <DM : IsRootDataModel> addAll(
    dataStore: FoundationDBDataStore,
    dataModel: DM,
    values: List<Values<DM>>
) {
    val response = dataStore.execute(dataModel.add(*values.toTypedArray()))
    val added = response.statuses.count { it is AddSuccess<*> }
    val skipped = response.statuses.count { it is AlreadyExists<*> }
    println("Seeded $added ${dataModel.Meta.name} rows (skipped $skipped existing).")
}

private suspend fun keepRunning() {
    while (true) {
        delay(60_000)
    }
}
