package demo

import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.core.properties.types.invoke
import maryk.core.query.changes.Change
import maryk.core.query.changes.change
import maryk.core.query.filters.Equals
import maryk.core.query.filters.GreaterThan
import maryk.core.query.filters.Prefix
import maryk.core.query.orders.Orders
import maryk.core.query.orders.ascending
import maryk.core.query.orders.descending
import maryk.core.query.pairs.with
import maryk.core.query.requests.add
import maryk.core.query.requests.change
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.requests.scanChanges
import maryk.core.query.requests.scanUpdateHistory
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.ChangeSuccess
import maryk.datastore.indexeddb.IndexedDbDataStore
import maryk.test.models.CompleteMarykModel
import maryk.test.models.MarykEnumEmbedded.E1
import maryk.test.models.MarykTypeEnum.T2
import maryk.test.models.Person
import maryk.test.models.SimpleMarykModel
import maryk.test.models.SimpleMarykTypeEnum.S1
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLOListElement
import org.w3c.dom.HTMLElement
import kotlin.math.ceil

private const val BULK_PERSON_COUNT = 10_000
private const val INITIAL_PERSON_COUNT = 3
private const val PERSON_SCAN_LIMIT = BULK_PERSON_COUNT + INITIAL_PERSON_COUNT
private const val BULK_BATCH_SIZE = 500

private val demoModels: Map<UInt, IsRootDataModel> = mapOf(
    2u to SimpleMarykModel,
    5u to CompleteMarykModel,
    8u to Person,
)

private val scope = MainScope()

fun main() {
    val button = document.getElementById("run") as HTMLButtonElement
    button.onclick = {
        scope.launch {
            button.disabled = true
            setStatus("Running")
            clearLog()
            try {
                runDemo()
                setStatus("PASS")
                button.setAttribute("data-state", "pass")
            } catch (cause: Throwable) {
                log("FAIL: ${cause.message ?: cause::class.simpleName}", failed = true)
                setStatus("FAIL")
                button.setAttribute("data-state", "fail")
                throw cause
            } finally {
                button.disabled = false
            }
        }
    }
}

private suspend fun runDemo() {
    val databaseName = "maryk-indexeddb-demo-${Date.now().toLong()}"
    log("Opening $databaseName with history and update-history indexes")

    val dataStore = IndexedDbDataStore.open(
        databaseName = databaseName,
        dataModelsById = demoModels,
        keepAllVersions = true,
        keepUpdateHistoryIndex = true,
    )

    val key: Key<CompleteMarykModel>
    val addVersion: ULong
    val changeVersion: ULong

    try {
        val values = CompleteMarykModel.create {
            string with "haas"
            number with 24u
            subModel with SimpleMarykModel.create {
                value with "haha"
            }
            multi with T2(22)
            booleanForKey with true
            dateForKey with LocalDate(2026, 6, 26)
            multiForKey with S1("web")
            enumEmbedded with E1
        }

        val add = dataStore.execute(CompleteMarykModel.add(values))
        val addStatus = add.statuses.single() as? AddSuccess<*>
            ?: error("add failed with ${add.statuses.single()}")
        @Suppress("UNCHECKED_CAST")
        key = addStatus.key as Key<CompleteMarykModel>
        addVersion = addStatus.version
        log("Added CompleteMarykModel key=${key.bytes.toHex()} version=$addVersion")

        val get = dataStore.execute(CompleteMarykModel.get(key))
        requireDemo(get.values.single().values == values) { "get after add did not return original values" }
        log("Direct get returned the added object")

        val change = dataStore.execute(
            CompleteMarykModel.change(
                key.change(Change(CompleteMarykModel.string.ref() with "haas2"))
            )
        )
        val changeStatus = change.statuses.single() as? ChangeSuccess<*>
            ?: error("change failed with ${change.statuses.single()}")
        changeVersion = changeStatus.version
        log("Changed unique string at version=$changeVersion")

        val historic = dataStore.execute(CompleteMarykModel.get(key, toVersion = addVersion))
        requireDemo(historic.values.single().values == values) { "historic get did not return add-version values" }
        log("Historic get(toVersion=addVersion) returned the original value")

        val currentUniqueScan = dataStore.execute(
            CompleteMarykModel.scan(
                where = Equals(CompleteMarykModel.string.ref() with "haas2"),
                limit = 5u,
            )
        )
        requireDemo(currentUniqueScan.values.size == 1) { "unique scan should find changed value" }
        log("Unique/index-backed scan found current changed value")

        val changeScan = dataStore.execute(
            CompleteMarykModel.scanChanges(
                fromVersion = addVersion,
                toVersion = changeVersion,
                maxVersions = 2u,
                filterSoftDeleted = false,
            )
        )
        requireDemo(changeScan.changes.isNotEmpty()) { "scanChanges returned no changes" }
        log("scanChanges returned ${changeScan.changes.size} changed object(s)")

        val history = dataStore.execute(
            CompleteMarykModel.scanUpdateHistory(
                fromVersion = addVersion,
                toVersion = changeVersion,
                limit = 10u,
                filterSoftDeleted = false,
            )
        )
        requireDemo(history.updates.size >= 2) { "update history should include add and change" }
        log("scanUpdateHistory returned ${history.updates.size} version-ordered update(s)")

        seedAndScanPeople(dataStore)
    } finally {
        dataStore.close()
    }

    val reopened = IndexedDbDataStore.open(
        databaseName = databaseName,
        dataModelsById = demoModels,
        keepAllVersions = true,
        keepUpdateHistoryIndex = true,
    )

    try {
        val afterReopen = reopened.execute(CompleteMarykModel.get(key))
        requireDemo(afterReopen.values.size == 1) { "reopen did not find persisted object" }
        log("Closed and reopened database; persisted row is still present")
    } finally {
        reopened.close()
    }
}

