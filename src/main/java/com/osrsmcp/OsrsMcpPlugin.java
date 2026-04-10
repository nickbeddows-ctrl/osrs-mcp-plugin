package com.osrsmcp;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
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
    @Inject private ConfigManager configManager;

    private NavigationButton navButton;

    @Override
    protected void startUp() throws Exception
    {
        panel.setRestartCallback(this::restartServer);
        panel.setRelayKeyService(relayKeyService);
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
            String lanIp = (mode == ConnectionMode.LAN) ? getLanIp() : null;
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
