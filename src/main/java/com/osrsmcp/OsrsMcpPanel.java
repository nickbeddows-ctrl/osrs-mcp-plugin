package com.osrsmcp;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.api.GameState;

import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

@Slf4j
@Singleton
public class OsrsMcpPanel extends PluginPanel
{
    public enum RelayStatus { OFF, CONNECTING, ACTIVE, ERROR, NEEDS_REGISTRATION }

    private static final Color SECTION_BG = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color GREEN      = new Color(0, 180, 90);

    // Status
    private final JLabel  statusDot      = new JLabel("⬤");
    private final JLabel  statusText     = new JLabel("Starting...");
    private final JLabel  statusMode     = new JLabel();
    private final JLabel  gameStateLabel = new JLabel("Not logged in");
    private final JLabel  localUrlLabel  = new JLabel();
    private final JButton restartButton  = new JButton("Restart server");

    // Setup
    private final JTextArea setupCodeBlock  = new JTextArea();
    private final JButton   copyConfigBtn   = new JButton("Copy config");
    private int            currentPort  = 8282;
    private String         currentLanIp = null;
    private ConnectionMode currentMode  = ConnectionMode.LOCAL;

    // Relay
    private final JLabel  relayDot      = new JLabel("⬤");
    private final JLabel  relayText     = new JLabel("Disabled");
    private final JLabel  relayUrlLabel = new JLabel();
    private final JButton relayUrlCopy  = new JButton("Copy");
    private final JPanel  relayUrlRow   = new JPanel(new BorderLayout(4, 0));
    private final JPanel  relaySection  = new JPanel();
    private String        fullRelayUrl  = null;
    private final JLabel  regLabel      = new JLabel();
    private final JPanel  regRow        = new JPanel(new BorderLayout(4, 0));

    private Runnable restartCallback;

    public OsrsMcpPanel()
    {
        super(false);
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(ColorScheme.DARK_GRAY_COLOR);
        root.setBorder(new EmptyBorder(8, 8, 8, 8));

        root.add(buildStatusSection());
        root.add(Box.createVerticalStrut(6));
        root.add(buildSeparator());
        root.add(Box.createVerticalStrut(6));
        root.add(buildSectionHeader("Setup"));
        root.add(Box.createVerticalStrut(4));
        root.add(buildSetupSection());
        root.add(Box.createVerticalStrut(6));
        root.add(buildSeparator());
        root.add(Box.createVerticalStrut(6));
        root.add(buildSectionHeader("Cloud relay"));
        root.add(Box.createVerticalStrut(4));
        root.add(buildRelaySection());
        root.add(Box.createVerticalStrut(6));
        root.add(buildSeparator());
        root.add(Box.createVerticalStrut(6));
        root.add(buildSectionHeader("Available tools"));
        root.add(Box.createVerticalStrut(4));
        root.add(buildToolsSection());

        add(root, BorderLayout.NORTH);
    }

    public void setRestartCallback(Runnable cb) { this.restartCallback = cb; }

    // ── SECTION BUILDERS ─────────────────────────────────────────────────────

    private JPanel buildStatusSection()
    {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("OSRS MCP");
        title.setForeground(Color.WHITE);
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setAlignmentX(LEFT_ALIGNMENT);
        p.add(title);
        p.add(Box.createVerticalStrut(6));

        JPanel statusRow = hRow();
        statusDot.setFont(statusDot.getFont().deriveFont(9f));
        statusDot.setForeground(Color.GRAY);
        statusText.setFont(FontManager.getRunescapeSmallFont());
        statusText.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        statusRow.add(statusDot);
        statusRow.add(Box.createHorizontalStrut(4));
        statusRow.add(statusText);
        p.add(statusRow);

        JPanel modeRow = hRow();
        statusMode.setFont(FontManager.getRunescapeSmallFont());
        statusMode.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        modeRow.add(statusMode);
        p.add(modeRow);

        JPanel stateRow = hRow();
        gameStateLabel.setFont(FontManager.getRunescapeSmallFont());
        gameStateLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        stateRow.add(gameStateLabel);
        p.add(stateRow);

        JPanel urlRow = hRow();
        localUrlLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        localUrlLabel.setForeground(ColorScheme.BRAND_ORANGE);
        urlRow.add(localUrlLabel);
        p.add(urlRow);

        p.add(Box.createVerticalStrut(6));
        styleButton(restartButton);
        restartButton.setAlignmentX(LEFT_ALIGNMENT);
        restartButton.addActionListener(e -> {
            if (restartCallback != null)
            {
                restartButton.setEnabled(false);
                restartButton.setText("Restarting...");
                new Thread(() -> {
                    restartCallback.run();
                    SwingUtilities.invokeLater(() -> {
                        restartButton.setEnabled(true);
                        restartButton.setText("Restart server");
                    });
                }, "osrs-mcp-restart").start();
            }
        });
        p.add(restartButton);
        return p;
    }

