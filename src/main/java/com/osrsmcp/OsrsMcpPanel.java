package com.osrsmcp;

import net.runelite.api.GameState;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

@Singleton
public class OsrsMcpPanel extends PluginPanel
{
    private final JLabel statusDot  = new JLabel("●");
    private final JLabel statusText = new JLabel("Starting...");
    private final JLabel gameState  = new JLabel("Not logged in");
    private final JLabel urlLabel   = new JLabel();
    private final JTextArea infoArea = new JTextArea();

    public OsrsMcpPanel()
    {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(12, 12, 12, 12));
        setBackground(new Color(40, 40, 40));

        JLabel title = new JLabel("OSRS MCP");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setBorder(new EmptyBorder(0, 0, 12, 0));

        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        statusRow.setOpaque(false);
        statusDot.setFont(statusDot.getFont().deriveFont(14f));
        statusDot.setForeground(Color.GRAY);
        statusText.setForeground(Color.LIGHT_GRAY);
        statusRow.add(statusDot);
        statusRow.add(statusText);

        gameState.setForeground(Color.GRAY);
        gameState.setFont(gameState.getFont().deriveFont(12f));
        gameState.setBorder(new EmptyBorder(4, 0, 0, 0));

        urlLabel.setForeground(new Color(100, 180, 255));
        urlLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        urlLabel.setBorder(new EmptyBorder(8, 0, 0, 0));

        infoArea.setEditable(false);
        infoArea.setOpaque(false);
        infoArea.setForeground(Color.GRAY);
        infoArea.setFont(infoArea.getFont().deriveFont(11f));
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);
        infoArea.setBorder(new EmptyBorder(12, 0, 0, 0));
        infoArea.setText(
            "Add to Claude Desktop config:\n\n" +
            "{\n  \"mcpServers\": {\n    \"osrs\": {\n" +
            "      \"url\": \"http://127.0.0.1:8282/mcp\",\n" +
            "      \"transport\": \"streamable-http\"\n    }\n  }\n}"
        );

        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(80, 80, 80));
        sep.setBorder(new EmptyBorder(12, 0, 12, 0));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        top.add(title);
        top.add(statusRow);
        top.add(gameState);
        top.add(urlLabel);
        top.add(sep);
        top.add(infoArea);
        add(top, BorderLayout.NORTH);
    }

    public void setStatus(boolean running, int port)
    {
        SwingUtilities.invokeLater(() ->
        {
            if (running)
            {
                statusDot.setForeground(new Color(80, 200, 120));
                statusText.setText("MCP server running");
                urlLabel.setText("http://127.0.0.1:" + port + "/mcp");
            }
            else
            {
                statusDot.setForeground(Color.GRAY);
                statusText.setText("MCP server stopped");
                urlLabel.setText("");
            }
        });
    }

    public void setError(String message)
    {
        SwingUtilities.invokeLater(() ->
        {
            statusDot.setForeground(new Color(220, 80, 80));
            statusText.setText("Error");
            urlLabel.setForeground(new Color(220, 80, 80));
            urlLabel.setText(message);
        });
    }

    public void updateGameState(GameState state)
    {
        SwingUtilities.invokeLater(() ->
        {
            switch (state)
            {
                case LOGGED_IN:
                    gameState.setForeground(new Color(80, 200, 120));
                    gameState.setText("Logged in");
                    break;
                case LOGIN_SCREEN:
                    gameState.setForeground(Color.GRAY);
                    gameState.setText("Login screen");
                    break;
                default:
                    gameState.setForeground(Color.GRAY);
                    gameState.setText(state.name());
            }
        });
    }
}
