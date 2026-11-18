package id.novamusic.app.network

import java.net.URI
import java.util.Locale

/** Conservative metadata gate. A matching URL is only a screening signal, not legal advice. */
object RightsPolicy {
    private val supportedVersions = setOf("1.0", "2.0", "2.5", "3.0", "4.0")

    private fun licensePath(value: String): String? = runCatching {
        val uri = URI(value.trim())
        if (uri.scheme?.lowercase(Locale.ROOT) != "https") return@runCatching null
        if (uri.host?.lowercase(Locale.ROOT) !in setOf("creativecommons.org", "www.creativecommons.org")) return@runCatching null
        if (uri.userInfo != null || uri.port != -1 || uri.query != null || uri.fragment != null) return@runCatching null
        uri.path.trimEnd('/').lowercase(Locale.ROOT)
    }.getOrNull()

    private fun recognizedLicense(value: String): Boolean {
        val path = licensePath(value) ?: return false
        if (path == "/publicdomain/zero/1.0" || path == "/publicdomain/mark/1.0") return true
        val parts = path.split('/').filter(String::isNotBlank)
        return parts.size == 3 && parts[0] == "licenses" && parts[1] in setOf("by", "by-sa") && parts[2] in supportedVersions
    }

    /** Only canonical HTTPS Creative Commons license URLs are accepted. Free-text labels are ambiguous. */
    fun mayStream(declaration: String): Boolean = recognizedLicense(declaration)

    /** Download uses the same conservative allow-list; item terms must still be checked by the user. */
    fun mayDownload(declaration: String): Boolean = recognizedLicense(declaration)
}
