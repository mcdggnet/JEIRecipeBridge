package com.mrbysco.jeicompat.hooks;

import com.mrbysco.jeicompat.JEIRecipeBridgePlugin;
import net.Indyuce.mmoitems.api.event.MMOItemsReloadEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Listens for MMOItems reload events and resyncs JEI recipe data to all online
 * Fabric/NeoForge clients. MMOItems registers its Bukkit-compatible recipes via
 * the vanilla RecipeManager, so they are included in the standard sync — this
 * hook refreshes that sync whenever MMOItems reloads its recipe data.
 */
public class MMOItemsHook implements Listener {

	@EventHandler
	public void onMMOItemsReload(MMOItemsReloadEvent event) {
		int count = Bukkit.getOnlinePlayers().size();
		if (count == 0) return;
		JEIRecipeBridgePlugin.LOGGER.info("MMOItems (re)loaded — resyncing recipes for {} online player(s).", count);
		Bukkit.getOnlinePlayers().forEach(p -> JEIRecipeBridgePlugin.RECIPE_HANDLER.resync(p));
	}
}
