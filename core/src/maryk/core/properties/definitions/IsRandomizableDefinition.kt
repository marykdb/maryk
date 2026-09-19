package maryk.core.properties.definitions

/** Definition which can create a value for randomized testing or generation. */
interface IsRandomizableDefinition<out T : Any> {
    fun createRandom(): T
}
