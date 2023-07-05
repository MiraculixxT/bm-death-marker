package de.miraculixx.bmdm

import de.miraculixx.bmdm.utils.YMLConfig
import de.miraculixx.bmdm.utils.cmp
import de.miraculixx.bmdm.utils.enumOf
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.client.telemetry.TelemetryProperty.GameMode
import java.io.File


class Config {
    private val config = YMLConfig(javaClass.getResourceAsStream("/config.yml"), "config.yml", File("config/bm-death-marker.yml"))
    var markerSetName: String
    var toggleable: Boolean
    var defaultHidden: Boolean
    var expireTimeInMinutes: Long
    var hiddenGameModes: List<GameMode>

    init {
        //Load config values into variables
        markerSetName = config.getString("MarkerSetName")
        toggleable = config.getBoolean("Toggleable")
        defaultHidden = config.getBoolean("DefaultHidden")
        expireTimeInMinutes = config.getLong("ExpireTimeInMinutes")
        hiddenGameModes = parseGameModes(config.getStringList("HiddenGameModes"))
    }

    private fun parseGameModes(hiddenGameModesStrings: List<String>): List<GameMode> {
        val gameModes = ArrayList<GameMode>()
        hiddenGameModesStrings.forEach { gm ->
            enumOf<GameMode>(gm) ?: consoleAudience.sendMessage(cmp("Invalid Game Mode: $gm", NamedTextColor.RED))
        }
        return gameModes
    }
}