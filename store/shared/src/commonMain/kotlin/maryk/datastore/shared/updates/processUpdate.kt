package maryk.datastore.shared.updates

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.yield
import maryk.core.models.IsRootDataModel
import maryk.core.exceptions.StorageException
import maryk.core.query.ValuesWithMetaData
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.query.changes.IndexChange
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.requests.IsChangesRequest
import maryk.core.query.requests.IsFlowRequest
import maryk.core.query.requests.IsScanRequest
import maryk.core.query.requests.ScanRequest
import maryk.core.query.requests.ScanCursor
import maryk.core.query.requests.createCursor
import maryk.core.query.requests.resolveCursor
import maryk.core.query.requests.ScanUpdatesRequest
import maryk.core.query.requests.get
import maryk.core.query.requests.scan
import maryk.core.query.requests.scanUpdates
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.RemovalReason
import maryk.core.query.responses.updates.RemovalReason.HardDelete
import maryk.core.query.responses.updates.RemovalReason.NotInRange
import maryk.core.query.responses.updates.RemovalReason.SoftDelete
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.core.values.Values
import maryk.core.values.copyWithOwnedValues
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.updates.Update.Addition
import maryk.datastore.shared.updates.Update.Change
import maryk.datastore.shared.updates.Update.Deletion

/** processes a single update */
internal suspend fun <DM : IsRootDataModel, RQ: IsFlowRequest<DM, *>> Update<DM>.process(
    updateListener: UpdateListener<DM, RQ>,
    dataStore: IsDataStore,
    sharedFlow: SendChannel<IsUpdateResponse<DM>>
) {
    if (updateListener.includesInSnapshot(this)) return
    val request = updateListener.request
    val currentKeys = updateListener.matchingKeys.value
    // Only process object requests or change requests if the version is after or equal to from version
    if (request !is IsChangesRequest<*, *> || request.fromVersion <= version) {
        when (this) {
            is Addition<DM> -> {
                // Referenced and named predicates use datastore filter semantics.
                // The read can also include newer commits, so publish and cover
                // that complete snapshot instead of replaying its queued deltas.
                val addition = if (request.where != null) {
                    dataStore.execute(dataModel.get(
                        key,
                        select = null,
                        where = request.where,
                        filterSoftDeleted = request.filterSoftDeleted,
                    )).values.singleOrNull()
                } else if (!isDeleted || !request.filterSoftDeleted) {
                    ValuesWithMetaData(key, values, version, version, isDeleted)
                } else null
                if (addition != null) {
                    val insertIndex = updateListener.addValues(key, addition.values, addition.lastVersion)
                    if (insertIndex != null) {
                        if (request.where != null) updateListener.includeSnapshot(key, addition.lastVersion)
                        sharedFlow.sendAddition(updateListener,
                            AdditionUpdate(
                                key = key,
                                version = addition.lastVersion,
                                firstVersion = addition.firstVersion,
                                insertionIndex = insertIndex,
                                values = addition.values.filterWithSelect(request.select),
                                isDeleted = addition.isDeleted
                            )
                        )

                        // Remove any values after the limit
                        if (updateListener is UpdateListenerForScan<DM, *> && updateListener.matchingKeys.value.size.toUInt() > updateListener.request.limit) {
                            val keyToRemove = updateListener.getLast()
                            updateListener.removeKey(keyToRemove)

                            sharedFlow.send(
                                RemovalUpdate(
                                    key = keyToRemove,
                                    version = addition.lastVersion,
                                    reason = NotInRange
                                )
                            )
                        }
                    }
                }
            }
            is Change<DM> -> {
                val softDeleteChange = changes.filterIsInstance<ObjectSoftDeleteChange>().lastOrNull()
                val shouldDelete = softDeleteChange?.isDeleted == true && request.filterSoftDeleted
                val isUndelete = softDeleteChange?.isDeleted == false

                if (currentKeys.contains(key)) {
                    if (shouldDelete) {
                        handleDeletion(dataStore, this, SoftDelete, updateListener, sharedFlow)
                    } else {
                        var currentValues: Values<DM>? = null
                        if (
                            updateListener.filterContainsMutableValues ||
                            updateListener is UpdateListenerForGet<DM, *> && request.where != null ||
                            updateListener is UpdateListenerForScan<DM, *> && updateListener.usesIndexScan
                        ) {
                            val response = dataStore.execute(
                                dataModel.get(
                                    key,
                                    select = null,
                                    where = request.where,
                                    filterSoftDeleted = request.filterSoftDeleted
                                )
                            )

                            if (response.values.isEmpty()) {
                                handleDeletion(dataStore, this, NotInRange, updateListener, sharedFlow)
                                return
                            }
                            currentValues = response.values.single().values
                        }

                        updateListener.changeOrder(this, currentValues) { newIndex, orderChanged ->
                            if (newIndex == null) {
                                handleDeletion(dataStore, this, NotInRange, updateListener, sharedFlow)
                            } else {
                                createChangeUpdate(request.select, orderChanged, newIndex)?.let { changeUpdate ->
                                    sharedFlow.send(changeUpdate)
                                }
                            }
                        }
                    }
                } else if (
                    !shouldDelete &&
                    (
                        isUndelete ||
                            updateListener.filterContainsMutableValues ||
                            updateListener is UpdateListenerForGet<DM, *> && request.where != null
                    ) &&
                    (
                        updateListener is UpdateListenerForGet<DM, *> ||
                            updateListener is UpdateListenerForScan<DM, *> &&
                            updateListener.request.limit > 0u
                    )
                ) {
                    // Gets and table scans have no index change to establish membership, so re-read mutable matches.
                    yield()
                    val response = dataStore.execute(
                        request.dataModel.get(
                            key,
                            select = null,
                            where = request.where,
                            filterSoftDeleted = request.filterSoftDeleted
                        )
                    )
                    val addition = response.values.firstOrNull()
                    if (addition != null) {
                        updateListener.addValues(addition.key, addition.values, addition.lastVersion)?.let { insertionIndex ->
                            updateListener.includeSnapshot(addition.key, addition.lastVersion)
                            sharedFlow.sendAddition(updateListener,
                                AdditionUpdate(
                                    key = addition.key,
                                    values = addition.values.filterWithSelect(request.select),
                                    insertionIndex = insertionIndex,
                                    version = addition.lastVersion,
                                    firstVersion = addition.firstVersion,
                                    isDeleted = addition.isDeleted
                                )
                            )

                            if (
                                updateListener is UpdateListenerForScan<DM, *> &&
                                updateListener.matchingKeys.value.size.toUInt() > updateListener.request.limit
                            ) {
                                val keyToRemove = updateListener.getLast()
                                updateListener.removeKey(keyToRemove)
                                sharedFlow.send(
                                    RemovalUpdate(
                                        key = keyToRemove,
                                        version = version,
                                        reason = NotInRange
                                    )
                                )
                            }
                        }
                    }
                } else if (
                    !shouldDelete &&
                    updateListener is UpdateListenerForScan<DM, *> &&
                    updateListener.request.limit > 0u &&
                    (updateListener.indexScanRange != null || updateListener.usesUpdateHistoryIndex)
                ) {
                    // Membership and ordering must describe the same snapshot.
                    // An older queued index delta can disagree with a newer read.
                    yield()
                    val addition = dataStore.execute(
                        request.dataModel.get(
                            key,
                            select = null,
                            where = request.where,
                            filterSoftDeleted = request.filterSoftDeleted,
                        )
                    ).values.firstOrNull()
                    if (addition != null) {
                        updateListener.addValues(addition.key, addition.values, addition.lastVersion)?.let { newIndex ->
                            if (updateListener.matchingKeys.value.size.toUInt() > updateListener.request.limit) {
                                val lastKey = updateListener.getLast()
                                updateListener.removeKey(lastKey)
                                sharedFlow.send(RemovalUpdate(key = lastKey, version = version, reason = NotInRange))
                            }
                            updateListener.includeSnapshot(addition.key, addition.lastVersion)
                            sharedFlow.send(
                                AdditionUpdate(
                                    key = addition.key,
                                    values = addition.values.filterWithSelect(request.select),
                                    insertionIndex = newIndex,
                                    version = addition.lastVersion,
                                    firstVersion = addition.firstVersion,
                                    isDeleted = addition.isDeleted,
                                )
                            )
                        }
                    }
                }
            }
            is Deletion<DM> -> {
                if (currentKeys.contains(key) && (isHardDelete || request.filterSoftDeleted)) {
                    handleDeletion(
                        dataStore,
                        this,
                        if (isHardDelete) HardDelete else SoftDelete,
                        updateListener,
                        sharedFlow
                    )
                } else if (currentKeys.contains(key) && !isHardDelete) {
                    val softDelete = Change(
                        dataModel = dataModel,
                        key = key,
                        version = version,
                        changes = listOf(ObjectSoftDeleteChange(true))
                    )
                    var currentValues: Values<DM>? = null
                    if (updateListener is UpdateListenerForScan<DM, *> && updateListener.usesIndexScan) {
                        currentValues = dataStore.execute(
                            dataModel.get(
                                key,
                                select = null,
                                where = request.where,
                                filterSoftDeleted = false
                            )
                        ).values.singleOrNull()?.values
                    }
                    updateListener.changeOrder(softDelete, currentValues) { newIndex, orderChanged ->
                        if (newIndex != null) {
                            softDelete.createChangeUpdate(request.select, orderChanged, newIndex)?.let {
                                sharedFlow.send(it)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One addition per requested occurrence, including interleaved repeated get keys. */
private suspend fun <DM : IsRootDataModel> SendChannel<IsUpdateResponse<DM>>.sendAddition(
    listener: UpdateListener<DM, *>,
    addition: AdditionUpdate<DM>,
) {
    val indices = if (listener is UpdateListenerForGet<DM, *>) {
        listener.matchingKeys.value.mapIndexedNotNull { index, key -> index.takeIf { key == addition.key } }
    } else listOf(addition.insertionIndex)
    for (index in indices) {
        send(addition.copy(
            insertionIndex = index,
            values = addition.values.copyWithOwnedValues(),
        ))
    }
}

private fun <DM : IsRootDataModel> Change<DM>.createChangeUpdate(
    select: RootPropRefGraph<DM>?,
    orderChanged: Boolean,
    newIndex: Int
): ChangeUpdate<DM>? {
    // Filter now in search of possible delete
    val filteredChanges = changes.filterWithSelect(select).map { it.copyWithOwnedValues() }

    // Only send ChangeUpdate if order has changed or there are changes with are not just index changes
    // IndexChanges are covered with orderChanged check so filteredChanges need to contain more than IndexChanges
    if (orderChanged || (filteredChanges.isNotEmpty() && filteredChanges.find { it !is IndexChange } != null)) {
        return ChangeUpdate(
            key = key,
            version = version,
            index = newIndex,
            changes = filteredChanges
        )
    }

    return null
}

/** Handles the deletion of Values defined in [change] and if necessary request a new value to put at end */
private suspend fun <DM : IsRootDataModel, RQ: IsFlowRequest<DM, *>> handleDeletion(
    dataStore: IsDataStore,
    change: Update<DM>,
    reason: RemovalReason,
    updateListener: UpdateListener<DM, RQ>,
    sharedFlow: SendChannel<IsUpdateResponse<DM>>
) {
    val originalIndex = updateListener.removeKey(change.key)

    suspend fun sendRemoval() {
        sharedFlow.send(
            RemovalUpdate(
                key = change.key,
                version = change.version,
                reason = reason
            )
        )
    }

    if (
        updateListener is UpdateListenerForScan<DM, *> &&
        updateListener.request.limit > 0u &&
        updateListener.request.limit - 1u == updateListener.matchingKeys.value.size.toUInt()
    ) {
        val nextValue = dataStore.requestNextValues(
            updateListener,
            updateListener.matchingKeys.value,
        )
        nextValue?.also { additionUpdate ->
            val insertionIndex = updateListener.addValuesAtEnd(
                additionUpdate.key, additionUpdate.values, additionUpdate.version,
            )
            val selectedAddition = additionUpdate.copy(
                insertionIndex = insertionIndex,
                values = additionUpdate.values.filterWithSelect(updateListener.request.select),
            )

            if (change is Change<DM> && additionUpdate.key == change.key && additionUpdate.version == change.version) {
                // The original delta still describes this exact row revision.
                change.createChangeUpdate(
                    updateListener.request.select,
                    orderChanged = originalIndex != insertionIndex,
                    newIndex = insertionIndex,
                )?.also { sharedFlow.send(it) }
            } else {
                // Even the same key can now represent a newer snapshot (for example,
                // a queued soft delete followed by an undelete). Replace the client
                // image and cover its revision instead of dropping intervening edits.
                sendRemoval()
                updateListener.includeSnapshot(additionUpdate.key, additionUpdate.version)
                sharedFlow.send(selectedAddition)
            }
        } ?: sendRemoval()
    } else {
        sendRemoval()
    }
}

/** Requests next values object after last key in [currentKeys] */
private suspend fun <DM : IsRootDataModel> IsDataStore.requestNextValues(
    listener: UpdateListenerForScan<DM, *>,
    currentKeys: List<Key<DM>>,
): AdditionUpdate<DM>? {
    val request = listener.request
    if (listener.usesIndexScan) return requestNextIndexedValues(listener, currentKeys)
    if (request is ScanUpdatesRequest<DM>) {
        // History additions can be creation-time snapshots. Use the result's
        // current key order, then fetch the complete row and its own revision.
        repeat(4) {
            val nextResults = execute(
                request.dataModel.scanUpdates(
                    startKey = request.startKey,
                    select = null,
                    where = request.where,
                    filterSoftDeleted = request.filterSoftDeleted,
                    limit = request.limit,
                    includeStart = request.includeStart,
                    order = request.order,
                    fromVersion = request.fromVersion,
                    toVersion = request.toVersion,
                    maxVersions = request.maxVersions,
                    orderedKeys = currentKeys,
                )
            )
            val candidate = nextResults.updates.filterIsInstance<OrderedKeysUpdate<DM>>()
                .firstOrNull()?.keys?.firstOrNull { it !in currentKeys } ?: return null
            val values = execute(request.dataModel.get(
                candidate, select = null, where = request.where,
                filterSoftDeleted = request.filterSoftDeleted, toVersion = request.toVersion,
            )).values.singleOrNull()
            if (values != null) {
                return AdditionUpdate(
                    key = values.key,
                    version = values.lastVersion,
                    firstVersion = values.firstVersion,
                    insertionIndex = currentKeys.size,
                    values = values.values,
                    isDeleted = values.isDeleted,
                )
            }
            // The candidate disappeared between the two reads. Re-evaluate the
            // window rather than silently leaving a slot permanently empty.
            yield()
        }
        throw StorageException("Live scan refill could not obtain a stable matching record")
    }

    val continuation = (request as? ScanRequest<DM>)?.resolveCursor()
    val nextResults = if (currentKeys.isEmpty()) {
        execute(
            request.dataModel.scan(
                startKey = continuation?.key?.let { Key(it.bytes) } ?: request.startKey,
                select = null,
                where = request.where,
                order = request.order,
                limit = 1u,
                includeStart = continuation == null && request.includeStart,
                toVersion = request.toVersion,
                filterSoftDeleted = request.filterSoftDeleted,
                allowTableScan = request.allowTableScan
            )
        )
    } else {
        execute(
            request.dataModel.scan(
                startKey = currentKeys.last(),
                select = null,
                where = request.where,
                order = request.order,
                limit = 1u,
                includeStart = false,
                toVersion = request.toVersion,
                filterSoftDeleted = request.filterSoftDeleted,
                allowTableScan = request.allowTableScan
            )
        )
    }

    return nextResults.values.firstOrNull { it.key !in currentKeys }?.let { nextValues ->
        AdditionUpdate(
            key = nextValues.key,
            version = nextValues.lastVersion,
            firstVersion = nextValues.firstVersion,
            insertionIndex = currentKeys.size,
            values = nextValues.values,
            isDeleted = nextValues.isDeleted
        )
    }
}

/** Select a refill against the listener's frozen index boundary, independently of mutable start records. */
private suspend fun <DM : IsRootDataModel> IsDataStore.requestNextIndexedValues(
    listener: UpdateListenerForScan<DM, *>,
    currentKeys: List<Key<DM>>,
): AdditionUpdate<DM>? {
    val request = listener.request
    fun scanPage(cursor: ScanCursor? = null) = request.dataModel.scan(
        cursor = cursor,
        select = null,
        where = request.where,
        order = request.order,
        limit = 256u,
        toVersion = request.toVersion,
        filterSoftDeleted = request.filterSoftDeleted,
        allowTableScan = request.allowTableScan,
    )
    val fullScan = scanPage()
    // The public projection participates in the cursor fingerprint. Validate it
    // against the original query, then retain its boundary for our full-value reads.
    val originalContinuation = (request as? ScanRequest<DM>)?.resolveCursor()
    var cursor = originalContinuation?.let {
        fullScan.createCursor(Key(it.key.bytes), it.orderKey?.bytes)
    }
    val visibleKeys = currentKeys.toHashSet()
    var best: ValuesWithMetaData<DM>? = null
    var bestIndexKey: ByteArray? = null
    while (true) {
        val pageRequest = scanPage(cursor)
        val page = execute(pageRequest)
        for (value in page.values) {
            if (value.key in visibleKeys) continue
            val indexKey = listener.resolveIndexKey(value.values, value.key.bytes) ?: continue
            val previous = bestIndexKey
            if (previous == null || listener.compareIndexKeys(indexKey, previous) < 0) {
                best = value
                bestIndexKey = indexKey
            }
        }
        val nextCursor = page.nextCursor ?: break
        if (nextCursor == cursor) throw StorageException("Index refill scan did not advance its cursor")
        // A record can straddle the original boundary with multiple entries. Its
        // physical canonical entry may precede the entry visible to this listener.
        // Stop only once later physical entries cannot improve the best candidate.
        val physicalKey = scanPage(nextCursor).resolveCursor()?.orderKey?.bytes
        val bestKey = bestIndexKey
        if (physicalKey != null && bestKey != null && listener.compareIndexKeys(physicalKey, bestKey) >= 0) break
        cursor = nextCursor
    }
    return best?.let {
        AdditionUpdate(
            key = it.key,
            version = it.lastVersion,
            firstVersion = it.firstVersion,
            insertionIndex = currentKeys.size,
            values = it.values,
            isDeleted = it.isDeleted,
        )
    }
}

/** Filters a list of changes to only have changes to properties defined in [select] */
private fun List<IsChange>.filterWithSelect(
    select: RootPropRefGraph<out IsRootDataModel>?,
    changeProcessor: ((IsChange) -> Unit)? = null
): List<IsChange> {
    if (select == null) {
        // process all changes
        changeProcessor?.let(this::forEach)
        return this
    }
    return this.mapNotNull { change: IsChange ->
        changeProcessor?.invoke(change)
        change.filterWithSelect(select)
    }
}