private suspend fun seedAndScanPeople(dataStore: IndexedDbDataStore) {
    val persons = arrayOf(
        Person.create {
            firstName with "Jurriaan"
            surname with "Mous"
        },
        Person.create {
            firstName with "Karel"
            surname with "Kastens"
        },
        Person.create {
            firstName with "Ariel"
            surname with "Kastens"
        },
    )

    dataStore.execute(Person.add(*persons))
    val scan = dataStore.execute(
        Person.scan(
            order = Orders(Person { surname::ref }.ascending(), Person { firstName::ref }.ascending()),
            limit = 10u,
        )
    )

    requireDemo(scan.values.size == persons.size) { "person index scan returned ${scan.values.size}" }
    log("Person index scan sorted ${scan.values.size} rows by surname + firstName")

    val seedResult = timed {
        seedBulkPeople(dataStore)
    }
    val highestBulkVersion = seedResult.value
    log("Seeded $BULK_PERSON_COUNT Person rows in ${seedResult.ms}ms")

    val tableScan = timed {
        dataStore.execute(Person.scan(limit = PERSON_SCAN_LIMIT.toUInt(), allowTableScan = true))
    }
    requireDemo(tableScan.value.values.size >= BULK_PERSON_COUNT) {
        "table scan returned ${tableScan.value.values.size}"
    }
    log("Table scan read ${tableScan.value.values.size} rows in ${tableScan.ms}ms")

    val indexAsc = timed {
        dataStore.execute(
            Person.scan(
                order = personOrderAsc(),
                limit = PERSON_SCAN_LIMIT.toUInt(),
            )
        )
    }
    requireDemo(indexAsc.value.values.size >= BULK_PERSON_COUNT) {
        "ascending index scan returned ${indexAsc.value.values.size}"
    }
    requireDemo(indexAsc.value.values.first().values[Person.surname.ref()] == "Family-000") {
        "ascending index scan did not start at Family-000"
    }
    log("Ascending compound index scan read ${indexAsc.value.values.size} rows in ${indexAsc.ms}ms")

    val indexDesc = timed {
        dataStore.execute(
            Person.scan(
                order = personOrderDesc(),
                limit = 25u,
            )
        )
    }
    requireDemo(indexDesc.value.values.size == 25) {
        "descending index scan returned ${indexDesc.value.values.size}"
    }
    requireDemo(indexDesc.value.values.first().values[Person.surname.ref()] == "Mous") {
        "descending index scan did not include existing highest surname first"
    }
    log("Descending compound index scan read 25 rows in ${indexDesc.ms}ms")

    val equalsScan = timed {
        dataStore.execute(
            Person.scan(
                where = Equals(Person.surname.ref() with "Family-042"),
                order = personOrderAsc(),
                limit = 150u,
            )
        )
    }
    requireDemo(equalsScan.value.values.size == 100) {
        "equals index scan returned ${equalsScan.value.values.size}"
    }
    log("Equals filter on indexed surname returned 100 rows in ${equalsScan.ms}ms")

    val prefixScan = timed {
        dataStore.execute(
            Person.scan(
                where = Prefix(Person.firstName.ref() with "Person-09"),
                limit = PERSON_SCAN_LIMIT.toUInt(),
                allowTableScan = true,
            )
        )
    }
    requireDemo(prefixScan.value.values.size == 1_000) {
        "prefix table scan returned ${prefixScan.value.values.size}"
    }
    log("Prefix table scan over firstName returned 1000 rows in ${prefixScan.ms}ms")

    val greaterThanScan = timed {
        dataStore.execute(
            Person.scan(
                where = GreaterThan(Person.surname.ref() with "Family-095"),
                order = personOrderAsc(),
                limit = 600u,
            )
        )
    }
    requireDemo(greaterThanScan.value.values.size >= 400) {
        "greater-than index scan returned ${greaterThanScan.value.values.size}"
    }
    log("Greater-than indexed scan returned ${greaterThanScan.value.values.size} rows in ${greaterThanScan.ms}ms")

    val firstPage = timed {
        dataStore.execute(Person.scan(limit = 100u, allowTableScan = true))
    }
    val secondPage = timed {
        dataStore.execute(
            Person.scan(
                startKey = firstPage.value.values.last().key,
                includeStart = false,
                limit = 100u,
                allowTableScan = true,
            )
        )
    }
    requireDemo(firstPage.value.values.last().key != secondPage.value.values.first().key) {
        "pagination repeated the last key"
    }
    log("Start-key pagination read page2=${secondPage.value.values.size} rows in ${secondPage.ms}ms")

    val scanChanges = timed {
        dataStore.execute(
            Person.scanChanges(
                fromVersion = 0uL,
                toVersion = highestBulkVersion,
                limit = 1_000u,
                filterSoftDeleted = false,
            )
        )
    }
    requireDemo(scanChanges.value.changes.size == 1_000) {
        "index-ordered scanChanges returned ${scanChanges.value.changes.size}"
    }
    log("Key-ordered scanChanges read ${scanChanges.value.changes.size} rows in ${scanChanges.ms}ms")

    val updateHistory = timed {
        dataStore.execute(
            Person.scanUpdateHistory(
                fromVersion = 0uL,
                toVersion = highestBulkVersion,
                limit = 1_000u,
                filterSoftDeleted = false,
            )
        )
    }
    requireDemo(updateHistory.value.updates.size == 1_000) {
        "scanUpdateHistory returned ${updateHistory.value.updates.size}"
    }
    log("Update-history scan read ${updateHistory.value.updates.size} rows in ${updateHistory.ms}ms")
}

