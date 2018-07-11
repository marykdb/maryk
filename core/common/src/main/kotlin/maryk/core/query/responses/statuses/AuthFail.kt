package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleValues
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions

/** Authorization fail for this action */
class AuthFail<DM: IsRootDataModel<*>> :
    IsAddResponseStatus<DM>,
    IsChangeResponseStatus<DM>,
    IsDeleteResponseStatus<DM>
{
    override val statusType = StatusType.AUTH_FAIL

    override fun equals(other: Any?) = other is AuthFail<*>
    override fun hashCode() = 0
    override fun toString() = "AuthFail"

    internal companion object: SimpleQueryDataModel<AuthFail<*>>(
        properties = object : ObjectPropertyDefinitions<AuthFail<*>>() {}
    ) {
        override fun invoke(map: SimpleValues<AuthFail<*>>) = AuthFail<IsRootDataModel<IsPropertyDefinitions>>()
    }
}
