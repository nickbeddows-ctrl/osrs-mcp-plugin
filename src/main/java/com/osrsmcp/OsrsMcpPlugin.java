package com.osrsmcp;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.InventoryID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

@Slf4j
@PluginDescriptor(
    name = "OSRS MCP",
    description = "Exposes RuneLite data via a local MCP server for AI-assisted in-game advice.",
    tags = {"claude", "ai", "stats", "helper", "assistant", "mcp"}
)
public class OsrsMcpPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private ClientToolbar clientToolbar;
    @Inject private OsrsMcpConfig config;
    @Inject private McpServer mcpServer;
    @Inject private OsrsMcpPanel panel;
    @Inject private RelayService relayService;
    @Inject private RelayKeyService relayKeyService;
    @Inject private TailscaleService tailscaleService;
    @Inject private ConfigManager configManager;
    @Inject private PlayerDataService playerDataService;
    @Inject private CacheWriter cacheWriter;
    @Inject private EquipmentStatsService equipmentStatsService;

    private NavigationButton navButton;

    @Override
    protected void startUp() throws Exception
    {
        panel.setRestartCallback(this::restartServer);
        playerDataService.loadPersistedItems();
        cacheWriter.init();
        panel.setRelayKeyService(relayKeyService);
        panel.setTailscaleService(tailscaleService);
        panel.setConfigManager(configManager);
        startServer();

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
        navButton = NavigationButton.builder()
            .tooltip("OSRS MCP")
            .icon(icon)
            .priority(10)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown() throws Exception
    {
        stopServer();
        clientToolbar.removeNavigation(navButton);
    }

    private void startServer()
    {
        ConnectionMode mode = config.connectionMode();

        try
        {
            mcpServer.start(config.port());
            String lanIp = null;
            if (mode == ConnectionMode.LAN)
                lanIp = getLanIp();
            else if (mode == ConnectionMode.TAILSCALE)
                lanIp = tailscaleService.getTailscaleIp();
            panel.setServerRunning(true, config.port(), mode, lanIp);
        }
        catch (IOException e)
        {
            log.error("OSRS MCP: Failed to start on port {}", config.port(), e);
            panel.setError("Port " + config.port() + " is in use. Change it in settings.");
            return;
        }

        if (mode == ConnectionMode.CLOUD_RELAY)
        {
            panel.setRelayStatus(OsrsMcpPanel.RelayStatus.CONNECTING, null);
            relayService.start(
                config.port(),
                url  -> panel.setRelayStatus(OsrsMcpPanel.RelayStatus.ACTIVE, url),
                err  -> panel.setRelayStatus(OsrsMcpPanel.RelayStatus.ERROR, err),
                regUrl -> panel.setRelayStatus(OsrsMcpPanel.RelayStatus.NEEDS_REGISTRATION, regUrl)
            );
        }
    }

    private void stopServer()
    {
        relayService.stop();
        mcpServer.stop();
        panel.setServerRunning(false, 0, ConnectionMode.LOCAL, null);
        panel.setRelayStatus(OsrsMcpPanel.RelayStatus.OFF, null);
    }

    private void restartServer()
    {
        log.info("OSRS MCP: Restarting server...");
        stopServer();
        try { Thread.sleep(300); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        startServer();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        panel.updateGameState(event.getGameState());
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        // Debounce -- write character cache once after a burst of stat changes
        javax.swing.SwingUtilities.invokeLater(() -> writeCharacterCache());
    }

    private void writeCharacterCache()
    {
        if (client.getGameState() != net.runelite.api.GameState.LOGGED_IN) return;
        net.runelite.api.Player player = client.getLocalPlayer();
        if (player == null) return;
        java.util.Map<String, Integer> skills = new java.util.LinkedHashMap<>();
        for (net.runelite.api.Skill skill : net.runelite.api.Skill.values())
            if (skill != net.runelite.api.Skill.OVERALL)
                skills.put(skill.getName(), client.getRealSkillLevel(skill));
        net.runelite.api.coords.WorldPoint wp = player.getWorldLocation();
        String loc = wp != null ? wp.getX() + ", " + wp.getY() : null;
        String username = config.shareUsername() ? player.getName() : "hidden";
        cacheWriter.writeCharacter(username, player.getCombatLevel(), skills, loc);
    }

    @Subscribe
    public void onItemContainerChanged2(ItemContainerChanged event)
    {
        playerDataService.onBankChanged(event);
        int id = event.getContainerId();
        if (id == InventoryID.BANK.getId())
        {
            net.runelite.api.Item[] items = event.getItemContainer().getItems();
            cacheWriter.writeBank(items);
        }
        else if (id == InventoryID.SEED_VAULT.getId())
        {
            cacheWriter.writeSeedVault(event.getItemContainer().getItems());
        }
        else if (id == InventoryID.EQUIPMENT.getId())
        {
            // Write equipment cache
            java.util.Map<String, String> slotToItem = new java.util.LinkedHashMap<>();
            String[] slotNames = {"head","cape","amulet","weapon","body","shield",
                                  "legs","hands","feet","ring","ammo"};
            net.runelite.api.Item[] items = event.getItemContainer().getItems();
            for (int i = 0; i < items.length && i < slotNames.length; i++)
            {
                if (items[i] == null || items[i].getId() <= 0) continue;
                String name = client.getItemDefinition(items[i].getId()).getName();
                if (name != null && !name.equals("null"))
                    slotToItem.put(slotNames[i], name);
            }
            if (!slotToItem.isEmpty())
                cacheWriter.writeEquipment(slotToItem, equipmentStatsService);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!event.getGroup().equals("osrsmcp")) return;
        if (!event.getKey().equals("connectionMode")) return;

        // Update panel sections immediately when mode changes -- no restart needed
        panel.refreshSectionsForMode(config.connectionMode());
    }

    @Provides
    OsrsMcpConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(OsrsMcpConfig.class);
    }

    private String getLanIp()
    {
        try
        {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces()))
            {
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;
                for (InetAddress addr : Collections.list(iface.getInetAddresses()))
                {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress())
                        return addr.getHostAddress();
                }
            }
        }
        catch (Exception e)
        {
            log.warn("OSRS MCP: Could not determine LAN IP", e);
        }
        return null;
    }
}
