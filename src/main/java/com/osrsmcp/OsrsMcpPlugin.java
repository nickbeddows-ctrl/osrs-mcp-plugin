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

@Slf4j
@PluginDescriptor(
    name = "OSRS MCP",
    description = "Connects RuneLite to Claude AI via a local MCP server for context-aware in-game assistance.",
    tags = {"claude", "ai", "stats", "helper", "assistant", "mcp"}
)
public class OsrsMcpPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private ClientToolbar clientToolbar;
    @Inject private OsrsMcpConfig config;
    @Inject private McpServer mcpServer;
    @Inject private OsrsMcpPanel panel;

    private NavigationButton navButton;

    @Override
    protected void startUp() throws Exception
    {
        try
        {
            mcpServer.start(config.port());
            panel.setStatus(true, config.port());
        }
        catch (IOException e)
        {
            log.error("OSRS MCP: Failed to start MCP server on port {}", config.port(), e);
            panel.setError("Port " + config.port() + " is in use. Change it in settings.");
        }
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
        mcpServer.stop();
        clientToolbar.removeNavigation(navButton);
        panel.setStatus(false, 0);
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
}
