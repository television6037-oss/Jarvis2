package com.jarvis.asistente.tools

import com.jarvis.asistente.brain.AssistantUi
import com.jarvis.asistente.brain.JarvisTool
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Claude no sabe la hora ni la fecha: esta herramienta se las da. */
class DateTimeTool : JarvisTool {
    override val name = "fecha_y_hora"
    override val description =
        "Devuelve la fecha, el día de la semana, la hora actual y la zona horaria del celular. " +
            "Úsala siempre que necesites saber qué día u hora es."

    override suspend fun execute(input: Map<String, Any?>, ui: AssistantUi): String {
        val now = ZonedDateTime.now()
        val format = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, h:mm a", Locale("es", "CO"))
        return "${now.format(format)} (zona horaria ${now.zone.id})"
    }
}
