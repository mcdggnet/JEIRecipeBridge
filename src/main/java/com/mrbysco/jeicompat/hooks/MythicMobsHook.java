package com.mrbysco.jeicompat.hooks;

import com.mrbysco.jeicompat.JEIRecipeBridgePlugin;
import io.lumine.mythic.bukkit.events.MythicReloadedEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Resyncs JEI recipe data to all online Fabric/NeoForge clients when MythicMobs
 * (and any MythicMobs add-ons, e.g. MythicCrucible) finish reloading their data.
 * MythicCrucible registers its vanilla-compatible recipes via Bukkit's RecipeManager,
 * so they are included in the standard sync payload — this hook just ensures the
 * sync is refreshed after a /mythicmobs reload.
 */
public class MythicMobsHook implements Listener {

	@EventHandler
	public void onMythicReloaded(MythicReloadedEvent event) {
		int count = Bukkit.getOnlinePlayers().size();
		if (count == 0) return;
		JEIRecipeBridgePlugin.LOGGER.info("MythicMobs (re)loaded — resyncing recipes for {} online player(s).", count);
		Bukkit.getOnlinePlayers().forEach(p -> JEIRecipeBridgePlugin.RECIPE_HANDLER.resync(p));
	}
}
