package com.jarvis.asistente.brain

/**
 * Una acción que Jarvis puede hacer en el celular.
 *
 * Para añadir una herramienta nueva: crea una clase que implemente esta
 * interfaz y regístrala en `ToolRegistry` (ver `tools/Tools.kt`).
 */
interface JarvisTool {
    /** Nombre que ve Claude, en snake_case. */
    val name: String

    /** Explicación para Claude de cuándo y cómo usarla. */
    val description: String

    /** Propiedades del JSON Schema de la entrada, p. ej. `mapOf("texto" to mapOf("type" to "string"))`. */
    val properties: Map<String, Any>
        get() = emptyMap()

    val required: List<String>
        get() = emptyList()

    /**
     * Ejecuta la acción. Devuelve un texto que se le entrega a Claude como
     * resultado. Lanza una excepción si algo falla: Claude recibirá el error.
     */
    suspend fun execute(input: Map<String, Any?>, ui: AssistantUi): String
}

/** Lo que una herramienta puede pedirle a la interfaz (hablar, confirmar). */
interface AssistantUi {
    suspend fun say(text: String)

    /** Pregunta en voz alta y escucha "sí" o "no". */
    suspend fun confirm(question: String): Boolean
}
