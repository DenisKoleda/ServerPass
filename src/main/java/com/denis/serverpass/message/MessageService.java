package com.denis.serverpass.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;

public final class MessageService {
    private static final Map<String, String> FALLBACKS = Map.ofEntries(
        Map.entry("prefix", "<gray>[<gold>ServerPass</gold>]</gray> "),
        Map.entry("loginTitle", "<yellow>Введите пароль"),
        Map.entry("loginSubtitle", "<white>/login <password>"),
        Map.entry("loginPrompt", "<yellow>Введите пароль: <white>/login <password>"),
        Map.entry("actionbar", "<yellow>Введите пароль: <white>/login <password> <gray>(%seconds%s)"),
        Map.entry("success", "<green>Пароль принят"),
        Map.entry("wrongPassword", "<red>Неверный пароль"),
        Map.entry("attemptsLeft", "<red>Неверный пароль. Осталось попыток: <white>%attempts%"),
        Map.entry("timeoutKick", "<red>Время входа истекло"),
        Map.entry("attemptsKick", "<red>Слишком много неверных попыток"),
        Map.entry("notConfiguredKick", "<red>ServerPass еще не настроен"),
        Map.entry("noPermission", "<red>Нет прав для ServerPass"),
        Map.entry("passwordRequired", "<red>Укажите пароль"),
        Map.entry("passwordUpdated", "<green>Пароль обновлен. В config.yml сохранены только salt/hash."),
        Map.entry("reloaded", "<green>ServerPass перезагружен"),
        Map.entry("onlyPlayers", "<red>Эта команда доступна только игроку")
    );

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private FileConfiguration messages;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = plugin.getDataFolder().toPath().resolve("messages.yml").toFile();
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(prefixed(key, placeholders));
    }

    public void sendActionbar(Player player, String key, Map<String, String> placeholders) {
        player.sendActionBar(raw(key, placeholders));
    }

    public void showLoginTitle(Player player) {
        player.showTitle(Title.title(raw("loginTitle"), raw("loginSubtitle")));
    }

    public Component prefixed(String key, Map<String, String> placeholders) {
        return miniMessage.deserialize(applyPlaceholders(value("prefix") + value(key), placeholders));
    }

    public Component raw(String key) {
        return raw(key, Map.of());
    }

    public Component raw(String key, Map<String, String> placeholders) {
        return miniMessage.deserialize(applyPlaceholders(value(key), placeholders));
    }

    public String plain(String key) {
        return value(key);
    }

    private String value(String key) {
        String fallback = FALLBACKS.getOrDefault(key, "");
        return messages.getString(key, fallback);
    }

    private String applyPlaceholders(String text, Map<String, String> placeholders) {
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }
}
