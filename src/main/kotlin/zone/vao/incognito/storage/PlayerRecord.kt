package zone.vao.incognito.storage

import java.util.UUID

data class PlayerRecord(
    val id: UUID,
    val realName: String,
    val enabled: Boolean,
    val showJoinQuit: Boolean? = null,
    val lastAlias: String? = null,
)
