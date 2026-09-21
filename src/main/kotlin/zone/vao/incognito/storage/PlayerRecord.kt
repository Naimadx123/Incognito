package zone.vao.incognito.storage

import java.util.UUID

data class PlayerRecord(val id: UUID, val realName: String, val enabled: Boolean)
