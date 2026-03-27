package com.mrbysco.jeicompat;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ConfigManager {

	private final JEIRecipeBridgePlugin plugin;
	private Set<String> blacklistedRecipes = new HashSet<>();

	public ConfigManager(JEIRecipeBridgePlugin plugin) {
		this.plugin = plugin;
		reload();
	}

	public void reload() {
		plugin.saveDefaultConfig();
		plugin.reloadConfig();
		blacklistedRecipes = new HashSet<>(plugin.getConfig().getStringList("blacklisted_recipes"));
		JEIRecipeBridgePlugin.LOGGER.info("Loaded {} blacklisted recipe(s).", blacklistedRecipes.size());
	}

	public boolean isBlacklisted(String recipeId) {
		return blacklistedRecipes.contains(recipeId);
	}

	public Set<String> getBlacklistedRecipes() {
		return Collections.unmodifiableSet(blacklistedRecipes);
	}
}
