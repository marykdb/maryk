@file:OptIn(ExperimentalWasmJsInterop::class)
package maryk.datastore.indexeddb

import kotlinx.coroutines.suspendCancellableCoroutine
import maryk.datastore.indexeddb.persistence.PersistedDataStore
import maryk.datastore.indexeddb.persistence.PersistedHistoricNode
import maryk.datastore.indexeddb.persistence.PersistedNode
import maryk.datastore.indexeddb.persistence.PersistedRecord
import maryk.datastore.indexeddb.persistence.PersistedValueNode
import kotlin.IllegalStateException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.definedExternally
import kotlin.js.get
import kotlin.js.length
import kotlin.js.unsafeCast

internal interface IndexedDbDriver {
    suspend fun loadStore(storeName: String): PersistedDataStore?
    suspend fun writeStore(storeName: String, store: PersistedDataStore)
    suspend fun deleteStore(storeName: String)
    fun close()
}

class IndexedDbNotSupportedException() : Throwable("IndexedDB is not supported on this platform")

internal suspend fun openIndexedDbDriver(
    name: String,
    version: Int,
    storeNames: List<String>,
    fallbackToMemoryStore: Boolean,
): IndexedDbDriver {
    val factory = findIndexedDb() ?: if(fallbackToMemoryStore) {
        return InMemoryIndexedDbDriver(storeNames)
    } else {
        throw IndexedDbNotSupportedException()
    }

    val openRequest = factory.open(name, version)
    openRequest.onupgradeneeded = fun(_: JsAny) {
        val database = runCatching { openRequest.result }.getOrNull() ?: return
        createMissingStores(database, storeNames)
    }

    val database = awaitDatabase(openRequest)
    verifyStores(database, storeNames)

    return JsIndexedDbDriver(database)
}

private class JsIndexedDbDriver(
    private val database: IDBDatabase,
) : IndexedDbDriver {
    override suspend fun loadStore(storeName: String): PersistedDataStore? {
        val transaction = database.transaction(storeName, MODE_READONLY)
        val store = transaction.objectStore(storeName)
        val request = store.getAll()
        val result = awaitResult(request)
        awaitCompletion(transaction)

        if (isNullOrUndefined(result)) {
            return null
        }

        val recordsArray = result!!.unsafeCast<JsArray<JsAny>>()
        val size = recordsArray.length
        if (size == 0) {
            return null
        }

        val records = mutableListOf<PersistedRecord>()
        for (index in 0 until size) {
            val recordAny = recordsArray[index] ?: continue
            records += toPersistedRecord(recordAny.unsafeCast<JsAny>())
        }
        records.sortBy { it.key }

        return PersistedDataStore(records)
    }

    override suspend fun writeStore(storeName: String, store: PersistedDataStore) {
        val transaction = database.transaction(storeName, MODE_READWRITE)
        val objectStore = transaction.objectStore(storeName)

        val clearRequest = objectStore.clear()
        awaitResult(clearRequest)

        for (record in store.records) {
            val putRequest = objectStore.put(record.toJsObject().unsafeCast<JsAny>(), record.key)
            awaitResult(putRequest)
        }

        awaitCompletion(transaction)
    }

    override suspend fun deleteStore(storeName: String) {
        val transaction = database.transaction(storeName, MODE_READWRITE)
        val objectStore = transaction.objectStore(storeName)
        val clearRequest = objectStore.clear()
        awaitResult(clearRequest)
        awaitCompletion(transaction)
    }

    override fun close() {
        database.close()
    }
}

private class InMemoryIndexedDbDriver(
    storeNames: List<String>,
) : IndexedDbDriver {
    init {
        println("Using in memory IndexedDB")
    }

    private val data = storeNames.associateWith { mutableMapOf<String, PersistedRecord>() }.toMutableMap()

    override suspend fun loadStore(storeName: String): PersistedDataStore? {
        val records = data[storeName]?.values ?: return null
        if (records.isEmpty()) {
            return null
        }
        return PersistedDataStore(records.sortedBy { it.key })
    }

    override suspend fun writeStore(storeName: String, store: PersistedDataStore) {
        val storeData = data.getOrPut(storeName) { mutableMapOf() }
        storeData.clear()
        for (record in store.records) {
            storeData[record.key] = record
        }
    }

    override suspend fun deleteStore(storeName: String) {
        data[storeName]?.clear()
    }

    override fun close() {
        data.clear()
    }
}

private suspend fun awaitDatabase(request: IDBOpenDBRequest): IDBDatabase = suspendCancellableCoroutine { cont ->
    request.onsuccess = { cont.resume(request.result) }
    request.onerror = {
        val message = request.error.messageOrFallback("IndexedDB open request failed")
        cont.resumeWithException(IllegalStateException(message))
    }
    request.onblocked = {
        cont.resumeWithException(IllegalStateException("IndexedDB open request was blocked"))
    }

    cont.invokeOnCancellation {
        request.onsuccess = null
        request.onerror = null
        request.onblocked = null
    }
}

