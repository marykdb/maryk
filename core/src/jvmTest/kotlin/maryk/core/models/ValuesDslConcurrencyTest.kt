package maryk.core.models

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import maryk.test.models.TestMarykModel
import kotlin.test.Test
import kotlin.test.assertEquals

class ValuesDslConcurrencyTest {
    @Test
    fun concurrentBuildersOwnSeparateFrames() {
        val workers = Executors.newFixedThreadPool(2)
        val firstEntered = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val firstFinished = CountDownLatch(1)
        try {
            val first = workers.submit(Callable {
                TestMarykModel.create(setDefaults = false) {
                    firstEntered.countDown()
                    check(secondEntered.await(5, TimeUnit.SECONDS))
                    string with "haha-first"
                    uint with 1u
                }.also { firstFinished.countDown() }
            })
            check(firstEntered.await(5, TimeUnit.SECONDS))
            val second = workers.submit(Callable {
                TestMarykModel.create {
                    secondEntered.countDown()
                    check(firstFinished.await(5, TimeUnit.SECONDS))
                    string with "haha-second"
                    uint with 2u
                }
            })
            val a = first.get(10, TimeUnit.SECONDS)
            val b = second.get(10, TimeUnit.SECONDS)
            assertEquals("haha-first", a { string })
            assertEquals(1u, a { uint })
            assertEquals(null, a.values[TestMarykModel.enum.index])
            assertEquals("haha-second", b { string })
            assertEquals(2u, b { uint })
        } finally { workers.shutdownNow() }
    }
}
