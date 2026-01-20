package io.maryk.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import maryk.core.models.IsRootDataModel
import maryk.core.models.key
import maryk.core.properties.types.Key
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.requests.delete
import maryk.core.query.requests.getChanges
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.requests.add
import maryk.core.values.Values
import maryk.datastore.shared.IsDataStore
import maryk.core.query.responses.AddResponse
import maryk.core.query.responses.statuses.AddSuccess
import maryk.core.query.responses.statuses.AlreadyExists
import maryk.core.query.responses.statuses.AuthFail
import maryk.core.query.responses.statuses.ServerFail
import maryk.core.query.responses.statuses.ValidationFail
import maryk.core.query.responses.statuses.IsAddResponseStatus

@Stable
class BrowserState(
    private val connector: StoreConnector,
    private val scope: CoroutineScope,
) {
    var activeConnection by mutableStateOf<StoreConnection?>(null)
        private set

    var models by mutableStateOf<List<ModelEntry>>(emptyList())
        private set

    var selectedModelId by mutableStateOf<UInt?>(null)
        private set

    var scanConfig by mutableStateOf(ScanConfig())
        private set

    var scanResults by mutableStateOf<List<ScanRow>>(emptyList())
        private set

    var scanStatus by mutableStateOf<String?>(null)
        private set

    var scanGeneration by mutableStateOf(0)
        private set

    var isWorking by mutableStateOf(false)
        private set

    var isScanning by mutableStateOf(false)
        private set

    var recordDetails by mutableStateOf<RecordDetails?>(null)
        private set

    var selectedModelField by mutableStateOf<ModelFieldRef?>(null)
        private set

    var pendingHighlightKey by mutableStateOf<Key<IsRootDataModel>?>(null)
        private set

    var pendingHighlightModelId by mutableStateOf<UInt?>(null)
        private set

    var referenceBackTarget by mutableStateOf<ReferenceBackTarget?>(null)
        private set

    var lastActionMessage by mutableStateOf<String?>(null)
        private set

    var exportDialog by mutableStateOf<ExportDialogRequest?>(null)
        private set

    var exportToastMessage by mutableStateOf<String?>(null)
        private set

    var historyChanges by mutableStateOf<List<VersionedChanges>>(emptyList())
        private set

    var showDeleteDialog by mutableStateOf(false)
        private set

    var pendingHardDelete by mutableStateOf(false)
        private set

    private var scanCursor by mutableStateOf(ScanCursor())

    private val modelRowCounts = mutableStateMapOf<UInt, ModelRowCount>()
    private val orderFieldsByModel = mutableStateMapOf<UInt, String>()

    fun connect(definition: StoreDefinition) {
        isWorking = true
        lastActionMessage = null
        scanStatus = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { connector.connect(definition) }
            when (result) {
                is ConnectResult.Error -> {
                    lastActionMessage = result.message
                    isWorking = false
                }
                is ConnectResult.Success -> {
                    activeConnection?.close()
                    activeConnection = result.connection
                    models = collectModels(result.connection.dataStore)
                    refreshModelCounts(result.connection.dataStore)
                    selectedModelId = models.firstOrNull()?.id
                    resetScan()
                    scanStatus = if (models.isEmpty()) {
                        "No models found in the connected store."
                    } else {
                        null
                    }
                    recordDetails = null
                    selectedModelField = null
                    lastActionMessage = null
                    isWorking = false
                    if (selectedModelId != null) {
                        scanFromStart()
                    }
                }
            }
        }
    }

    fun disconnect() {
        activeConnection?.close()
        activeConnection = null
        models = emptyList()
        selectedModelId = null
        scanResults = emptyList()
        scanCursor = ScanCursor()
        recordDetails = null
        selectedModelField = null
        scanStatus = null
        modelRowCounts.clear()
        orderFieldsByModel.clear()
        historyChanges = emptyList()
        lastActionMessage = "Disconnected."
    }

    fun clearLastActionMessage() {
        lastActionMessage = null
    }

    fun selectModel(modelId: UInt) {
        selectedModelId = modelId
        recordDetails = null
        selectedModelField = null
        historyChanges = emptyList()
        scanConfig = scanConfig.copy(orderFields = orderFieldsByModel[modelId].orEmpty())
        scanFromStart()
    }

    fun updateOrderFields(orderFields: String) {
        selectedModelId?.let { orderFieldsByModel[it] = orderFields }
        scanConfig = scanConfig.copy(orderFields = orderFields)
        scanFromStart()
    }

    fun selectModelField(field: ModelFieldRef?) {
        selectedModelField = field
    }

    fun openReference(
        modelName: String,
        key: Key<IsRootDataModel>,
        setBackTarget: Boolean = true,
    ) {
        val (modelId, _) = resolveModelByName(modelName) ?: return
        openReferenceById(modelId, key, setBackTarget)
    }

    fun openReferenceById(
        modelId: UInt,
        key: Key<IsRootDataModel>,
        setBackTarget: Boolean = true,
    ) {
        if (setBackTarget) {
            val details = recordDetails
            if (details != null) {
                val backModelId = models.firstOrNull { it.name == details.model.Meta.name }?.id
                    ?: selectedModelId
                if (backModelId != null) {
                    referenceBackTarget = ReferenceBackTarget(
                        modelId = backModelId,
                        modelName = details.model.Meta.name,
                        key = details.key,
                        keyText = details.keyText,
                    )
                }
            }
        }
        selectedModelId = modelId
        recordDetails = null
        historyChanges = emptyList()
        pendingHighlightKey = key
        pendingHighlightModelId = modelId
        scanFromStart()
    }

    fun clearPendingHighlight() {
        pendingHighlightKey = null
        pendingHighlightModelId = null
    }

    fun backToReferenceTarget() {
        val target = referenceBackTarget ?: return
        referenceBackTarget = null
        openReferenceById(target.modelId, target.key, setBackTarget = false)
    }

    fun clearReferenceBackTarget() {
        referenceBackTarget = null
    }

    fun closeDeleteDialog() {
        pendingHardDelete = false
        showDeleteDialog = false
    }

    fun markPendingHardDelete(enabled: Boolean) {
        pendingHardDelete = enabled
    }

    fun scanFromStart() {
        scanResults = emptyList()
        scanCursor = ScanCursor()
        scanGeneration += 1
        loadNextPage(reset = true)
    }

    fun loadMoreScanResults() {
        loadNextPage(reset = false)
    }

    fun canLoadMoreScanResults(): Boolean {
        return !scanCursor.endReached && !isScanning
    }

    fun hasMoreScanResults(): Boolean {
        return !scanCursor.endReached
    }

    fun openRecord(row: ScanRow) {
        val connection = activeConnection ?: return
        val dataModel = resolveSelectedModel(connection.dataStore) ?: return
        isWorking = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val response = connection.dataStore.execute(
                        dataModel.get(
                            row.key,
                            filterSoftDeleted = false,
                        )
                    )
                    response.values.firstOrNull()
                } catch (_: Throwable) {
                    null
                }
            }
            if (result == null) {
                lastActionMessage = "Record not found."
                isWorking = false
                return@launch
            }
            val yaml = serializeRecordToYaml(dataModel, result)
            recordDetails = RecordDetails(
                model = dataModel,
                key = row.key,
                keyText = row.key.toString(),
                firstVersion = result.firstVersion,
                lastVersion = result.lastVersion,
                isDeleted = result.isDeleted,
                yaml = yaml,
                editedYaml = yaml,
            )
            historyChanges = emptyList()
            loadHistory()
            isWorking = false
        }
    }

    fun updateRecordEditor(text: String) {
        recordDetails = recordDetails?.copy(
            editedYaml = text,
            dirty = text != recordDetails?.yaml,
        )
    }

    fun resetRecordEditor() {
        recordDetails = recordDetails?.copy(
            editedYaml = recordDetails?.yaml.orEmpty(),
            dirty = false,
        )
    }

    fun applyRecordChanges(useVersionGuard: Boolean = true) {
        val connection = activeConnection ?: return
        val details = recordDetails ?: return
        isWorking = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val values = parseValuesFromYaml(details.model, details.editedYaml)
                    val changes = values.toChanges().toList()
                    val ifVersion = if (useVersionGuard) details.lastVersion else null
                    applyChanges(
                        dataStore = connection.dataStore,
                        dataModel = details.model,
                        key = details.key,
                        changes = changes,
                        ifVersion = ifVersion,
                        action = "Updated",
                    )
                } catch (e: Throwable) {
                    ApplyResult("Update failed: ${e.message ?: e::class.simpleName}", success = false)
                }
            }
            lastActionMessage = result.message
            isWorking = false
            if (result.success) {
                refreshRecord()
                scanFromStart()
            }
        }
    }

    fun applyRecordChanges(changes: List<IsChange>, useVersionGuard: Boolean = true) {
        val connection = activeConnection ?: return
        val details = recordDetails ?: return
        isWorking = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val ifVersion = if (useVersionGuard) details.lastVersion else null
                    applyChanges(
                        dataStore = connection.dataStore,
                        dataModel = details.model,
                        key = details.key,
                        changes = changes,
                        ifVersion = ifVersion,
                        action = "Updated",
                    )
                } catch (e: Throwable) {
                    ApplyResult("Update failed: ${e.message ?: e::class.simpleName}", success = false)
                }
            }
            lastActionMessage = result.message
            isWorking = false
            if (result.success) {
                refreshRecord()
                scanFromStart()
            }
        }
    }

    fun addRecord(values: Values<IsRootDataModel>, key: Key<IsRootDataModel>?) {
        val connection = activeConnection ?: return
        val dataModel = currentDataModel() ?: return
        isWorking = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val request = if (key != null) dataModel.add(key to values) else dataModel.add(values)
                    val response: AddResponse<IsRootDataModel> = connection.dataStore.execute(request)
                    formatAddStatus(dataModel, response.statuses.firstOrNull())
                } catch (e: Throwable) {
                    ApplyResult("Add failed: ${e.message ?: e::class.simpleName}", success = false)
                }
            }
            lastActionMessage = result.message
            isWorking = false
            if (result.success) {
                result.targetKey?.let { newKey ->
                    pendingHighlightKey = newKey
                    pendingHighlightModelId = selectedModelId
                }
                scanFromStart()
            }
        }
    }

    fun deleteRecord(hardDelete: Boolean) {
        val connection = activeConnection ?: return
        val details = recordDetails ?: return
        isWorking = true
        closeDeleteDialog()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val request = details.model.delete(details.key, hardDelete = hardDelete)
                    connection.dataStore.execute(request)
                    ApplyResult(
                        if (hardDelete) {
                            "Hard deleted ${details.model.Meta.name} ${details.keyText}."
                        } else {
                            "Deleted ${details.model.Meta.name} ${details.keyText}."
                        },
                        success = true,
                    )
                } catch (e: Throwable) {
                    ApplyResult("Delete failed: ${e.message ?: e::class.simpleName}", success = false)
                }
            }
            lastActionMessage = result.message
            isWorking = false
            if (result.success) {
                if (hardDelete) {
                    recordDetails = null
                } else {
                    refreshRecord()
                }
                scanFromStart()
            }
        }
    }

    fun deleteRow(row: ScanRow, hardDelete: Boolean = false) {
        val connection = activeConnection ?: return
        val dataModel = currentDataModel() ?: return
        isWorking = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    connection.dataStore.execute(dataModel.delete(row.key, hardDelete = hardDelete))
                    ApplyResult(
                        if (hardDelete) {
                            "Hard deleted ${dataModel.Meta.name} ${row.keyText}."
                        } else {
                            "Deleted ${dataModel.Meta.name} ${row.keyText}."
                        },
                        success = true,
                    )
                } catch (e: Throwable) {
                    ApplyResult("Delete failed: ${e.message ?: e::class.simpleName}", success = false)
                }
            }
            lastActionMessage = result.message
            isWorking = false
            if (result.success) {
                if (recordDetails?.keyText == row.keyText) {
                    refreshRecord()
                }
                scanFromStart()
            }
        }
    }

    private fun refreshRecord() {
        val connection = activeConnection ?: return
        val details = recordDetails ?: return
        scope.launch {
            val refreshed = withContext(Dispatchers.IO) {
                try {
                    val response = connection.dataStore.execute(
                        details.model.get(details.key, filterSoftDeleted = false)
                    )
                    response.values.firstOrNull()
                } catch (_: Throwable) {
                    null
                }
            }
            if (refreshed != null) {
                val yaml = serializeRecordToYaml(details.model, refreshed)
                recordDetails = details.copy(
                    firstVersion = refreshed.firstVersion,
                    lastVersion = refreshed.lastVersion,
                    isDeleted = refreshed.isDeleted,
                    yaml = yaml,
                    editedYaml = yaml,
                    dirty = false,
                )
                loadHistory()
            }
        }
    }

    private fun loadHistory() {
        val connection = activeConnection ?: return
        val details = recordDetails ?: return
        scope.launch {
            val changes = withContext(Dispatchers.IO) {
                try {
                    val key = details.key
                    val response = connection.dataStore.execute(
                        details.model.getChanges(key, filterSoftDeleted = false)
                    )
                    response.changes.firstOrNull()?.changes.orEmpty()
                } catch (_: Throwable) {
                    emptyList()
                }
            }
            historyChanges = changes
        }
    }

    private fun loadNextPage(reset: Boolean) {
        val connection = activeConnection ?: return
        val dataModel = resolveSelectedModel(connection.dataStore) ?: return
        if (scanCursor.endReached && !reset) return
        isWorking = true
        isScanning = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                buildScanPage(connection.dataStore, dataModel, reset)
            }
            when (result) {
                is ScanPageResult.Error -> {
                    scanStatus = result.message
                    isWorking = false
                    isScanning = false
                }
                is ScanPageResult.Success -> {
                    scanStatus = result.message
                    scanCursor = result.cursor
                    scanResults = if (reset) result.rows else scanResults + result.rows
                    isWorking = false
                    isScanning = false
                }
            }
        }
    }

    private fun buildScanPage(
        dataStore: IsDataStore,
        dataModel: IsRootDataModel,
        reset: Boolean,
    ): ScanPageResult {
        val requestContext = ScanQueryParser.createRequestContext(dataModel)
        val showPaths = ScanQueryParser.parseReferencePaths(listOf(scanConfig.showFields))
        val selectPaths = ScanQueryParser.parseReferencePaths(listOf(scanConfig.selectFields))
        val displayFields = resolveDisplayFields(dataModel, showPaths, requestContext)
        val selectGraph = try {
            val merged = (showPaths + selectPaths).distinct()
            ScanQueryParser.parseSelectGraph(dataModel, merged)
        } catch (_: Throwable) {
            null
        }

        val where = scanConfig.filterText.takeIf { it.isNotBlank() }?.let { raw ->
            try {
                ScanQueryParser.parseFilter(dataModel, raw)
            } catch (e: Throwable) {
                return ScanPageResult.Error("Filter error: ${e.message ?: e::class.simpleName}")
            }
        }

        val order = scanConfig.orderFields.takeIf { it.isNotBlank() }?.let { raw ->
            try {
                ScanQueryParser.parseOrder(dataModel, listOf(raw))
            } catch (e: Throwable) {
                return ScanPageResult.Error("Order error: ${e.message ?: e::class.simpleName}")
            }
        }

        val limit = scanConfig.limit.coerceIn(1, 500)
        val startKey = if (reset) {
            scanConfig.startKey.takeIf { it.isNotBlank() }?.let { token ->
                try {
                    dataModel.key(token)
                } catch (e: Throwable) {
                    return ScanPageResult.Error("Start key error: ${e.message ?: e::class.simpleName}")
                }
            }
        } else {
            scanCursor.nextStartKey
        }

        val includeStart = if (reset) scanConfig.includeStart else scanCursor.includeStart
        val toVersion = scanConfig.toVersion.takeIf { it.isNotBlank() }?.toULongOrNull()

        return try {
            val response = runBlocking {
                dataStore.execute(
                    dataModel.scan(
                        startKey = startKey,
                        select = selectGraph,
                        where = where,
                        order = order,
                        limit = limit.toUInt(),
                        includeStart = includeStart,
                        toVersion = toVersion,
                        filterSoftDeleted = !scanConfig.includeDeleted,
                        allowTableScan = true,
                    )
                )
            }

            val rows = response.values.map { valuesWithMeta ->
                ScanRow(
                    key = valuesWithMeta.key,
                    keyText = valuesWithMeta.key.toString(),
                    values = valuesWithMeta.values,
                    isDeleted = valuesWithMeta.isDeleted,
                    lastVersion = valuesWithMeta.lastVersion,
                    summary = buildSummary(
                        dataModel = dataModel,
                        values = valuesWithMeta.values,
                        displayFields = displayFields,
                        requestContext = requestContext,
                        maxChars = 320,
                    ),
                )
            }

            val endReached = response.values.size < limit
            val nextKey = response.values.lastOrNull()?.key
            ScanPageResult.Success(
                rows = rows,
                cursor = ScanCursor(
                    nextStartKey = nextKey,
                    includeStart = false,
                    endReached = endReached,
                ),
                message = if (rows.isEmpty()) "No results." else "Loaded ${rows.size} rows.",
            )
        } catch (e: Throwable) {
            ScanPageResult.Error("Scan failed: ${e.message ?: e::class.simpleName}")
        }
    }

    private fun resolveSelectedModel(dataStore: IsDataStore): IsRootDataModel? {
        val modelId = selectedModelId ?: return null
        return dataStore.dataModelsById[modelId]
    }

    fun currentDataModel(): IsRootDataModel? {
        val connection = activeConnection ?: return null
        return selectedModelId?.let { connection.dataStore.dataModelsById[it] }
    }

    fun resolveModelByName(name: String): Pair<UInt, IsRootDataModel>? {
        val connection = activeConnection ?: return null
        return connection.dataStore.dataModelsById.entries
            .firstOrNull { it.value.Meta.name == name }
            ?.let { it.key to it.value }
    }

    fun modelRowCount(modelId: UInt): ModelRowCount? = modelRowCounts[modelId]

    fun requestExportAllDialog() {
        exportDialog = ExportDialogRequest(modelId = null)
    }

    fun requestExportModelDialog(modelId: UInt) {
        exportDialog = ExportDialogRequest(modelId = modelId)
    }

    fun clearExportDialog() {
        exportDialog = null
    }

    fun clearExportToast() {
        exportToastMessage = null
    }

    fun exportModelById(modelId: UInt, format: ModelExportFormat) {
        val connection = activeConnection ?: return
        val model = connection.dataStore.dataModelsById[modelId] ?: return
        val folder = pickDirectory("Export ${model.Meta.name}") ?: return
        isWorking = true
        exportToastMessage = null
        scope.launch {
            val allModels = buildAllModelsByName(connection)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    exportModelToFolder(model, format, folder, allModels)
                }
            }
            if (result.isSuccess) {
                exportToastMessage = "Exported ${model.Meta.name} as ${format.label}."
            } else {
                lastActionMessage = "Export failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
            }
            isWorking = false
        }
    }

    fun exportAllModels(format: ModelExportFormat) {
        val connection = activeConnection ?: return
        val folder = pickDirectory("Export all models") ?: return
        isWorking = true
        exportToastMessage = null
        scope.launch {
            val allModels = buildAllModelsByName(connection)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    allModels.values.forEach { model ->
                        exportModelToFolder(model, format, folder, allModels)
                    }
                }
            }
            if (result.isSuccess) {
                exportToastMessage = "Exported ${allModels.size} models as ${format.label}."
            } else {
                lastActionMessage = "Export failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
            }
            isWorking = false
        }
    }

    private fun buildAllModelsByName(connection: StoreConnection): Map<String, IsRootDataModel> {
        return connection.dataStore.dataModelsById.values.associateBy { it.Meta.name }
    }

    private fun refreshModelCounts(dataStore: IsDataStore) {
        val modelsSnapshot = models.toList()
        modelRowCounts.clear()
        scope.launch {
            modelsSnapshot.forEach { entry ->
                val count = withContext(Dispatchers.IO) {
                    fetchModelRowCount(dataStore, entry.id)
                }
                if (count != null) {
                    modelRowCounts[entry.id] = count
                }
            }
        }
    }

    private suspend fun fetchModelRowCount(
        dataStore: IsDataStore,
        modelId: UInt,
    ): ModelRowCount? {
        val dataModel = dataStore.dataModelsById[modelId] ?: return null
        return try {
            val response = dataStore.execute(
                dataModel.scan(
                    limit = 101u,
                    filterSoftDeleted = true,
                    allowTableScan = true,
                )
            )
            val size = response.values.size
            if (size > 100) {
                ModelRowCount(100, true)
            } else {
                ModelRowCount(size, false)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun collectModels(dataStore: IsDataStore): List<ModelEntry> {
        return dataStore.dataModelsById.entries
            .map { (id, model) -> ModelEntry(model.Meta.name, id) }
            .sortedBy { it.name.lowercase() }
    }

    private fun resetScan() {
        scanResults = emptyList()
        scanCursor = ScanCursor()
        scanStatus = null
        scanGeneration += 1
    }
}

private fun formatAddStatus(
    dataModel: IsRootDataModel,
    status: IsAddResponseStatus<IsRootDataModel>?,
): ApplyResult {
    if (status == null) {
        return ApplyResult("Add failed: no response status.", success = false)
    }
    return when (status) {
        is AddSuccess -> ApplyResult(
            "Added ${dataModel.Meta.name} ${status.key} (version ${status.version}).",
            success = true,
            targetKey = status.key,
        )
        is AlreadyExists -> ApplyResult(
            "Add failed: ${dataModel.Meta.name} ${status.key} already exists.",
            success = false,
        )
        is ValidationFail -> {
            val details = status.exceptions.joinToString(separator = "; ") { exception ->
                exception.message ?: exception.toString()
            }
            ApplyResult("Add failed: $details", success = false)
        }
        is AuthFail -> ApplyResult("Add failed: authorization error.", success = false)
        is ServerFail -> ApplyResult("Add failed: ${status.reason}", success = false)
        else -> ApplyResult("Add failed: ${status.statusType}.", success = false)
    }
}

data class ModelEntry(
    val name: String,
    val id: UInt,
)

data class ModelRowCount(
    val count: Int,
    val capped: Boolean,
)

data class ScanConfig(
    val filterText: String = "",
    val showFields: String = "",
    val selectFields: String = "",
    val orderFields: String = "",
    val limit: Int = 100,
    val includeDeleted: Boolean = false,
    val toVersion: String = "",
    val startKey: String = "",
    val includeStart: Boolean = true,
)

data class ScanRow(
    val key: Key<IsRootDataModel>,
    val keyText: String,
    val values: Values<IsRootDataModel>,
    val isDeleted: Boolean,
    val lastVersion: ULong,
    val summary: String,
)

data class RecordDetails(
    val model: IsRootDataModel,
    val key: Key<IsRootDataModel>,
    val keyText: String,
    val firstVersion: ULong,
    val lastVersion: ULong,
    val isDeleted: Boolean,
    val yaml: String,
    val editedYaml: String,
    val dirty: Boolean = false,
)

data class ModelFieldRef(
    val path: String,
    val wrapper: maryk.core.properties.definitions.wrapper.IsDefinitionWrapper<*, *, *, *>?,
    val definition: maryk.core.properties.definitions.IsPropertyDefinition<*>? = null,
    val typeIndex: UInt? = null,
)

data class ReferenceBackTarget(
    val modelId: UInt,
    val modelName: String,
    val key: Key<IsRootDataModel>,
    val keyText: String,
)

data class ExportDialogRequest(
    val modelId: UInt?,
)

private data class ScanCursor(
    val nextStartKey: Key<IsRootDataModel>? = null,
    val includeStart: Boolean = true,
    val endReached: Boolean = false,
)

private sealed class ScanPageResult {
    data class Success(
        val rows: List<ScanRow>,
        val cursor: ScanCursor,
        val message: String,
    ) : ScanPageResult()

    data class Error(
        val message: String,
    ) : ScanPageResult()
}