private suspend fun awaitResult(request: IDBRequest): JsAny? = suspendCancellableCoroutine { cont ->
    request.onsuccess = { cont.resume(request.result) }
    request.onerror = {
        val message = request.error.messageOrFallback("IndexedDB request failed")
        cont.resumeWithException(IllegalStateException(message))
    }

    cont.invokeOnCancellation {
        request.onsuccess = null
        request.onerror = null
    }
}

private suspend fun awaitCompletion(transaction: IDBTransaction) = suspendCancellableCoroutine { cont ->
    transaction.oncomplete = { cont.resume(Unit) }
    transaction.onerror = {
        val message = transaction.error.messageOrFallback("IndexedDB transaction failed")
        cont.resumeWithException(IllegalStateException(message))
    }
    transaction.onabort = {
        cont.resumeWithException(IllegalStateException("IndexedDB transaction aborted"))
    }

    cont.invokeOnCancellation {
        transaction.oncomplete = null
        transaction.onerror = null
        transaction.onabort = null
    }
}

private fun PersistedRecord.toJsObject(): JsPersistedRecord {
    val jsRecord = createJsObject().unsafeCast<JsPersistedRecord>()
    jsRecord.key = this.key
    jsRecord.firstVersion = this.firstVersion
    jsRecord.lastVersion = this.lastVersion
    val valuesArray = createJsArray()
    for (node in this.values) {
        jsArrayPush(valuesArray, node.toJsObject().unsafeCast<JsAny>())
    }
    jsRecord.values = valuesArray
    return jsRecord
}

private fun PersistedNode.toJsObject(): JsPersistedNode = when (this) {
    is PersistedValueNode -> {
        val jsNode = createJsObject().unsafeCast<JsPersistedNode>()
        jsNode.kind = VALUE_KIND
        jsNode.reference = this.reference
        jsNode.version = this.version
        jsNode.valueJson = this.valueJson
        jsNode.isDeleted = this.isDeleted
        jsNode
    }
    is PersistedHistoricNode -> {
        val jsNode = createJsObject().unsafeCast<JsPersistedNode>()
        jsNode.kind = HISTORIC_KIND
        jsNode.reference = this.reference
        val historyArray = createJsArray()
        for (entry in this.history) {
            jsArrayPush(historyArray, entry.toHistoryEntryJs().unsafeCast<JsAny>())
        }
        jsNode.history = historyArray
        jsNode
    }
}

private fun PersistedValueNode.toHistoryEntryJs(): JsPersistedHistoryEntry {
    val entry = createJsObject().unsafeCast<JsPersistedHistoryEntry>()
    entry.version = this.version
    entry.valueJson = this.valueJson
    entry.isDeleted = this.isDeleted
    return entry
}

private fun toPersistedRecord(jsValue: JsAny): PersistedRecord {
    val jsRecord = jsValue.unsafeCast<JsPersistedRecord>()
    val key = jsRecord.key ?: throw IllegalStateException("IndexedDB record is missing key")
    val firstVersion = jsRecord.firstVersion
        ?: throw IllegalStateException("IndexedDB record is missing firstVersion")
    val lastVersion = jsRecord.lastVersion
        ?: throw IllegalStateException("IndexedDB record is missing lastVersion")
    val values = mutableListOf<PersistedNode>()
    val valueArray = jsRecord.values?.unsafeCast<JsArray<JsAny>>()
    if (valueArray != null) {
        val size = valueArray.length
        for (index in 0 until size) {
            val valueAny = valueArray[index] ?: continue
            val valueJs = valueAny.unsafeCast<JsPersistedNode>()
            values += toPersistedNode(valueJs)
        }
    }

    return PersistedRecord(
        key = key,
        firstVersion = firstVersion,
        lastVersion = lastVersion,
        values = values
    )
}

