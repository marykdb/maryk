package maryk.core.query.responses.statuses

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.types.Key

data class DoesNotExist<DO: Any>(
        val key: Key<DO>
) : IsChangeResponseStatus<DO>, IsDeleteResponseStatus<DO> {
    override val statusType = StatusType.DOES_NOT_EXIST

    companion object: QueryDataModel<DoesNotExist<*>>(
            definitions = listOf(
                    Def(keyDefinition, DoesNotExist<*>::key)
            )
    ) {
        override fun invoke(map: Map<Int, *>) = DoesNotExist(
                key = map[0] as Key<Any>
        )
    }
}