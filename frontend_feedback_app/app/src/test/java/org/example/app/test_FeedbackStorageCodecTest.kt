package org.example.app

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for FeedbackStorageCodec covering:
 * - encode/decode roundtrip
 * - escaping of quotes, backslashes and control characters
 * - empty and whitespace JSON handling
 * - corrupted/invalid JSON handling should not crash and should return empty list
 */
class FeedbackStorageCodecTest {

    @Test
    fun encodeDecode_roundTrip_singleItem() {
        val items = listOf(
            FeedbackItem(
                id = 12345L,
                name = "Alice",
                feedback = "Great app!"
            )
        )
        val json = FeedbackStorageCodec.encode(items)
        assertTrue(json.startsWith("[") && json.endsWith("]"))
        val decoded = FeedbackStorageCodec.decode(json)
        assertEquals(1, decoded.size)
        assertEquals(12345L, decoded[0].id)
        assertEquals("Alice", decoded[0].name)
        assertEquals("Great app!", decoded[0].feedback)
    }

    @Test
    fun encodeDecode_roundTrip_multipleItems_latestFirst_preserveOrder() {
        val items = listOf(
            FeedbackItem(3, "C", "third"),
            FeedbackItem(2, "B", "second"),
            FeedbackItem(1, "A", "first"),
        )
        val json = FeedbackStorageCodec.encode(items)
        val decoded = FeedbackStorageCodec.decode(json)
        assertEquals(3, decoded.size)
        assertEquals(3L, decoded[0].id)
        assertEquals(2L, decoded[1].id)
        assertEquals(1L, decoded[2].id)
    }

    @Test
    fun encode_escapesQuotesAndBackslashesAndControls() {
        val trickyName = "Quo\"te \\ Back\nTab\tCR\rEnd"
        val trickyFeedback = "Saying: \"Hello\\World\"\nnew line"
        val json = FeedbackStorageCodec.encode(listOf(FeedbackItem(1, trickyName, trickyFeedback)))
        // Should contain escaped sequences
        assertTrue(json.contains("\\\"")) // escaped quotes present
        assertTrue(json.contains("\\\\")) // escaped backslash present
        assertTrue(json.contains("\\n")) // newline escaped
        assertTrue(json.contains("\\t")) // tab escaped
        assertTrue(json.contains("\\r")) // carriage return escaped

        val decoded = FeedbackStorageCodec.decode(json)
        assertEquals(1, decoded.size)
        assertEquals(trickyName, decoded[0].name)
        assertEquals(trickyFeedback, decoded[0].feedback)
    }

    @Test
    fun decode_emptyAndWhitespace_returnsEmptyList() {
        val empty = FeedbackStorageCodec.decode("")
        val ws = FeedbackStorageCodec.decode("   ")
        val emptyArray = FeedbackStorageCodec.decode("[]")
        assertTrue(empty.isEmpty())
        assertTrue(ws.isEmpty())
        assertTrue(emptyArray.isEmpty())
    }

    @Test
    fun decode_corrupted_returnsEmptyList_noCrash() {
        // Missing closing brace
        val corrupted1 = "[{\"id\":1,\"name\":\"A\",\"feedback\":\"B\""
        // Random garbage
        val corrupted2 = "this-is-not-json"
        // Broken quoting
        val corrupted3 = "[{\"id\":1,\"name\":\"A\",\"feedback\":\"B}]"
        assertTrue(FeedbackStorageCodec.decode(corrupted1).isEmpty())
        assertTrue(FeedbackStorageCodec.decode(corrupted2).isEmpty())
        assertTrue(FeedbackStorageCodec.decode(corrupted3).isEmpty())
    }

    @Test
    fun decode_missingFields_skipsInvalidObjects() {
        // One valid, one invalid (missing feedback)
        val json = "[{\"id\":1,\"name\":\"A\",\"feedback\":\"ok\"},{\"id\":2,\"name\":\"B\"}]"
        val decoded = FeedbackStorageCodec.decode(json)
        assertEquals(1, decoded.size)
        assertEquals(1L, decoded[0].id)
        assertEquals("A", decoded[0].name)
        assertEquals("ok", decoded[0].feedback)
    }
}
