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
                      "<b>Cloud relay</b> — any device on any network via SSH tunnel " +
                      "(no extra software, SSH is built into macOS and Windows 10+)</html>",
        section = connectionSection, position = 1)
    default ConnectionMode connectionMode() { return ConnectionMode.LOCAL; }

    @ConfigItem(keyName = "authToken", name = "Auth Token",
        description = "Optional Bearer token for extra security. Recommended with LAN or Cloud Relay.",
        section = connectionSection, position = 2, secret = true)
    default String authToken() { return ""; }

    // ── PRIVACY ───────────────────────────────────────────────────────────────

    @ConfigSection(name = "Privacy", description = "Control what data the AI can access", position = 1)
    String privacySection = "privacy";

    @ConfigItem(keyName = "shareStats", name = "Share skill levels & XP",
        section = privacySection, position = 0,
        description = "Allow the AI to read your skill levels and experience")
    default boolean shareStats() { return true; }

    @ConfigItem(keyName = "shareEquipment", name = "Share equipped gear",
        section = privacySection, position = 1,
        description = "Allow the AI to see what items you have equipped")
    default boolean shareEquipment() { return true; }

    @ConfigItem(keyName = "shareInventory", name = "Share inventory",
        section = privacySection, position = 2,
        description = "Allow the AI to see your inventory contents")
    default boolean shareInventory() { return true; }

    @ConfigItem(keyName = "shareLocation", name = "Share location",
        section = privacySection, position = 3,
        description = "Allow the AI to see your current in-game location")
    default boolean shareLocation() { return true; }

    @ConfigItem(keyName = "shareUsername", name = "Share username",
        section = privacySection, position = 4,
        description = "Include your RSN in responses. Disable for privacy.")
    default boolean shareUsername() { return true; }

    // ── CLOUD RELAY ───────────────────────────────────────────────────────────

    @ConfigSection(name = "Cloud Relay",
        description = "Only used when Connection mode is set to Cloud Relay. " +
                      "The plugin panel guides you through the full setup.",
        position = 2)
    String relaySection = "relay";

    @ConfigItem(keyName = "relaySubdomain", name = "Stable subdomain",
        description = "<html>Optional. Enter a unique name (e.g. <b>yourname-osrs-mcp</b>) to get a " +
                      "permanent URL that never changes across restarts.<br><br>" +
                      "Requires a one-time SSH key registration — use the <b>Register</b> button " +
                      "in the plugin panel to complete this.<br><br>" +
                      "Leave blank to use a random URL (changes every restart).</html>",
        section = relaySection, position = 0)
    default String relaySubdomain() { return ""; }
}
