package org.organicprogramming.holons

import org.yaml.snakeyaml.Yaml
import java.io.File

/** Parsed holon identity. */
data class HolonIdentity(
    val uuid: String = "",
    val givenName: String = "",
    val familyName: String = "",
    val motto: String = "",
    val composer: String = "",
    val clade: String = "",
    val status: String = "",
    val born: String = "",
    val lang: String = "",
    val parents: List<String> = emptyList(),
    val reproduction: String = "",
    val generatedBy: String = "",
    val protoStatus: String = "",
    val aliases: List<String> = emptyList(),
)

/** Parse a holon.yaml file. */
object Identity {
    @Suppress("UNCHECKED_CAST")
    fun parseHolon(path: String): HolonIdentity {
        val text = File(path).readText()
        val loaded = Yaml().load<Any?>(text)
        require(loaded is Map<*, *>) { "$path: holon.yaml must be a YAML mapping" }
        val data = loaded as Map<String, Any?>

        return HolonIdentity(
            uuid = data["uuid"]?.toString() ?: "",
            givenName = data["given_name"]?.toString() ?: "",
            familyName = data["family_name"]?.toString() ?: "",
            motto = data["motto"]?.toString() ?: "",
            composer = data["composer"]?.toString() ?: "",
            clade = data["clade"]?.toString() ?: "",
            status = data["status"]?.toString() ?: "",
            born = data["born"]?.toString() ?: "",
            lang = data["lang"]?.toString() ?: "",
            parents = (data["parents"] as? List<String>) ?: emptyList(),
            reproduction = data["reproduction"]?.toString() ?: "",
            generatedBy = data["generated_by"]?.toString() ?: "",
            protoStatus = data["proto_status"]?.toString() ?: "",
            aliases = (data["aliases"] as? List<String>) ?: emptyList(),
        )
    }
}
