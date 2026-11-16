package com.cristopbot.estadia.data

data class PublicScreenConfig(
    val revision: Long,
    val homeTitle: String?,
    val homeSubtitle: String?,
    val homeAnnouncement: Announcement?,
    val settingsNotice: String?
) {
    data class Announcement(val title: String, val message: String)

    companion object {
        fun from(data: Map<String, Any?>): PublicScreenConfig {
            require((data["schemaVersion"] as? Number)?.toInt() == 1) {
                "La configuración remota tiene una versión de esquema no compatible"
            }
            val revision = (data["revision"] as? Number)?.toLong()
                ?: error("La configuración remota no contiene una revisión válida")
            require(revision >= 0) { "La revisión de configuración no puede ser negativa" }

            val screens = optionalMap(data, "screens")
            val home = optionalMap(screens, "home")
            val settings = optionalMap(screens, "settings")
            val announcementFields = optionalMap(home, "announcement")
            val announcement = if (announcementFields.isEmpty()) null else Announcement(
                title = requiredText(announcementFields, "title", 80),
                message = requiredText(announcementFields, "message", 240)
            )

            return PublicScreenConfig(
                revision = revision,
                homeTitle = optionalText(home, "title", 80),
                homeSubtitle = optionalText(home, "subtitle", 180),
                homeAnnouncement = announcement,
                settingsNotice = optionalText(settings, "notice", 240)
            )
        }

        private fun optionalMap(parent: Map<*, *>, key: String): Map<*, *> {
            val value = parent[key] ?: return emptyMap<Any, Any>()
            require(value is Map<*, *>) { "El campo '$key' debe ser un objeto" }
            return value
        }

        private fun requiredText(parent: Map<*, *>, key: String, maxLength: Int): String =
            optionalText(parent, key, maxLength)
                ?: error("El campo '$key' es obligatorio")

        private fun optionalText(parent: Map<*, *>, key: String, maxLength: Int): String? {
            val value = parent[key] ?: return null
            require(value is String) { "El campo '$key' debe ser texto" }
            val text = value.trim()
            require(text.length <= maxLength) { "El campo '$key' supera $maxLength caracteres" }
            return text.takeIf { it.isNotEmpty() }
        }
    }
}
