package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryModel
import maryk.core.query.responses.statuses.StatusType.AUTH_FAIL
import maryk.core.values.SimpleObjectValues

/** Authorization fail for this action */
class AuthFail<DM : IsRootDataModel> :
    IsAddResponseStatus<DM>,
    IsChangeResponseStatus<DM>,
    IsDeleteResponseStatus<DM> {
    override val statusType = AUTH_FAIL

    override fun equals(other: Any?) = other is AuthFail<*>
    override fun hashCode() = 0
    override fun toString() = "AuthFail"

    internal companion object : SimpleQueryModel<AuthFail<*>>() {
        override fun invoke(values: SimpleObjectValues<AuthFail<*>>) =
            AuthFail<IsRootDataModel>()
    }
}
