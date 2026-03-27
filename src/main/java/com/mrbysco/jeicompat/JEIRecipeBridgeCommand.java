package com.mrbysco.jeicompat;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public class JEIRecipeBridgeCommand implements CommandExecutor, TabCompleter {

	private final RecipeHandler recipeHandler;

	public JEIRecipeBridgeCommand(RecipeHandler recipeHandler) {
		this.recipeHandler = recipeHandler;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 0) {
			sender.sendMessage("§6JEI Recipe Bridge §7— Usage: §f/" + label + " <reload|resend <player>>");
			return true;
		}

		switch (args[0].toLowerCase(Locale.ROOT)) {
			case "reload" -> {
				if (!sender.hasPermission("jeirecipebridge.reload")) {
					sender.sendMessage("§cYou don't have permission to do that.");
					return true;
				}
				JEIRecipeBridgePlugin.CONFIG.reload();
				sender.sendMessage("§aJEI Recipe Bridge config reloaded.");
			}
			case "resend" -> {
				if (!sender.hasPermission("jeirecipebridge.resend")) {
					sender.sendMessage("§cYou don't have permission to do that.");
					return true;
				}
				if (args.length < 2) {
					sender.sendMessage("§cUsage: §f/" + label + " resend <player>");
					return true;
				}
				Player target = Bukkit.getPlayerExact(args[1]);
				if (target == null) {
					sender.sendMessage("§cPlayer not found: §f" + args[1]);
					return true;
				}
				recipeHandler.resync(target);
				sender.sendMessage("§aResent recipes to §f" + target.getName() + "§a.");
			}
			default -> sender.sendMessage("§cUnknown subcommand. Usage: §f/" + label + " <reload|resend <player>>");
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 1) {
			return List.of("reload", "resend").stream()
					.filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
					.toList();
		}
		if (args.length == 2 && args[0].equalsIgnoreCase("resend")) {
			return Bukkit.getOnlinePlayers().stream()
					.map(Player::getName)
					.filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
					.toList();
		}
		return List.of();
	}
}
