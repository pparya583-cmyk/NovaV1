package id.novamusic.app

import id.novamusic.app.network.RightsPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RightsPolicyTest {
    @Test fun acceptsCanonicalCc0ByAndBySaUrls() {
        listOf(
            "https://creativecommons.org/licenses/by/4.0/",
            "https://creativecommons.org/licenses/by-sa/3.0/",
            "https://creativecommons.org/publicdomain/zero/1.0/",
            "https://creativecommons.org/publicdomain/mark/1.0/"
        ).forEach {
            assertTrue("Expected accepted: $it", RightsPolicy.mayStream(it))
            assertTrue("Expected downloadable: $it", RightsPolicy.mayDownload(it))
        }
    }

    @Test fun rejectsNcNdUnknownAndFreeTextDeclarations() {
        listOf(
            "https://creativecommons.org/licenses/by-nc/4.0/",
            "https://creativecommons.org/licenses/by-nd/4.0/",
            "https://creativecommons.org/licenses/by-nc-sa/4.0/",
            "Public Domain", "public-domain", "All rights reserved", "", "unknown"
        ).forEach { assertBlocked(it) }
    }

    @Test fun rejectsLookalikeAndMalformedUrls() {
        listOf(
            "http://creativecommons.org/licenses/by/4.0/",
            "https://creativecommons.org.evil.example/licenses/by/4.0/",
            "https://evil.example/creativecommons.org/licenses/by/4.0/",
            "https://user@creativecommons.org/licenses/by/4.0/",
            "https://creativecommons.org:443/licenses/by/4.0/",
            "https://creativecommons.org/licenses/by/4.0/#fragment",
            "https://creativecommons.org/licenses/by/9.0/",
            "creativecommons.org/licenses/by/4.0/"
        ).forEach { assertBlocked(it) }
    }

    private fun assertBlocked(value: String) {
        assertFalse("Must be blocked: $value", RightsPolicy.mayStream(value))
        assertFalse("Must not be downloadable: $value", RightsPolicy.mayDownload(value))
    }
}
