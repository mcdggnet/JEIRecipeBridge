package com.mrbysco.jeicompat;

import com.mrbysco.jeicompat.hooks.MMOItemsHook;
import com.mrbysco.jeicompat.hooks.MythicMobsHook;
import com.mrbysco.jeicompat.hooks.NexoHook;
import com.mrbysco.jeicompat.hooks.VaneHook;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class JEIRecipeBridgePlugin extends JavaPlugin {
	public static final Logger LOGGER = LoggerFactory.getLogger("JEIRecipeBridge");
	public static Plugin Plugin;
	public static ConfigManager CONFIG;
	public static RecipeHandler RECIPE_HANDLER;

	@Override
	public void onEnable() {
		Plugin = this;
		CONFIG = new ConfigManager(this);
		RECIPE_HANDLER = new RecipeHandler();

		final Server server = getServer();
		final PluginManager pm = server.getPluginManager();

		pm.registerEvents(RECIPE_HANDLER, this);

		final JEIRecipeBridgeCommand commandHandler = new JEIRecipeBridgeCommand(RECIPE_HANDLER);
		getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
			event.registrar().register(
				"jeirecipebridge",
				"JEI Recipe Bridge management commands",
				List.of("jrb"),
				new BasicCommand() {
					@Override
					public void execute(CommandSourceStack stack, String[] args) {
						commandHandler.onCommand(stack.getSender(), null, "jeirecipebridge", args);
					}

					@Override
					public Collection<String> suggest(CommandSourceStack stack, String[] args) {
						List<String> completions = commandHandler.onTabComplete(
							stack.getSender(), null, "jeirecipebridge", args);
						return completions != null ? completions : List.of();
					}
				}
			);
		});

		final Messenger messenger = server.getMessenger();
		// Register plugin channels for outgoing messages with the ids used by NeoForge and Fabric
		messenger.registerOutgoingPluginChannel(this, "neoforge:recipe_content");
		messenger.registerOutgoingPluginChannel(this, "fabric:recipe_sync");
		// Register incoming channel so the Velocity proxy plugin can trigger recipe sync
		messenger.registerIncomingPluginChannel(this, "jeibridge:sync", RECIPE_HANDLER);

		// Soft-dependency hooks — only register if the plugin is present
		if (pm.isPluginEnabled("Nexo")) {
			tryRegisterHook(pm, new NexoHook(), "Nexo", "com.nexomc.nexo.api.events.NexoItemsLoadedEvent");
		}
		if (pm.isPluginEnabled("MythicMobs")) {
			tryRegisterHook(pm, new MythicMobsHook(), "MythicMobs", "io.lumine.mythic.bukkit.events.MythicReloadedEvent");
		}
		if (pm.isPluginEnabled("vane-core")) {
			tryRegisterHook(pm, new VaneHook(), "Vane", null);
		}
		if (pm.isPluginEnabled("MMOItems")) {
			tryRegisterHook(pm, new MMOItemsHook(), "MMOItems", "net.Indyuce.mmoitems.api.event.MMOItemsReloadEvent");
		}
	}

	private void tryRegisterHook(PluginManager pm, org.bukkit.event.Listener hook, String name, String requiredClass) {
		if (requiredClass != null) {
			try {
				Class.forName(requiredClass);
			} catch (ClassNotFoundException e) {
				LOGGER.warn("{} plugin found but hook skipped — event class not available (version mismatch).", name);
				return;
			}
		}
		pm.registerEvents(hook, this);
		LOGGER.info("{} integration enabled.", name);
	}

	@Override
	public void onDisable() {
		// Plugin shutdown logic
	}
}
