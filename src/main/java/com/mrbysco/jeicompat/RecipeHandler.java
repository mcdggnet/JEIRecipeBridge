package com.mrbysco.jeicompat;

import com.mrbysco.jeicompat.compat.fabric.FabricRecipeSyncPayload;
import com.mrbysco.jeicompat.compat.neoforge.NeoforgeRecipeSyncPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RecipeHandler implements Listener, PluginMessageListener {

	private static final byte MOD_TYPE_FABRIC = 0x01;
	private static final byte MOD_TYPE_NEOFORGE = 0x02;

	/**
	 * Tracks players who already received recipes this session.
	 * Prevents double-sending when both the Velocity plugin and the
	 * delayed-retry path would otherwise both fire.
	 */
	private final Set<UUID> syncedPlayers = ConcurrentHashMap.newKeySet();

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		final Player player = event.getPlayer();
		String brand = player.getClientBrandName();

		if (brand == null) {
			// Brand message not received yet — common when behind a Velocity proxy.
			// Schedule a short retry; the Velocity plugin will also send jeibridge:sync
			// which takes priority and cancels this retry if it arrives first.
			Bukkit.getScheduler().runTaskLater(JEIRecipeBridgePlugin.Plugin, () -> {
				if (!player.isOnline()) return;
				// If the Velocity plugin already handled sync, skip
				if (syncedPlayers.contains(player.getUniqueId())) return;
				triggerSync(player);
			}, 5L);
			return;
		}
		triggerSync(player);
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		syncedPlayers.remove(event.getPlayer().getUniqueId());
	}

	/**
	 * Receives the jeibridge:sync message sent by the Velocity proxy plugin.
	 * Message format: single byte — 0x01 = Fabric, 0x02 = NeoForge.
	 */
	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (!channel.equals("jeibridge:sync") || message.length < 1) return;

		// Mark as synced so the delayed retry in onJoin skips this player
		if (!syncedPlayers.add(player.getUniqueId())) return;

		final ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
		final MinecraftServer server = serverPlayer.level().getServer();
		final RecipeMap recipeMap = server.getRecipeManager().recipes;
		final RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());

		player.sendMessage("§6PaperMC JEI Compat: Syncing Recipes...§r");

		byte modType = message[0];
		if (modType == MOD_TYPE_FABRIC) {
			sendFabricPayload(serverPlayer, recipeMap, buffer);
		} else if (modType == MOD_TYPE_NEOFORGE) {
			sendNeoForgePayload(serverPlayer, server, recipeMap, buffer);
		}
	}

	/** Forces a fresh recipe sync for the given player, bypassing the dedup guard. */
	public void resync(Player player) {
		syncedPlayers.remove(player.getUniqueId());
		triggerSync(player);
	}

	private void triggerSync(Player originalPlayer) {
		if (!syncedPlayers.add(originalPlayer.getUniqueId())) return;

		String brand = originalPlayer.getClientBrandName();
		if (brand == null) return;

		final ServerPlayer player = ((CraftPlayer) originalPlayer).getHandle();
		final MinecraftServer server = player.level().getServer();
		final RecipeMap recipeMap = server.getRecipeManager().recipes;
		final RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());

		originalPlayer.sendMessage("§6PaperMC JEI Compat: Syncing Recipes...§r");

		if (brand.equalsIgnoreCase("fabric")) {
			sendFabricPayload(player, recipeMap, buffer);
		} else if (brand.equalsIgnoreCase("neoforge")) {
			sendNeoForgePayload(player, server, recipeMap, buffer);
		}
	}

	private static void sendNeoForgePayload(ServerPlayer player, MinecraftServer server, RecipeMap recipeMap, RegistryFriendlyByteBuf buffer) {
		final Set<String> blacklist = JEIRecipeBridgePlugin.CONFIG.getBlacklistedRecipes();
		final Set<RecipeType<?>> allRecipeTypes = Set.copyOf(BuiltInRegistries.RECIPE_TYPE.stream().toList());

		final List<RecipeHolder<?>> filteredRecipes = recipeMap.values().stream()
				.filter(h -> allRecipeTypes.contains(h.value().getType()))
				.filter(h -> !blacklist.contains(h.id().identifier().toString()))
				.toList();

		final var payload = new NeoforgeRecipeSyncPayload(allRecipeTypes, filteredRecipes);
		NeoforgeRecipeSyncPayload.STREAM_CODEC.encode(buffer, payload);

		byte[] bytes = new byte[buffer.writerIndex()];
		buffer.getBytes(0, bytes);

		sendPayload(player, Identifier.fromNamespaceAndPath("neoforge", "recipe_content"), bytes);

		player.connection.send(new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(server.registries())));
	}

	private static void sendFabricPayload(ServerPlayer player, RecipeMap recipeMap, RegistryFriendlyByteBuf buffer) {
		final Set<String> blacklist = JEIRecipeBridgePlugin.CONFIG.getBlacklistedRecipes();
		final var list = new ArrayList<FabricRecipeSyncPayload.Entry>();
		final var seen = new HashSet<RecipeSerializer<?>>();

		for (RecipeSerializer<?> serializer : BuiltInRegistries.RECIPE_SERIALIZER) {
			if (!seen.add(serializer)) continue; // skip duplicates

			List<RecipeHolder<?>> recipes = new ArrayList<>();
			for (RecipeHolder<?> holder : recipeMap.values()) {
				if (holder.value().getSerializer() != serializer) continue;
				if (blacklist.contains(holder.id().identifier().toString())) continue;
				recipes.add(holder);
			}

			if (!recipes.isEmpty()) {
				list.add(new FabricRecipeSyncPayload.Entry(recipes.get(0).value().getSerializer(), recipes));
			}
		}

		var payload = new FabricRecipeSyncPayload(list);
		FabricRecipeSyncPayload.CODEC.encode(buffer, payload);

		byte[] bytes = new byte[buffer.writerIndex()];
		buffer.getBytes(0, bytes);

		sendPayload(player, Identifier.fromNamespaceAndPath("fabric", "recipe_sync"), bytes);
	}

	private static void sendPayload(ServerPlayer player, Identifier id, byte[] bytes) {
		player.connection.send(new ClientboundCustomPayloadPacket(new DiscardedPayload(id, bytes)));
	}
}
