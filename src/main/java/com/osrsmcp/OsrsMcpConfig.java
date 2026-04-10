package com.osrsmcp;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("osrsmcp")
public interface OsrsMcpConfig extends Config
{
    @ConfigSection(name = "Connection", description = "MCP server connection settings", position = 0)
    String connectionSection = "connection";

    @ConfigItem(keyName = "port", name = "Port",
        description = "Port the local MCP server listens on. Click Restart server after changing.",
        section = connectionSection, position = 0)
    default int port() { return 8282; }

    @ConfigItem(keyName = "connectionMode", name = "Connection mode",
        description = "<html><b>Local</b> — same machine only (default)<br>" +
                      "<b>LAN</b> — devices on the same network/subnet<br>" +
                      "<b>Cloud relay</b> — any device on any network, uses a free SSH tunnel " +
                      "(no extra software needed, SSH is built into macOS and Windows 10+)</html>",
        section = connectionSection, position = 1)
    default ConnectionMode connectionMode() { return ConnectionMode.LOCAL; }

    @ConfigItem(keyName = "relaySubdomain", name = "Relay subdomain",
        description = "<html>Optional. Pick a unique name (e.g. <b>yourname-osrs-mcp</b>) to get a " +
                      "stable URL that never changes: <b>https://yourname-osrs-mcp.serveo.net</b><br>" +
                      "Leave blank for a random URL (changes on every restart).<br>" +
                      "Only letters, numbers and hyphens. Must be unique on serveo.net.</html>",
        section = connectionSection, position = 2)
    default String relaySubdomain() { return ""; }

    @ConfigItem(keyName = "authToken", name = "Auth Token",
        description = "Optional Bearer token. Recommended when using LAN or Cloud Relay mode.",
        section = connectionSection, position = 3, secret = true)
    default String authToken() { return ""; }

    @ConfigSection(name = "Privacy", description = "Control what data the AI can access", position = 1)
    String privacySection = "privacy";

    @ConfigItem(keyName = "shareStats", name = "Share skill levels & XP",
        description = "Allow the AI to read your skill levels and experience",
        section = privacySection, position = 0)
    default boolean shareStats() { return true; }

    @ConfigItem(keyName = "shareEquipment", name = "Share equipped gear",
        description = "Allow the AI to see what items you have equipped",
        section = privacySection, position = 1)
    default boolean shareEquipment() { return true; }

    @ConfigItem(keyName = "shareInventory", name = "Share inventory",
        description = "Allow the AI to see your inventory contents",
        section = privacySection, position = 2)
    default boolean shareInventory() { return true; }

    @ConfigItem(keyName = "shareLocation", name = "Share location",
        description = "Allow the AI to see your current in-game location",
        section = privacySection, position = 3)
    default boolean shareLocation() { return true; }

    @ConfigItem(keyName = "shareUsername", name = "Share username",
        description = "Include your RSN in responses. Disable for privacy.",
        section = privacySection, position = 4)
    default boolean shareUsername() { return true; }
}