    private JPanel buildSetupSection()
    {
        JPanel p = box(true);
        p.add(smallLabel("1. Add to Claude Desktop config:"));
        p.add(Box.createVerticalStrut(4));
        styleCodeBlock(setupCodeBlock);
        refreshSetupBlock();
        p.add(setupCodeBlock);
        p.add(Box.createVerticalStrut(4));

        // Copy config button
        styleButton(copyConfigBtn);
        copyConfigBtn.setAlignmentX(LEFT_ALIGNMENT);
        copyConfigBtn.addActionListener(e -> {
            String text = setupCodeBlock.getText();
            if (text != null && !text.isEmpty())
            {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
                copyConfigBtn.setText("Copied!");
                Timer t = new Timer(1500, ev -> copyConfigBtn.setText("Copy config"));
                t.setRepeats(false);
                t.start();
            }
        });
        p.add(copyConfigBtn);

        p.add(Box.createVerticalStrut(6));
        p.add(smallLabel("2. Restart Claude Desktop."));
        p.add(Box.createVerticalStrut(2));
        p.add(smallLabel("3. Ask your AI about your stats!"));
        return p;
    }

    private JPanel buildRelaySection()
    {
        relaySection.setLayout(new BoxLayout(relaySection, BoxLayout.Y_AXIS));
        relaySection.setBackground(SECTION_BG);
        relaySection.setAlignmentX(LEFT_ALIGNMENT);
        relaySection.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        relaySection.setBorder(new CompoundBorder(
            new MatteBorder(1, 1, 1, 1, ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(6, 8, 6, 8)
        ));

        JPanel dotRow = hRow();
        relayDot.setFont(relayDot.getFont().deriveFont(9f));
        relayDot.setForeground(Color.GRAY);
        relayText.setFont(FontManager.getRunescapeSmallFont());
        relayText.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        dotRow.add(relayDot);
        dotRow.add(Box.createHorizontalStrut(4));
        dotRow.add(relayText);
        dotRow.setAlignmentX(LEFT_ALIGNMENT);
        relaySection.add(dotRow);

        relayUrlLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        relayUrlLabel.setForeground(ColorScheme.BRAND_ORANGE);

        styleButton(relayUrlCopy);
        relayUrlCopy.addActionListener(e -> {
            if (fullRelayUrl != null)
            {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(fullRelayUrl), null);
                relayUrlCopy.setText("Copied!");
                Timer t = new Timer(1500, ev -> relayUrlCopy.setText("Copy"));
                t.setRepeats(false);
                t.start();
            }
        });

        relayUrlRow.setBackground(SECTION_BG);
        relayUrlRow.add(relayUrlLabel, BorderLayout.CENTER);
        relayUrlRow.add(relayUrlCopy, BorderLayout.EAST);
        relayUrlRow.setAlignmentX(LEFT_ALIGNMENT);
        relayUrlRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        relayUrlRow.setVisible(false);
        relaySection.add(Box.createVerticalStrut(2));
        relaySection.add(relayUrlRow);

        // Registration required row
        regLabel.setFont(FontManager.getRunescapeSmallFont());
        regLabel.setForeground(ColorScheme.BRAND_ORANGE);
        JButton copyRegBtn = new JButton("Copy URL");
        styleButton(copyRegBtn);
        copyRegBtn.addActionListener(e -> {
            String regUrl = regLabel.getToolTipText();
            if (regUrl != null && !regUrl.isEmpty())
            {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(regUrl), null);
                copyRegBtn.setText("Copied!");
                Timer t = new Timer(1500, ev -> copyRegBtn.setText("Copy URL"));
                t.setRepeats(false);
                t.start();
            }
        });
        regRow.setBackground(SECTION_BG);
        regRow.add(regLabel, BorderLayout.CENTER);
        regRow.add(copyRegBtn, BorderLayout.EAST);
        regRow.setAlignmentX(LEFT_ALIGNMENT);
        regRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        regRow.setVisible(false);
        relaySection.add(Box.createVerticalStrut(2));
        relaySection.add(regRow);

