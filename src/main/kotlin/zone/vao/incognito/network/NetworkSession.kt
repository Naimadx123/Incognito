package zone.vao.incognito.network

import zone.vao.incognito.coordinate.CoordinateOffset
import java.util.UUID

data class NetworkSession(val alias: String, val maskedId: UUID, val offset: CoordinateOffset)

data class NetworkClaim(val session: NetworkSession, val continued: Boolean)
