package zone.vao.incognito.identity

import com.google.gson.JsonParser
import java.util.Base64
import java.util.UUID

object HeadProfiles {

    fun find(id: UUID?, name: String?, textures: Collection<String>, identities: Collection<Identity>): Identity? {
        identities.firstOrNull { it.id == id }?.let { return it }
        identities.firstOrNull { it.realName.equals(name, true) }?.let { return it }
        return textures.firstNotNullOfOrNull { texture ->
            runCatching {
                val data = JsonParser.parseString(String(Base64.getDecoder().decode(texture), Charsets.UTF_8)).asJsonObject
                val profileId = data.get("profileId")?.asString?.replace("-", "")
                val profileName = data.get("profileName")?.asString
                identities.firstOrNull { it.id.toString().replace("-", "").equals(profileId, true) }
                    ?: identities.firstOrNull { it.realName.equals(profileName, true) }
            }.getOrNull()
        }
    }

    fun maskedId(identity: Identity): UUID = UUID.nameUUIDFromBytes("incognito-head:${identity.alias}".toByteArray(Charsets.UTF_8))
}
