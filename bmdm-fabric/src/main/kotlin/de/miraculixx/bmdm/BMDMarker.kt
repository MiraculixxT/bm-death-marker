package de.miraculixx.bmdm

import com.flowpowered.math.vector.Vector2i
import de.bluecolored.bluemap.api.BlueMapAPI
import de.bluecolored.bluemap.api.markers.MarkerSet
import de.bluecolored.bluemap.api.markers.POIMarker
import de.miraculixx.bmdm.utils.cmp
import de.miraculixx.bmdm.utils.plus
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.platform.fabric.FabricServerAudiences
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.minecraft.client.telemetry.TelemetryProperty.GameMode
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.silkmc.silk.core.task.mcCoroutineTask
import java.util.*
import java.util.regex.Pattern
import kotlin.time.Duration.Companion.minutes


lateinit var consoleAudience: Audience
val prefix = cmp("BMDeathMarker", NamedTextColor.BLUE) + cmp(" >> ", NamedTextColor.DARK_GRAY)
val markerSetID = "death-markers"
lateinit var adventure: FabricServerAudiences

class BMDMarker : ModInitializer {
    private lateinit var blueMapInstance: BlueMapAPI

    private val viewBoxRegex = "viewBox\\s*=\\s*\"\\d+\\s\\d+\\s(\\d+)\\s(\\d+)\\s*\""
    private val viewBoxPattern = Pattern.compile(viewBoxRegex)

    private val widthRegex = "width\\s*=\\s*\"(\\d+)\\s*\""
    private val widthPattern: Pattern = Pattern.compile(widthRegex)

    private val heightRegex = "height\\s*=\\s*\"(\\d+)\\s*\""
    private val heightPattern: Pattern = Pattern.compile(heightRegex)

    private var config: Config? = null

    private var iconName: String? = null
    private var anchor: Vector2i? = null


    override fun onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(ServerLifecycleEvents.ServerStarting { server: MinecraftServer? ->
            adventure = FabricServerAudiences.of(server!!)
            consoleAudience = adventure.console()

            BlueMapAPI.onEnable {
                config = Config() //reload data
                consoleAudience.sendMessage(prefix + cmp("BlueMapDeathMarkers has been enabled!"))
            }
        })

        ServerLivingEntityEvents.AFTER_DEATH.register(ServerLivingEntityEvents.AfterDeath { entity, _ ->
            val oApi: Optional<BlueMapAPI> = BlueMapAPI.getInstance()
            if (oApi.isEmpty) return@AfterDeath
            val api: BlueMapAPI = oApi.get()
            val config = this.config ?: return@AfterDeath

            // Check if player death
            if (entity !is Player) return@AfterDeath
            val player = entity as? ServerPlayer ?: return@AfterDeath

            // If this player's visibility is disabled on the map, don't add the marker.
            if (!api.webApp.getPlayerVisibility(entity.uuid)) return@AfterDeath

            // If this player's game mode is disabled on the map, don't add the marker.
            val gm = when (player.gameMode.gameModeForPlayer.id) {
                0 -> GameMode.SURVIVAL
                1 -> GameMode.CREATIVE
                2 -> GameMode.ADVENTURE
                3 -> GameMode.SPECTATOR
                else -> GameMode.SURVIVAL
            }
            if (config.hiddenGameModes.contains(gm)) return@AfterDeath


            // Get BlueMapWorld for the location
            val location = player.position()
            val blueMapWorld = api.getWorld(player.serverLevel()).orElse(null) ?: return@AfterDeath

            // Create marker-template
            // (add 1.8 to y to place the marker at the head-position of the player, like BlueMap does with its player-markers)
            val markerBuilder = POIMarker.builder().label(PlainTextComponentSerializer.plainText().serialize(player.displayName.asComponent()))
                .icon("assets/$iconName", anchor).position(location.x, location.y + 1.8, location.z)

            // Create an icon and marker for each map of this world

            // Create an icon and marker for each map of this world
            for (map in blueMapWorld.maps) {
                // Get MarkerSet (or create new MarkerSet if none is found)
                val markerSet = map.markerSets.computeIfAbsent(markerSetID) { id: String? ->
                    MarkerSet.builder()
                        .label(config.markerSetName)
                        .toggleable(config.toggleable)
                        .defaultHidden(config.defaultHidden)
                        .build()
                }
                val key = player.uuid.toString()

                // Add marker
                markerSet.put(key, markerBuilder.build())

                // Wait seconds and remove the marker (?)
                mcCoroutineTask(false, delay = config.expireTimeInMinutes.minutes) {
                    markerSet.remove(key)
                }
            }
        })
    }
}