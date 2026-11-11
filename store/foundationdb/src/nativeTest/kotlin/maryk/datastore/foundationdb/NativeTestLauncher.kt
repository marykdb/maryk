@file:OptIn(ExperimentalNativeApi::class)

package maryk.datastore.foundationdb

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.internal.test.testLauncherEntryPoint
import kotlin.system.exitProcess
import maryk.foundationdb.FDB

/** Stop the shared client network only after every test has released its datastores. */
fun runNativeTests(args: Array<String>) {
    val exitCode = try {
        testLauncherEntryPoint(args)
    } finally {
        if (FDB.isAPIVersionSelected()) FDB.instance().shutdown()
    }
    if (exitCode != 0) exitProcess(exitCode)
}
