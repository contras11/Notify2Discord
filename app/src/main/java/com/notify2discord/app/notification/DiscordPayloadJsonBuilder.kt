package com.notify2discord.app.notification

import com.notify2discord.app.notification.model.DiscordEmbedPayload
import com.notify2discord.app.notification.model.MessageRenderResult
import org.json.JSONArray
import org.json.JSONObject

object DiscordPayloadJsonBuilder {
    fun build(result: MessageRenderResult): String {
        val root = JSONObject()
        root.put("content", result.content)

        if (result.embeds.isNotEmpty()) {
            val embeds = JSONArray()
            result.embeds.forEach { embed ->
                embeds.put(embedToJson(embed))
            }
            root.put("embeds", embeds)
        }

        return root.toString()
    }

    private fun embedToJson(embed: DiscordEmbedPayload): JSONObject {
        val fields = JSONArray()
        embed.fields.forEach { field ->
            fields.put(
                JSONObject()
                    .put("name", field.name)
                    .put("value", field.value)
                    .put("inline", field.inline)
            )
        }

        return JSONObject()
            .put("title", embed.title)
            .put("description", embed.description)
            .put("color", embed.color)
            .put("fields", fields)
            .put(
                "footer",
                JSONObject().put("text", embed.footerText)
            )
    }
}
