package com.jarvis.asistente.brain

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolResultBlockParam
import com.fasterxml.jackson.core.type.TypeReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration

/**
 * El "cerebro": manda lo que dices a Claude, ejecuta las herramientas que
 * Claude pida y devuelve la respuesta final para leerla en voz alta.
 *
 * Guarda una memoria corta de la conversación. La memoria solo crece (nunca
 * se recortan mensajes viejos, porque la API lo rechaza con el razonamiento
 * interno de los modelos nuevos); se reinicia tras unos minutos sin hablar o
 * al llegar a un límite de mensajes.
 */
class ClaudeBrain(
    private val tools: List<JarvisTool>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val history = mutableListOf<MessageParam>()
    private var lastActivity = 0L
    private var client: AnthropicClient? = null
    private var clientKey: String? = null

    fun resetConversation() {
        history.clear()
    }

    /**
     * Procesa una orden. Devuelve el texto que Jarvis debe decir.
     * Nunca lanza excepciones: los errores se devuelven como frase hablada.
     */
    suspend fun ask(userText: String, apiKey: String, model: String, ui: AssistantUi): String =
        withContext(Dispatchers.IO) {
            try {
                runTurn(userText, apiKey, model, ui)
            } catch (e: UnauthorizedException) {
                "Señor, la clave de la API no es válida. Revísela en Ajustes."
            } catch (e: PermissionDeniedException) {
                "Señor, la clave no tiene permiso para usar ese modelo."
            } catch (e: RateLimitException) {
                "Estoy recibiendo demasiadas solicitudes. Intente de nuevo en un momento."
            } catch (e: BadRequestException) {
                resetConversation()
                if (e.message?.contains("credit", ignoreCase = true) == true) {
                    "Señor, parece que no queda saldo en la cuenta de Anthropic."
                } else {
                    "Hubo un problema con la solicitud. Empecemos una conversación nueva."
                }
            } catch (e: AnthropicServiceException) {
                "El servicio de Claude respondió con un error ${e.statusCode()}. Intente de nuevo."
            } catch (e: AnthropicIoException) {
                "No logro conectarme a internet, señor."
            }
        }

    private suspend fun runTurn(userText: String, apiKey: String, model: String, ui: AssistantUi): String {
        val now = clock()
        if (now - lastActivity > IDLE_RESET_MS || history.size > MAX_HISTORY) {
            history.clear()
        }
        lastActivity = now

        val api = clientFor(apiKey)
        val turnStart = history.size
        history.add(MessageParam.ofUser(userText))
        try {
            return converse(api, model, ui, turnStart)
        } catch (e: Exception) {
            // Si algo falla a mitad de camino, deshacemos este turno para que
            // la memoria no quede con una pregunta o herramienta sin respuesta.
            while (history.size > turnStart) history.removeAt(history.size - 1)
            throw e
        }
    }

    private suspend fun converse(api: AnthropicClient, model: String, ui: AssistantUi, turnStart: Int): String {
        repeat(MAX_TOOL_ROUNDS) {
            val response = api.messages().create(buildParams(model))
            history.add(response.toParam())

            when (response.stopReason().orElse(null)) {
                StopReason.TOOL_USE -> {
                    val results = response.content().mapNotNull { it.toolUse().orElse(null) }.map { call ->
                        ContentBlockParam.ofToolResult(runTool(call.id(), call.name(), call._input(), ui))
                    }
                    history.add(MessageParam.ofUserOfContentBlockParams(results))
                }

                StopReason.PAUSE_TURN -> Unit // El servidor pide continuar: volvemos a llamar.

                StopReason.REFUSAL -> {
                    // Quitamos el intercambio rechazado para que no contamine la conversación.
                    while (history.size > turnStart) history.removeAt(history.size - 1)
                    return "Lo siento, señor, no puedo ayudarle con eso."
                }

                else -> {
                    val text = response.content()
                        .mapNotNull { it.text().orElse(null)?.text() }
                        .joinToString(" ")
                        .trim()
                    return text.ifEmpty { "Listo, señor." }
                }
            }
        }
        while (history.size > turnStart) history.removeAt(history.size - 1)
        return "Me tomó demasiados pasos resolver eso, señor. Intentemos de otra forma."
    }

    private suspend fun runTool(id: String, name: String, rawInput: JsonValue, ui: AssistantUi): ToolResultBlockParam {
        val builder = ToolResultBlockParam.builder().toolUseId(id)
        val tool = tools.firstOrNull { it.name == name }
            ?: return builder.content("Herramienta desconocida: $name").isError(true).build()
        return try {
            val input: Map<String, Any?> = rawInput.convert(object : TypeReference<Map<String, Any?>>() {}) ?: emptyMap()
            builder.content(tool.execute(input, ui)).build()
        } catch (e: Exception) {
            builder.content("Error al ejecutar $name: ${e.message ?: e.javaClass.simpleName}").isError(true).build()
        }
    }

    private fun buildParams(model: String): MessageCreateParams {
        val builder = MessageCreateParams.builder()
            .model(model)
            .maxTokens(4096L)
            .system(SYSTEM_PROMPT)
            // Esfuerzo bajo: respuestas rápidas y baratas, ideales para voz.
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
            .messages(history.toList())
        tools.forEach { builder.addTool(it.toApiTool()) }
        if (model.startsWith("claude-sonnet")) {
            // Si los filtros de seguridad rechazan algo por error, el servidor
            // reintenta con otro modelo automáticamente.
            builder.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            builder.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        }
        return builder.build()
    }

    private fun clientFor(apiKey: String): AnthropicClient {
        val existing = client
        if (existing != null && clientKey == apiKey) return existing
        existing?.close()
        return AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .timeout(Duration.ofSeconds(60))
            .maxRetries(2)
            .build()
            .also {
                client = it
                clientKey = apiKey
            }
    }

    private fun JarvisTool.toApiTool(): Tool {
        val props = Tool.InputSchema.Properties.builder()
        properties.forEach { (key, schema) -> props.putAdditionalProperty(key, JsonValue.from(schema)) }
        return Tool.builder()
            .name(name)
            .description(description)
            .inputSchema(
                Tool.InputSchema.builder()
                    .properties(props.build())
                    .required(required)
                    .build()
            )
            .build()
    }

    companion object {
        const val MODEL_HAIKU = "claude-haiku-5-5"
        const val MODEL_SONNET = "claude-sonnet-5-5"

        private const val IDLE_RESET_MS = 10 * 60 * 1000L
        private const val MAX_HISTORY = 60
        private const val MAX_TOOL_ROUNDS = 8

        private val SYSTEM_PROMPT = """
            Eres Jarvis, el asistente personal por voz de tu usuario, inspirado en el mayordomo digital de Iron Man.
            Hablas español de Colombia, con un tono elegante, cordial y con un toque de humor seco. Llamas al usuario "señor".

            Tus respuestas se leen en voz alta con un sintetizador de voz, así que:
            - Sé breve: normalmente una o dos frases. Solo te extiendes si te piden una explicación.
            - Escribe solo texto plano para hablar: sin listas, viñetas, markdown, emojis, URLs ni tablas.
            - Escribe los números y símbolos como se dirían en voz alta cuando ayude a entender.

            Tienes herramientas para actuar en el celular. Úsalas cuando la orden lo requiera; si ninguna sirve
            para lo que se pide, dilo con naturalidad en vez de inventar que lo hiciste.
            Recuerdas lo que se habló hace un momento, así que puedes resolver referencias como "lo mismo" o "a él".
        """.trimIndent()
    }
}
