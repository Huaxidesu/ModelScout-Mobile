package com.modelscout.mobile.core.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 测试组 1：/models 三形态解析。 */
class ModelsParsingTest {

    @Test
    fun `parses data envelope shape and sorts by id`() {
        val raw = """
            {"object":"list","data":[
              {"id":"z-model","name":"Z"},
              {"id":"a-model","name":"A"}
            ]}
        """.trimIndent()
        val models = ModelsParser.parse(raw)
        assertEquals(listOf("a-model", "z-model"), models.map { it.id })
        assertEquals("A", models.first().displayName)
    }

    @Test
    fun `parses models envelope shape and falls back to name when id missing`() {
        val raw = """
            {"models":[{"name":"fallback-name"},{"model":"bare-model"},{"no":"identifier"}]}
        """.trimIndent()
        val models = ModelsParser.parse(raw)
        assertEquals(listOf("bare-model", "fallback-name"), models.map { it.id })
        assertEquals(2, models.size)
    }

    @Test
    fun `parses bare array shape`() {
        val raw = """[{"id":"only-one"}]"""
        val models = ModelsParser.parse(raw)
        assertEquals(1, models.size)
        assertEquals("only-one", models.first().id)
    }

    @Test
    fun `trusts openrouter style declaration fields`() {
        val raw = """
            {"data":[{
              "id":"declared-model",
              "supported_parameters":["tools","temperature","reasoning"],
              "input_modalities":["text","image"],
              "max_context_length":{"value":128000}
            }]}
        """.trimIndent()
        val model = ModelsParser.parse(raw).single()
        assertEquals(true, model.declaredTool)
        assertEquals(true, model.declaredReasoning)
        assertEquals(true, model.declaredVision)
        assertEquals(128000, model.declaredContext)
    }

    @Test
    fun `returns empty list on malformed payload`() {
        assertTrue(ModelsParser.parse("not json at all").isEmpty())
        assertTrue(ModelsParser.parse("").isEmpty())
    }

    @Test
    fun `parses nested modality schema used by real gateways`() {
        val raw = """
            {"object":"list","data":[{
              "id":"nested-model",
              "input_modalities":[
                {"type":"text","supported_inputs":{"max_context_length":{"value":262144,"unit":"token"}}},
                {"type":"image","supported_inputs":{"sources":{"type":"enum","values":["url","base64"]}}}
              ],
              "output_modalities":[{
                "type":"text",
                "max_length":{"value":262144,"unit":"token"},
                "supported_parameters":{
                  "max_tokens":{"type":"integer","min":1,"max":262144,"unit":"token"},
                  "tools":{"type":"boolean"},
                  "reasoning":{"type":"boolean"}
                }
              }]
            }]}
        """.trimIndent()
        val model = ModelsParser.parse(raw).single()
        assertEquals(true, model.declaredTool)
        assertEquals(true, model.declaredReasoning)
        assertEquals(true, model.declaredVision)
        assertEquals(262144, model.declaredContext)
        assertEquals(262144, model.declaredOutput)
    }

    @Test
    fun `treats missing image modality as explicitly unsupported vision`() {
        val raw = """
            [{"id":"text-only","input_modalities":[
              {"type":"text","supported_inputs":{"max_context_length":{"value":1048576}}}
            ]}]
        """.trimIndent()
        val model = ModelsParser.parse(raw).single()
        assertEquals(false, model.declaredVision)
        assertEquals(1048576, model.declaredContext)
    }

    @Test
    fun `handles chinese modality names`() {
        val raw = """[{"id":"cn","input_modalities":[{"type":"文本"},{"type":"图片"}]}]"""
        val model = ModelsParser.parse(raw).single()
        assertEquals(true, model.declaredVision)
    }

    @Test
    fun `declared fields stay null when gateway is silent`() {
        val model = ModelsParser.parse("""[{"id":"plain"}]""").single()
        assertNull(model.declaredTool)
        assertNull(model.declaredVision)
        assertNull(model.declaredReasoning)
    }
}
