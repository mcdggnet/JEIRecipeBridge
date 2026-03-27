package com.mrbysco.jeicompat.hooks;

import com.mrbysco.jeicompat.JEIRecipeBridgePlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * Resyncs JEI recipe data to all online Fabric/NeoForge clients after a
 * {@code /vane reload} command is executed. Vane registers its recipes via
 * Bukkit's RecipeManager, so they appear in the standard sync payload —
 * this hook refreshes that sync when vane re-registers them on reload.
 *
 * <p>Vane does not dispatch a public Bukkit reload event, so we intercept
 * the command and schedule a resync 2 ticks later to let vane finish
 * re-registering its recipes before we capture the updated RecipeMap.</p>
 */
public class VaneHook implements Listener {

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
		if (isVaneReload(event.getMessage().substring(1))) {
			scheduleResync();
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onServerCommand(ServerCommandEvent event) {
		if (isVaneReload(event.getCommand())) {
			scheduleResync();
		}
	}

	private static boolean isVaneReload(String command) {
		String lower = command.trim().toLowerCase();
		// Match "/vane reload" and "/vane reload <module>" (with optional namespace prefix)
		return lower.startsWith("vane reload") || lower.startsWith("vane:vane reload");
	}

	private static void scheduleResync() {
		// Delay 2 ticks so vane finishes re-registering its recipes first
		Bukkit.getScheduler().runTaskLater(JEIRecipeBridgePlugin.Plugin, () -> {
			int count = Bukkit.getOnlinePlayers().size();
			if (count == 0) return;
			JEIRecipeBridgePlugin.LOGGER.info("Vane reloaded — resyncing recipes for {} online player(s).", count);
			Bukkit.getOnlinePlayers().forEach(p -> JEIRecipeBridgePlugin.RECIPE_HANDLER.resync(p));
		}, 2L);
	}
}
