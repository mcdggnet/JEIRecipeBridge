package com.mrbysco.jeicompat.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(
		id = "jei-recipe-bridge",
		name = "JEI Recipe Bridge",
		version = "1.0.0",
		authors = {"Mrbysco"},
		description = "Forwards JEI recipe sync requests to backend servers for Fabric/NeoForge clients"
)
public class JEIRecipeBridgeVelocityPlugin {

	/**
	 * Channel used to notify the Paper backend which mod type the client is using.
	 * Message format: single byte — 0x01 = Fabric, 0x02 = NeoForge.
	 */
	static final MinecraftChannelIdentifier SYNC_CHANNEL =
			MinecraftChannelIdentifier.create("jeibridge", "sync");

	private static final byte MOD_TYPE_FABRIC = 0x01;
	private static final byte MOD_TYPE_NEOFORGE = 0x02;

	private final ProxyServer server;
	private final Logger logger;

	/** Tracks players for whom we already sent a sync request this session. */
	private final Set<UUID> syncedPlayers = ConcurrentHashMap.newKeySet();

	@Inject
	public JEIRecipeBridgeVelocityPlugin(ProxyServer server, Logger logger) {
		this.server = server;
		this.logger = logger;
	}

	@Subscribe
	public void onProxyInitialize(ProxyInitializeEvent event) {
		logger.info("JEI Recipe Bridge Velocity plugin enabled");
	}

	@Subscribe
	public void onServerConnected(ServerConnectedEvent event) {
		Player player = event.getPlayer();

		// Only trigger once per login session to avoid double-sync when
		// the player switches between backend servers.
		if (!syncedPlayers.add(player.getUniqueId())) {
			return;
		}

		String brand = player.getClientBrand();
		if (brand == null) {
			syncedPlayers.remove(player.getUniqueId());
			return;
		}

		String lowerBrand = brand.toLowerCase();
		final byte modType;
		if (lowerBrand.contains("fabric")) {
			modType = MOD_TYPE_FABRIC;
		} else if (lowerBrand.contains("neoforge")) {
			modType = MOD_TYPE_NEOFORGE;
		} else {
			// Vanilla or unknown client — nothing to do
			syncedPlayers.remove(player.getUniqueId());
			return;
		}

		player.getCurrentServer().ifPresent(conn -> {
			boolean sent = conn.sendPluginMessage(SYNC_CHANNEL, new byte[]{modType});
			if (!sent) {
				logger.warn("Failed to send jeibridge:sync to backend for player {}", player.getUsername());
			}
		});
	}

	@Subscribe
	public void onDisconnect(DisconnectEvent event) {
		syncedPlayers.remove(event.getPlayer().getUniqueId());
	}
}
