package com.devxrohan.smusic

import com.devxrohan.smusic.ai.AiTextProtocol
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AiTextProtocolTest {
    @Test fun requestPreservesUnicodeQuotesAndLineBreaks() {
        val prompt = "A \"night\" mix\nहिंदी 🎵"
        val request = Json.parseToJsonElement(AiTextProtocol.request("test-model", prompt, 120)).jsonObject
        assertEquals(prompt, request["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals(120, request["max_tokens"]!!.jsonPrimitive.int)
    }

    @Test fun readsOpenAiTextResponses() {
        assertEquals("A listening insight", AiTextProtocol.message(
            """{"choices":[{"message":{"content":" A listening insight "}}]}""", false))
    }

    @Test fun readsAnthropicTextBlocks() {
        assertEquals("First\nSecond", AiTextProtocol.message(
            """{"content":[{"type":"text","text":"First"},{"type":"text","text":"Second"}]}""", true))
    }

    @Test fun readsOpenAiContentPartResponses() {
        assertEquals("Part one\nPart two", AiTextProtocol.message(
            """{"choices":[{"message":{"content":[{"type":"text","text":"Part one"},{"type":"text","text":"Part two"}]}}]}""", false))
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsEmptyResponsesInsteadOfReportingSuccess() {
        AiTextProtocol.message("""{"choices":[]}""", false)
    }

    @Test fun acceptsFencedSongListsAndRejectsBlankOrDuplicateTracks() {
        val songs = AiTextProtocol.songs("```json\n" +
            """[{"title":"Song","artist":"Artist"},{"title":"song","artist":"artist"},{"title":"","artist":"Nobody"},{"title":"Another"}]""" + "\n```")
        assertEquals(listOf("Song", "Another"), songs.map { it.title })
    }
}
