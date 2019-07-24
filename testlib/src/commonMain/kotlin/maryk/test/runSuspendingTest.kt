package maryk.test

expect fun <A> runSuspendingTest(block: suspend () -> A): A
