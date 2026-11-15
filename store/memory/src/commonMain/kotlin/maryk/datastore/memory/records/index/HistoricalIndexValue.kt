package maryk.datastore.memory.records.index

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.datastore.memory.records.DataRecord
import maryk.datastore.memory.records.lastVersionIndex

/**  index with historical [records] for [value] */
internal class HistoricalIndexValue<DM : IsRootDataModel, T : Any>(
    override val value: T,
    val records: MutableList<IsRecordAtVersion<DM>>,
    orderedByVersion: Boolean = records.indices.all { it == 0 || records[it - 1].version <= records[it].version },
) : IsIndexItem<DM, T> {
    var orderedByVersion = orderedByVersion
        private set
    private var orderedRecords: MutableList<IsRecordAtVersion<DM>>? = null
    private var unorderedLookups = 0

    override val record get() = records.last().record
    override val version get() = records.last().version

    /** Keep write-only replay cheap; build the ordered read view only when it is needed. */
    val historyByVersion: List<IsRecordAtVersion<DM>>
        get() = if (orderedByVersion) records else orderedRecords ?: buildOrderedHistory().also { orderedRecords = it }

    fun addRecord(entry: IsRecordAtVersion<DM>, releasedRecord: DataRecord<DM>? = null): Boolean {
        if (records.isNotEmpty() && records.last().version > entry.version) orderedByVersion = false
        val added = records.add(entry)
        orderedRecords?.let { insert(it, withOwner(entry, releasedRecord)) }
        return added
    }

    private fun insert(history: MutableList<IsRecordAtVersion<DM>>, entry: IsRecordAtVersion<DM>) {
        if (history.isEmpty() || history.last().version <= entry.version) {
            history.add(resolve(entry, history.lastOrNull()?.record))
            return
        }
        // Insert after equal versions to retain last-write ordering within a version.
        val position = history.lastVersionIndex(entry.version) { it.version } + 1
        history.add(position, resolve(entry, history.getOrNull(position - 1)?.record))
        var previous = history[position].record
        for (index in position + 1 until history.size) {
            val old = history[index]
            val resolved = resolve(old, previous)
            if (resolved === old) break
            history[index] = resolved
            previous = resolved.record
        }
    }

    override fun recordAtVersion(version: HLC): DataRecord<DM>? {
        if (orderedByVersion) return records.getOrNull(records.lastVersionIndex(version) { it.version })?.record
        // One-shot reads should not sort and retain a second representation.
        // Repeated reads amortize preparation and keep the binary-search path.
        if (orderedRecords == null && ++unorderedLookups <= 8) return findUnordered(version)
        return historyByVersion.let { it.getOrNull(it.lastVersionIndex(version) { entry -> entry.version })?.record }
    }

    private fun findUnordered(version: HLC): DataRecord<DM>? {
        var claimIndex = -1
        var hasRelease = false
        for (index in records.indices) {
            val entry = records[index]
            if (entry.version > version) continue
            if (entry.record == null || entry.releasedOwner != null) {
                hasRelease = true
            } else if (claimIndex < 0 || entry.version >= records[claimIndex].version) {
                claimIndex = index
            }
        }
        if (claimIndex < 0) return null
        val claim = records[claimIndex]
        val owner = claim.record ?: return null
        if (!hasRelease) return owner
        var previous: DataRecord<DM>? = null
        for (index in records.indices) {
            val entry = records[index]
            if (entry.record == null || entry.releasedOwner != null) {
                val releasesOwner = entry.releasedOwner?.matches(owner)
                    ?: (previous?.key == owner.key && previous.firstVersion == owner.firstVersion)
                if (releasesOwner && entry.version <= version &&
                    (entry.version > claim.version || entry.version == claim.version && index > claimIndex)) return null
            }
            previous = entry.record
        }
        return owner
    }

    private fun withOwner(entry: IsRecordAtVersion<DM>, released: DataRecord<DM>?) =
        if (released == null) entry else OwnedIndexRelease(entry.record, entry.version, IndexOwner(released))

    private fun buildOrderedHistory(): MutableList<IsRecordAtVersion<DM>> {
        val history = ArrayList<IsRecordAtVersion<DM>>(records.size)
        // Raw entries retain applied order, so a release follows the owner it releases.
        var previous: DataRecord<DM>? = null
        for (entry in records) {
            history.add(if (entry.releasedOwner == null && entry.record == null && previous != null) {
                PreparedIndexRelease(null, entry.version, IndexOwner(previous))
            } else entry)
            previous = entry.record
        }
        history.sortWith { first, second -> first.version.compareTo(second.version) }
        previous = null
        for (index in history.indices) {
            val entry = history[index]
            // These wrappers belong only to this unpublished view. Resolve once,
            // avoiding a second wrapper when another owner has since claimed it.
            val resolved = if (entry is PreparedIndexRelease<DM>) {
                entry.record = if (entry.releasedOwner.matches(previous)) null else previous
                entry
            } else resolve(entry, previous)
            history[index] = resolved
            previous = resolved.record
        }
        return history
    }

    private fun resolve(entry: IsRecordAtVersion<DM>, previous: DataRecord<DM>?): IsRecordAtVersion<DM> {
        val releasedOwner = entry.releasedOwner ?: return entry
        val owner = if (releasedOwner.matches(previous)) null else previous
        return if (owner === entry.record) entry else OwnedIndexRelease(owner, entry.version, releasedOwner)
    }

    fun forgetRecord(erased: DataRecord<DM>) {
        // Remember releases before removing their claims from the raw arrival history.
        val owner = IndexOwner(erased)
        var previous: DataRecord<DM>? = null
        for (index in records.indices) {
            val entry = records[index]
            if (entry.record == null && owner.matches(previous) && entry.releasedOwner == null) {
                records[index] = withOwner(entry, erased)
            }
            previous = entry.record
        }
        records.removeAll { owner.matches(it.record) }
        orderedRecords = null
        unorderedLookups = 0
    }
}

/** Mutable only while normalizing an unpublished view. Later edits replace entries. */
private class PreparedIndexRelease<DM : IsRootDataModel>(
    override var record: DataRecord<DM>?,
    override val version: HLC,
    override val releasedOwner: IndexOwner<DM>,
) : IsRecordAtVersion<DM>
