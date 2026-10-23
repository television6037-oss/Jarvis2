package com.jarvis.asistente.tools

import android.content.Context
import com.jarvis.asistente.brain.JarvisTool

/** Lista de todas las herramientas que Jarvis tiene disponibles. Añade aquí las nuevas. */
object ToolRegistry {
    @Suppress("UNUSED_PARAMETER")
    fun all(context: Context): List<JarvisTool> = listOf(
        DateTimeTool(),
    )
}
