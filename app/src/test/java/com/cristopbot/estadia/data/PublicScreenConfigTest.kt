package com.cristopbot.estadia.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PublicScreenConfigTest {
    @Test fun parsesSupportedScreenContentAndRevision() {
        val config = PublicScreenConfig.from(
            mapOf(
                "schemaVersion" to 1,
                "revision" to 7,
                "screens" to mapOf(
                    "home" to mapOf(
                        "title" to "Alozena cerca de ti",
                        "subtitle" to "Encuentra una estancia",
                        "announcement" to mapOf("title" to "Aviso", "message" to "Hay nuevas publicaciones.")
                    ),
                    "settings" to mapOf("notice" to "Actualización de la app")
                )
            )
        )

        assertEquals(7L, config.revision)
        assertEquals("Alozena cerca de ti", config.homeTitle)
        assertEquals("Encuentra una estancia", config.homeSubtitle)
        assertEquals(PublicScreenConfig.Announcement("Aviso", "Hay nuevas publicaciones."), config.homeAnnouncement)
        assertEquals("Actualización de la app", config.settingsNotice)
    }

    @Test fun defaultsMissingScreensAndOptionalText() {
        val config = PublicScreenConfig.from(mapOf("schemaVersion" to 1, "revision" to 0))

        assertNull(config.homeTitle)
        assertNull(config.homeSubtitle)
        assertNull(config.homeAnnouncement)
        assertNull(config.settingsNotice)
    }

    @Test fun rejectsUnsupportedSchemaAndOversizedScreenText() {
        assertThrows(IllegalArgumentException::class.java) {
            PublicScreenConfig.from(mapOf("schemaVersion" to 2, "revision" to 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PublicScreenConfig.from(
                mapOf(
                    "schemaVersion" to 1,
                    "revision" to 1,
                    "screens" to mapOf("home" to mapOf("title" to "x".repeat(81)))
                )
            )
        }
    }
}
