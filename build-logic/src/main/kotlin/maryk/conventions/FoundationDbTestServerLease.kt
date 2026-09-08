package maryk.conventions

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.Semaphore

fun resetTestResultStore(binaryResultsDirectory: Path) {
    if (!Files.exists(binaryResultsDirectory)) return
    check(binaryResultsDirectory.toFile().deleteRecursively()) {
        "Could not remove stale Gradle binary test results at $binaryResultsDirectory"
    }
}

class FoundationDbTestServerLease(private val lockFile: Path) : AutoCloseable {
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    @Synchronized
    fun acquire() {
        if (lock != null) return

        processLease.acquireUninterruptibly()
        try {
            Files.createDirectories(lockFile.parent)
            val openedChannel = FileChannel.open(lockFile, CREATE, WRITE)
            try {
                lock = openedChannel.lock()
                channel = openedChannel
            } catch (throwable: Throwable) {
                openedChannel.close()
                throw throwable
            }
        } catch (throwable: Throwable) {
            processLease.release()
            throw throwable
        }
    }

    @Synchronized
    override fun close() {
        val heldLock = lock ?: return
        lock = null
        try {
            heldLock.release()
        } finally {
            try {
                channel?.close()
            } finally {
                channel = null
                processLease.release()
            }
        }
    }

    private companion object {
        val processLease = Semaphore(1, true)
    }
}

abstract class FoundationDbTestServerLeaseService : BuildService<FoundationDbTestServerLeaseService.Parameters>, AutoCloseable {
    interface Parameters : BuildServiceParameters {
        val lockFile: RegularFileProperty
    }

    private val lease by lazy {
        FoundationDbTestServerLease(parameters.lockFile.get().asFile.toPath())
    }

    fun acquire() = lease.acquire()

    override fun close() {
        lease.close()
    }
}