private fun toPersistedNode(jsValue: JsPersistedNode): PersistedNode {
    val kind = jsValue.kind ?: throw IllegalStateException("IndexedDB node is missing kind")
    val reference = jsValue.reference ?: throw IllegalStateException("IndexedDB node is missing reference")

    return when (kind) {
        VALUE_KIND -> PersistedValueNode(
            reference = reference,
            version = jsValue.version ?: throw IllegalStateException("IndexedDB node is missing version"),
            valueJson = jsValue.valueJson,
            isDeleted = jsValue.isDeleted ?: false
        )
        HISTORIC_KIND -> {
            val historyEntries = mutableListOf<PersistedValueNode>()
            val historyArray = jsValue.history?.unsafeCast<JsArray<JsAny>>()
            if (historyArray != null) {
                val size = historyArray.length
                for (index in 0 until size) {
                    val entryAny = historyArray[index] ?: continue
                    val entry = entryAny.unsafeCast<JsPersistedHistoryEntry>()
                    val version = entry.version
                        ?: throw IllegalStateException("IndexedDB historic node is missing version")
                    historyEntries += PersistedValueNode(
                        reference = reference,
                        version = version,
                        valueJson = entry.valueJson,
                        isDeleted = entry.isDeleted ?: false
                    )
                }
            }
            val history = historyEntries
            PersistedHistoricNode(reference, history)
        }
        else -> throw IllegalStateException("Unknown IndexedDB node kind $kind")
    }
}

private external interface JsPersistedRecord : JsAny {
    var key: String?
    var firstVersion: String?
    var lastVersion: String?
    var values: JsAny?
}

private external interface JsPersistedNode : JsAny {
    var kind: String?
    var reference: String?
    var version: String?
    var valueJson: String?
    var isDeleted: Boolean?
    var history: JsAny?
}

private external interface JsPersistedHistoryEntry : JsAny {
    var version: String?
    var valueJson: String?
    var isDeleted: Boolean?
}

private fun createMissingStores(database: IDBDatabase, storeNames: List<String>) {
    for (storeName in storeNames) {
        if (!hasStore(database, storeName)) {
            database.createObjectStore(storeName)
        }
    }
}

private fun verifyStores(database: IDBDatabase, storeNames: List<String>) {
    for (storeName in storeNames) {
        if (!hasStore(database, storeName)) {
            throw IllegalStateException("IndexedDB store $storeName is missing")
        }
    }
}

private fun hasStore(database: IDBDatabase, storeName: String): Boolean =
    databaseHasStore(database, storeName)

private fun databaseHasStore(database: IDBDatabase, name: String): Boolean {
    val objectStoreNames = database.objectStoreNames ?: return false
    val length = objectStoreNames.length ?: return false
    for (index in 0 until length) {
        if (objectStoreNames.item(index) == name) {
            return true
        }
    }

    return false
}

private fun findIndexedDb(): IDBFactory? =
    findIndexedDbFactory()?.unsafeCast<IDBFactory>()

private fun JsAny?.messageOrFallback(fallback: String): String =
    this?.unsafeCast<JsErrorMessage>()?.message ?: fallback

private external interface IDBFactory : JsAny {
    fun open(name: String, version: Int): IDBOpenDBRequest
}

private external interface IDBOpenDBRequest : IDBRequest {
    override val result: IDBDatabase
    var onupgradeneeded: ((JsAny) -> Unit)?
    var onblocked: ((JsAny) -> Unit)?
}

private external interface IDBDatabase : JsAny {
    val objectStoreNames: DOMStringList?
    fun createObjectStore(name: String): IDBObjectStore
    fun transaction(storeName: String, mode: String = definedExternally): IDBTransaction
    fun close()
}

private external interface IDBTransaction : JsAny {
    val error: JsAny?
    fun objectStore(name: String): IDBObjectStore
    var oncomplete: ((JsAny) -> Unit)?
    var onerror: ((JsAny) -> Unit)?
    var onabort: ((JsAny) -> Unit)?
}

private external interface IDBObjectStore : JsAny {
    fun getAll(): IDBRequest
    fun put(value: JsAny, key: String): IDBRequest
    fun clear(): IDBRequest
}

private external interface IDBRequest : JsAny {
    val result: JsAny?
    val error: JsAny?
    var onsuccess: ((JsAny) -> Unit)?
    var onerror: ((JsAny) -> Unit)?
}

private external interface DOMStringList : JsAny {
    val length: Int?
    fun item(index: Int): String?
}

private external interface JsErrorMessage : JsAny {
    val message: String?
}


private const val MODE_READONLY = "readonly"
private const val MODE_READWRITE = "readwrite"
private const val VALUE_KIND = "value"
private const val HISTORIC_KIND = "historic"

private fun isNullOrUndefined(value: JsAny?): Boolean = value == null || isUndefined(value)

@JsFun("function() { return []; }")
private external fun createJsArray(): JsAny

@JsFun("function(array, value) { array.push(value); }")
private external fun jsArrayPush(array: JsAny, value: JsAny)

@JsFun("function() { return {}; }")
private external fun createJsObject(): JsAny

@JsFun("function(value) { return value === undefined; }")
private external fun isUndefined(value: JsAny?): Boolean

@JsFun("function() { return globalThis.indexedDB || globalThis.mozIndexedDB || globalThis.webkitIndexedDB || globalThis.msIndexedDB || null; }")
private external fun findIndexedDbFactory(): JsAny?

