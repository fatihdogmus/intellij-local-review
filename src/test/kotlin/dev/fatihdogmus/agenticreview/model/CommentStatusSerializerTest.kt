package dev.fatihdogmus.agenticreview.model

import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CommentStatusSerializerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun serializeOpen() {
        val result = json.encodeToString(CommentStatus.serializer(), CommentStatus.OPEN)
        assertThat(result).isEqualTo("\"OPEN\"")
    }

    @Test
    fun serializeResolved() {
        val result = json.encodeToString(CommentStatus.serializer(), CommentStatus.RESOLVED)
        assertThat(result).isEqualTo("\"RESOLVED\"")
    }

    @Test
    fun deserializeOpen() {
        val result = json.decodeFromString(CommentStatus.serializer(), "\"OPEN\"")
        assertThat(result).isEqualTo(CommentStatus.OPEN)
    }

    @Test
    fun deserializeResolved() {
        val result = json.decodeFromString(CommentStatus.serializer(), "\"RESOLVED\"")
        assertThat(result).isEqualTo(CommentStatus.RESOLVED)
    }

    @Test
    fun deserializeUnknownDefaultsToOpen() {
        val result = json.decodeFromString(CommentStatus.serializer(), "\"UNKNOWN\"")
        assertThat(result).isEqualTo(CommentStatus.OPEN)
    }
}
