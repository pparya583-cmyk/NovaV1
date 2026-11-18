package id.novamusic.app.network

import org.junit.Assert.assertEquals
import org.junit.Test

class ArchiveSearchPolicyTest {
    @Test fun trimsAndCollapsesWhitespace() {
        assertEquals("ambient piano", ArchiveSearchPolicy.normalize("  ambient   piano  "))
    }

    @Test fun removesQuerySyntaxAndPunctuation() {
        assertEquals("jazz OR history", ArchiveSearchPolicy.normalize("jazz) OR * history"))
    }

    @Test fun limitsQueryLength() {
        assertEquals(100, ArchiveSearchPolicy.normalize("a".repeat(140)).length)
    }

    @Test fun punctuationOnlyQueryBecomesEmpty() {
        assertEquals("", ArchiveSearchPolicy.normalize("!!! ** ()"))
    }
}
