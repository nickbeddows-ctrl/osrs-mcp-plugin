package com.claudeosrs;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("claudeosrs")
public interface ClaudeOsrsConfig extends Config
{
    @ConfigSection(name = "Connection", description = "MCP server connection settings", position = 0)
    String connectionSection = "connection";

    @ConfigItem(keyName = "port", name = "Port",
        description = "Port the local MCP server listens on. Restart the plugin after changing.",
        section = connectionSection, position = 0)
    default int port() { return 8282; }

    @ConfigItem(keyName = "allowLan", name = "Allow LAN connections",
        description = "Bind to all network interfaces instead of localhost only. " +
                      "Lets Claude on other devices on the same network connect. " +
                      "Set an Auth Token if you enable this.",
        section = connectionSection, position = 1)
    default boolean allowLan() { return false; }

    @ConfigItem(keyName = "authToken", name = "Auth Token",
        description = "Optional Bearer token. Recommended when Allow LAN is enabled.",
        section = connectionSection, position = 2, secret = true)
    default String authToken() { return ""; }

    @ConfigSection(name = "Privacy", description = "Control what data Claude can access", position = 1)
    String privacySection = "privacy";

    @ConfigItem(keyName = "shareStats", name = "Share skill levels & XP",
        description = "Allow Claude to read your skill levels and experience",
        section = privacySection, position = 0)
    default boolean shareStats() { return true; }

    @ConfigItem(keyName = "shareEquipment", name = "Share equipped gear",
        description = "Allow Claude to see what items you have equipped",
        section = privacySection, position = 1)
    default boolean shareEquipment() { return true; }

    @ConfigItem(keyName = "shareInventory", name = "Share inventory",
        description = "Allow Claude to see your inventory contents",
        section = privacySection, position = 2)
    default boolean shareInventory() { return true; }

    @ConfigItem(keyName = "shareLocation", name = "Share location",
        description = "Allow Claude to see your current in-game location",
        section = privacySection, position = 3)
    default boolean shareLocation() { return true; }

    @ConfigItem(keyName = "shareUsername", name = "Share username",
        description = "Include your RSN in responses. Disable for privacy.",
        section = privacySection, position = 4)
    default boolean shareUsername() { return true; }
}
