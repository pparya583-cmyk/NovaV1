package id.novamusic.app.network

object ArchiveSearchPolicy {
    private val unsafeCharacters = Regex("[^\\p{L}\\p{N} ]")

    fun normalize(raw: String): String = raw
        .trim()
        .take(100)
        .replace(unsafeCharacters, " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
