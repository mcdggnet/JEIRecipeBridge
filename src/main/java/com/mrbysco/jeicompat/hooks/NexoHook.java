package com.mrbysco.jeicompat.hooks;

import com.mrbysco.jeicompat.JEIRecipeBridgePlugin;
import com.nexomc.nexo.api.events.NexoItemsLoadedEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Listens for Nexo item/recipe reloads and resyncs JEI recipe data to all
 * online Fabric/NeoForge clients. Nexo registers its crafting recipes with the
 * vanilla RecipeManager, so they are already included in the standard sync —
 * this hook just ensures the sync is refreshed whenever Nexo reloads.
 */
public class NexoHook implements Listener {

	@EventHandler
	public void onNexoItemsLoaded(NexoItemsLoadedEvent event) {
		int count = Bukkit.getOnlinePlayers().size();
		if (count == 0) return;
		JEIRecipeBridgePlugin.LOGGER.info("Nexo (re)loaded — resyncing recipes for {} online player(s).", count);
		Bukkit.getOnlinePlayers().forEach(p -> JEIRecipeBridgePlugin.RECIPE_HANDLER.resync(p));
	}
}
