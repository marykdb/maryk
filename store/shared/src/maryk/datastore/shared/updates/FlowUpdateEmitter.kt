package maryk.datastore.shared.updates

import maryk.core.models.IsRootDataModel

typealias FlowUpdateEmitter = suspend (Update<out IsRootDataModel>) -> Unit