        relaySection.add(Box.createVerticalStrut(6));
        relaySection.add(smallLabel("Set mode to \"Cloud relay\" in settings"));
        relaySection.add(smallLabel("to connect across different networks."));
        relaySection.add(Box.createVerticalStrut(6));

        // Permanent register button — always visible so users can register at any time
        JPanel regBtnRow = new JPanel(new BorderLayout(4, 0));
        regBtnRow.setBackground(SECTION_BG);
        regBtnRow.setAlignmentX(LEFT_ALIGNMENT);
        regBtnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        JLabel regInfo = smallLabel("For stable subdomain URL:");
        JButton regBtn = new JButton("Register on serveo");
        styleButton(regBtn);
        regBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection("https://console.serveo.net"), null);
            regBtn.setText("URL copied!");
            Timer t = new Timer(2000, ev -> regBtn.setText("Register on serveo"));
            t.setRepeats(false);
            t.start();
        });
        regBtnRow.add(regInfo, BorderLayout.CENTER);
        regBtnRow.add(regBtn, BorderLayout.EAST);
        relaySection.add(regBtnRow);

        return relaySection;
    }

    private JPanel buildToolsSection()
    {
        JPanel p = box(false);
        String[][] tools = {
            {"get_all",          "All data in one call"},
            {"get_player_stats", "Skill levels & XP"},
            {"get_equipment",    "Equipped gear by slot"},
            {"get_inventory",    "Inventory contents"},
            {"get_location",     "World coords & area"},
        };
        for (String[] tool : tools) p.add(buildToolRow(tool[0], tool[1]));
        return p;
    }

    // ── SETUP CODE BLOCK ─────────────────────────────────────────────────────

    private void refreshSetupBlock()
    {
        String url;
        String argsLine;

        switch (currentMode)
        {
            case LAN:
                String ip = currentLanIp != null ? currentLanIp : "YOUR_LAN_IP";
                url = "http://" + ip + ":" + currentPort + "/mcp";
                argsLine = "      \"" + url + "\",\n      \"--allow-http\"]";
                break;
            case CLOUD_RELAY:
                url = fullRelayUrl != null ? fullRelayUrl : "https://YOUR_RELAY_URL/mcp";
                argsLine = "      \"" + url + "\"]";
                break;
            default:
                url = "http://127.0.0.1:" + currentPort + "/mcp";
                argsLine = "      \"" + url + "\"]";
                break;
        }

        setupCodeBlock.setText(
            "\"osrs\": {\n" +
            "  \"command\": \"npx\",\n" +
            "  \"args\": [\"mcp-remote\",\n" +
            argsLine + "\n" +
            "}"
        );
    }

    // ── COMPONENT HELPERS ────────────────────────────────────────────────────

    private JSeparator buildSeparator()
    {
        JSeparator sep = new JSeparator();
        sep.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        sep.setBackground(ColorScheme.DARK_GRAY_COLOR);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(LEFT_ALIGNMENT);
        return sep;
    }

    private JLabel buildSectionHeader(String text)
    {
        JLabel l = new JLabel(text);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(ColorScheme.BRAND_ORANGE);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JPanel buildToolRow(String name, String desc)
    {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(SECTION_BG);
        row.setBorder(new EmptyBorder(4, 8, 4, 8));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        row.setAlignmentX(LEFT_ALIGNMENT);
        JLabel n = new JLabel(name);
        n.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        n.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        JLabel d = new JLabel(desc);
        d.setFont(FontManager.getRunescapeSmallFont());
        d.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        d.setHorizontalAlignment(SwingConstants.RIGHT);
        row.add(n, BorderLayout.WEST);
        row.add(d, BorderLayout.EAST);
        return row;
    }

    private JPanel hRow()
    {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setBackground(ColorScheme.DARK_GRAY_COLOR);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        return p;
    }

    private JPanel box(boolean padded)
    {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(SECTION_BG);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        int pad = padded ? 8 : 4;
        p.setBorder(new CompoundBorder(
            new MatteBorder(1, 1, 1, 1, ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(pad, padded ? 8 : 0, pad, padded ? 8 : 0)
        ));
        return p;
    }

    private JLabel smallLabel(String text)
    {
        JLabel l = new JLabel(text);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private void styleCodeBlock(JTextArea area)
    {
        area.setEditable(false);
        area.setBackground(ColorScheme.DARK_GRAY_COLOR);
        area.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        area.setLineWrap(false);
        area.setBorder(new EmptyBorder(2, 2, 2, 2));
        area.setAlignmentX(LEFT_ALIGNMENT);
        area.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
    }

    private void styleButton(JButton btn)
    {
        btn.setFont(FontManager.getRunescapeSmallFont());
        btn.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        btn.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
        btn.setBorder(new EmptyBorder(4, 8, 4, 8));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
    }

    // ── PUBLIC STATE METHODS ─────────────────────────────────────────────────

    public void setServerRunning(boolean running, int port, ConnectionMode mode, String lanIp)
    {
        SwingUtilities.invokeLater(() ->
        {
            currentPort  = port;
            currentMode  = mode;
            currentLanIp = running ? lanIp : null;

            statusDot.setForeground(running ? GREEN : Color.GRAY);
            statusText.setText(running ? "MCP server running" : "MCP server stopped");
            statusMode.setText(running ? "Mode: " + mode.toString() : "");

            if (running)
            {
                String displayUrl = (mode == ConnectionMode.LAN && lanIp != null)
                    ? "http://" + lanIp + ":" + port + "/mcp"
                    : (mode == ConnectionMode.LOCAL)
                        ? "http://127.0.0.1:" + port + "/mcp"
                        : "";
                localUrlLabel.setText(displayUrl);
            }
            else { localUrlLabel.setText(""); }

            refreshSetupBlock();
        });
    }

    public void setStatus(boolean running, int port) { setServerRunning(running, port, ConnectionMode.LOCAL, null); }

    public void setError(String message)
    {
        SwingUtilities.invokeLater(() ->
        {
            statusDot.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
            statusText.setText("Error: " + message);
        });
    }

    public void setRelayStatus(RelayStatus status, String url)
    {
        SwingUtilities.invokeLater(() ->
        {
            switch (status)
            {
                case OFF:
                    relayDot.setForeground(Color.GRAY);
                    relayText.setText("Disabled");
                    relayUrlRow.setVisible(false);
                    fullRelayUrl = null;
                    regRow.setVisible(false);
                    break;
                case CONNECTING:
                    relayDot.setForeground(ColorScheme.BRAND_ORANGE);
                    relayText.setText("Connecting...");
                    relayUrlRow.setVisible(false);
                    fullRelayUrl = null;
                    regRow.setVisible(false);
                    break;
                case ACTIVE:
                    relayDot.setForeground(GREEN);
                    relayText.setText("Active");
                    fullRelayUrl = url;
                    // Truncate display label, full URL in tooltip
                    String display = url != null && url.length() > 30
                        ? url.substring(0, 30) + "..." : url;
                    relayUrlLabel.setText(display);
                    relayUrlLabel.setToolTipText(url);
                    relayUrlRow.setToolTipText(url);
                    relayUrlRow.setVisible(true);
                    regRow.setVisible(false);
                    // Update setup block with real relay URL
                    refreshSetupBlock();
                    break;
                case ERROR:
                    relayDot.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
                    relayText.setText("Failed");
                    fullRelayUrl = null;
                    relayUrlLabel.setText(url != null ? url : "");
                    relayUrlRow.setVisible(url != null && !url.isEmpty());
                    regRow.setVisible(false);
                    break;
                case NEEDS_REGISTRATION:
                    relayDot.setForeground(ColorScheme.BRAND_ORANGE);
                    relayText.setText("Registration needed");
                    regLabel.setText("Copy URL, open in browser & sign in");
                    regLabel.setToolTipText(url);
                    regRow.setVisible(true);
                    break;
            }
            relaySection.revalidate();
            relaySection.repaint();
        });
    }

    public void updateGameState(GameState state)
    {
        SwingUtilities.invokeLater(() ->
        {
            switch (state)
            {
                case LOGGED_IN:
                    gameStateLabel.setForeground(GREEN);
                    gameStateLabel.setText("Logged in");
                    break;
                case LOGIN_SCREEN:
                    gameStateLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
                    gameStateLabel.setText("Login screen");
                    break;
                case LOADING:
                    gameStateLabel.setForeground(ColorScheme.BRAND_ORANGE);
                    gameStateLabel.setText("Loading...");
                    break;
                default:
                    gameStateLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
                    gameStateLabel.setText(state.name().toLowerCase());
            }
        });
    }
}