private suspend fun seedBulkPeople(dataStore: IndexedDbDataStore): ULong {
    var highestVersion = 0uL
    val batchCount = ceil(BULK_PERSON_COUNT.toDouble() / BULK_BATCH_SIZE).toInt()

    for (batchIndex in 0 until batchCount) {
        val start = batchIndex * BULK_BATCH_SIZE
        val end = minOf(start + BULK_BATCH_SIZE, BULK_PERSON_COUNT)
        val values = Array(end - start) { offset ->
            val index = start + offset
            Person.create {
                firstName with "Person-${index.toString().padStart(5, '0')}"
                surname with "Family-${(index / 100).toString().padStart(3, '0')}"
            }
        }
        val add = dataStore.execute(Person.add(*values))
        for (status in add.statuses) {
            val success = status as? AddSuccess<*>
                ?: error("bulk add failed with $status")
            if (success.version > highestVersion) {
                highestVersion = success.version
            }
        }
        if ((batchIndex + 1) % 5 == 0) {
            log("Seed progress: ${end}/$BULK_PERSON_COUNT")
        }
    }

    return highestVersion
}

private fun personOrderAsc() =
    Orders(Person { surname::ref }.ascending(), Person { firstName::ref }.ascending())

private fun personOrderDesc() =
    Orders(Person { surname::ref }.descending(), Person { firstName::ref }.descending())

private fun clearLog() {
    (document.getElementById("log") as HTMLOListElement).innerHTML = ""
}

private fun setStatus(value: String) {
    (document.getElementById("status") as HTMLElement).textContent = value
}

private fun log(message: String, failed: Boolean = false) {
    val item = document.createElement("li") as HTMLElement
    item.textContent = message
    if (failed) {
        item.classList.add("fail")
    }
    document.getElementById("log")!!.appendChild(item)
}

private fun requireDemo(value: Boolean, lazyMessage: () -> String) {
    if (!value) {
        error(lazyMessage())
    }
}

private data class TimedResult<T>(
    val value: T,
    val ms: Long,
)

private suspend fun <T> timed(block: suspend () -> T): TimedResult<T> {
    val start = Date.now()
    val value = block()
    return TimedResult(value, (Date.now() - start).toLong())
}

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }

private external object Date {
    fun now(): Double
}
