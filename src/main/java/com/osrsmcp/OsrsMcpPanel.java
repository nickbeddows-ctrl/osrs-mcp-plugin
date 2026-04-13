package com.osrsmcp;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.api.GameState;

import net.runelite.client.config.ConfigManager;
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

    private static final Color SECTION_BG  = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color GREEN       = new Color(0, 180, 90);
    private static final Color STEP_DONE   = new Color(0, 180, 90);
    private static final Color STEP_ACTIVE = ColorScheme.BRAND_ORANGE;
    private static final Color STEP_TODO   = ColorScheme.MEDIUM_GRAY_COLOR;

    // ── Status ────────────────────────────────────────────────────────────────
    private final JLabel  statusDot      = new JLabel("⬤");
    private final JLabel  statusText     = new JLabel("Starting...");
    private final JLabel  statusMode     = new JLabel();
    private final JLabel  gameStateLabel = new JLabel("Not logged in");
    private final JLabel  localUrlLabel  = new JLabel();
    private final JButton restartButton  = new JButton("Restart server");

    // ── Setup code block ──────────────────────────────────────────────────────
    private final JTextArea setupCodeBlock = new JTextArea();
    private final JButton   copyConfigBtn  = new JButton("Copy config");
    private int            currentPort  = 8282;
    private String         currentLanIp = null;
    private ConnectionMode currentMode  = ConnectionMode.LOCAL;

    // ── Cloud relay steps ─────────────────────────────────────────────────────
    private final JPanel  relayStepsPanel = new JPanel();
    private final JLabel  relayDot        = new JLabel("⬤");
    private final JLabel  relayStatusText = new JLabel("Disabled");
    private final JLabel  relayUrlLabel   = new JLabel();
    private final JButton relayUrlCopy    = new JButton("Copy");
    private final JPanel  relayUrlRow     = new JPanel(new BorderLayout(4, 0));
    private String        fullRelayUrl    = null;

    // Step labels
    private final JLabel step1Label = new JLabel();
    private final JLabel step2Label = new JLabel();
    private final JLabel step3Label = new JLabel();
    private final JLabel step4Label = new JLabel();

    // Step buttons
    private final JButton genKeyBtn      = new JButton("Generate key");
    private final JButton copyKeyBtn     = new JButton("Copy public key");
    private final JButton openRegBtn     = new JButton("Copy register URL");
    private final JButton openDomainBtn  = new JButton("Copy domain URL");

    // Step 4 subdomain field
    private final JTextField subdomainField  = new JTextField();
    private final JButton   saveSubdomainBtn = new JButton("Save & restart");

    // Tailscale
    private TailscaleService tailscaleService;
    private final JLabel tailscaleStep1Label = new JLabel();
    private final JLabel tailscaleStep2Label = new JLabel();
    private final JLabel tailscaleStep3Label = new JLabel();
    private final JButton copyTailscaleUrlBtn = new JButton("Copy tailscale.com");

    // Root panel ref for revalidation
    private JPanel rootPanel;

    // Section visibility refs
    private JPanel relayPanelRef;
    private JLabel relayHeaderRef;
    private JPanel tailscalePanelRef;
    private JLabel tailscaleHeaderRef;

    // Callbacks
    private Runnable         restartCallback;
    private RelayKeyService  relayKeyService;
    private ConfigManager    configManager;

    public OsrsMcpPanel()
    {
        super(false);
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel root = new JPanel();
        rootPanel = root;
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
        relayHeaderRef = buildSectionHeader("Cloud relay");
        root.add(relayHeaderRef);
        root.add(Box.createVerticalStrut(4));
        relayPanelRef = buildRelaySection();
        root.add(relayPanelRef);
        root.add(Box.createVerticalStrut(6));
        root.add(buildSeparator());
        root.add(Box.createVerticalStrut(6));
        tailscaleHeaderRef = buildSectionHeader("Tailscale");
        root.add(tailscaleHeaderRef);
        root.add(Box.createVerticalStrut(4));
        tailscalePanelRef = buildTailscaleSection();
        root.add(tailscalePanelRef);
        root.add(Box.createVerticalStrut(6));
        root.add(buildSeparator()); // tools separator
        root.add(Box.createVerticalStrut(6));
        root.add(buildSectionHeader("Available tools"));
        root.add(Box.createVerticalStrut(4));
        root.add(buildToolsSection());

        add(root, BorderLayout.NORTH);
    }

    public void setRestartCallback(Runnable cb)   { this.restartCallback  = cb; }
    public void setRelayKeyService(RelayKeyService s) { this.relayKeyService = s; }
    public void setConfigManager(ConfigManager cm)    { this.configManager = cm; refreshSubdomainField(); }
    public void setTailscaleService(TailscaleService ts) { this.tailscaleService = ts; }

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
        styleButton(copyConfigBtn);
        copyConfigBtn.setAlignmentX(LEFT_ALIGNMENT);
        copyConfigBtn.addActionListener(e -> {
            String text = setupCodeBlock.getText();
            if (text != null && !text.isEmpty())
            {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
                flash(copyConfigBtn, "Copied!", "Copy config");
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
        JPanel outer = new JPanel();
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
        outer.setBackground(SECTION_BG);
        outer.setAlignmentX(LEFT_ALIGNMENT);
        outer.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        outer.setBorder(new CompoundBorder(
            new MatteBorder(1, 1, 1, 1, ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(6, 8, 8, 8)
        ));

        // Status dot row
        JPanel dotRow = hRow();
        dotRow.setBackground(SECTION_BG);
        relayDot.setFont(relayDot.getFont().deriveFont(9f));
        relayDot.setForeground(Color.GRAY);
        relayStatusText.setFont(FontManager.getRunescapeSmallFont());
        relayStatusText.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        dotRow.add(relayDot);
        dotRow.add(Box.createHorizontalStrut(4));
        dotRow.add(relayStatusText);
        outer.add(dotRow);

        // Active URL row
        relayUrlLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        relayUrlLabel.setForeground(ColorScheme.BRAND_ORANGE);
        styleButton(relayUrlCopy);
        relayUrlCopy.addActionListener(e -> {
            if (fullRelayUrl != null)
            {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(fullRelayUrl), null);
                flash(relayUrlCopy, "Copied!", "Copy");
            }
        });
        relayUrlRow.setBackground(SECTION_BG);
        relayUrlRow.add(relayUrlLabel, BorderLayout.CENTER);
        relayUrlRow.add(relayUrlCopy, BorderLayout.EAST);
        relayUrlRow.setAlignmentX(LEFT_ALIGNMENT);
        relayUrlRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        relayUrlRow.setVisible(false);
        outer.add(Box.createVerticalStrut(2));
        outer.add(relayUrlRow);

        outer.add(Box.createVerticalStrut(8));
        outer.add(buildSeparatorInner());
        outer.add(Box.createVerticalStrut(8));

        // Step-by-step setup
        outer.add(buildStepHeader("Stable URL setup (optional)"));
        outer.add(Box.createVerticalStrut(4));
        outer.add(buildStep("1", step1Label, buildStep1Buttons()));
        outer.add(Box.createVerticalStrut(4));
        outer.add(buildStep("2", step2Label, buildStep2Buttons()));
        outer.add(Box.createVerticalStrut(4));
        outer.add(buildStep("3", step3Label, buildStep3Buttons()));
        outer.add(Box.createVerticalStrut(4));
        outer.add(buildStep("4", step4Label, buildStep4Subdomain()));

        return outer;
    }

    private JPanel buildStep(String number, JLabel label, JPanel buttons)
    {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(SECTION_BG);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(SECTION_BG);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel num = new JLabel(number + ".");
        num.setFont(FontManager.getRunescapeSmallFont());
        num.setForeground(STEP_TODO);
        num.setPreferredSize(new Dimension(14, 20));
        num.setVerticalAlignment(SwingConstants.TOP);

        // Use HTML to allow word wrap; actual color set via setForeground doesn't work with HTML
        // so we store the label text without HTML wrapper and add it in refreshSteps
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(STEP_TODO);
        label.setVerticalAlignment(SwingConstants.TOP);

        row.add(num, BorderLayout.WEST);
        row.add(label, BorderLayout.CENTER);
        p.add(row);

        if (buttons != null)
        {
            p.add(Box.createVerticalStrut(3));
            p.add(buttons);
        }
        return p;
    }

    private JPanel buildStep1Buttons()
    {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setBackground(SECTION_BG);
        p.setAlignmentX(LEFT_ALIGNMENT);
        styleButton(genKeyBtn);
        styleButton(copyKeyBtn);
        genKeyBtn.addActionListener(e -> {
            if (relayKeyService == null) return;
            genKeyBtn.setEnabled(false);
            genKeyBtn.setText("Generating...");
            new Thread(() -> {
                boolean ok = relayKeyService.generateKey();
                SwingUtilities.invokeLater(() -> {
                    refreshSteps();
                    genKeyBtn.setEnabled(true);
                    genKeyBtn.setText(ok ? "Generate key" : "Failed — try again");
                });
            }, "osrs-mcp-keygen").start();
        });
        copyKeyBtn.addActionListener(e -> {
            if (relayKeyService == null) return;
            String pub = relayKeyService.getPublicKey();
            if (pub != null)
            {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(pub), null);
                flash(copyKeyBtn, "Copied!", "Copy public key");
            }
        });
        // Only show generate button if no key exists; copy key is always shown when key exists
        p.add(genKeyBtn);
        p.add(copyKeyBtn);
        return p;
    }

    private JPanel buildStep2Buttons()
    {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setBackground(SECTION_BG);
        p.setAlignmentX(LEFT_ALIGNMENT);
        styleButton(openRegBtn);
        openRegBtn.addActionListener(e -> {
            if (relayKeyService == null) return;
            String url = relayKeyService.getRegistrationUrl();
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(url), null);
            flash(openRegBtn, "URL copied!", "Copy register URL");
        });
        p.add(openRegBtn);
        return p;
    }

    private JPanel buildStep3Buttons()
    {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setBackground(SECTION_BG);
        p.setAlignmentX(LEFT_ALIGNMENT);
        styleButton(openDomainBtn);
        openDomainBtn.addActionListener(e -> {
            String sub = subdomainField.getText().trim();
            String hint = sub.isEmpty() ? "" : " (enter: " + sub + ")";
            openDomainBtn.setToolTipText("console.serveo.net/domains → Add Domain" + hint);
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection("https://console.serveo.net/domains"), null);
            flash(openDomainBtn, "URL copied!", "Copy domain URL");
        });
        p.add(openDomainBtn);
        return p;
    }

    private JPanel buildStep4Subdomain()
    {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(SECTION_BG);
        p.setAlignmentX(LEFT_ALIGNMENT);

        // Text field
        subdomainField.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 10));
        subdomainField.setBackground(ColorScheme.DARK_GRAY_COLOR);
        subdomainField.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        subdomainField.setCaretColor(ColorScheme.LIGHT_GRAY_COLOR);
        subdomainField.setBorder(new CompoundBorder(
            new MatteBorder(1, 1, 1, 1, ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(3, 6, 3, 6)
        ));
        subdomainField.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 24));
        subdomainField.setAlignmentX(LEFT_ALIGNMENT);
        subdomainField.setToolTipText("e.g. yourname-osrs-mcp");
        p.add(Box.createVerticalStrut(3));
        p.add(subdomainField);
        p.add(Box.createVerticalStrut(4));

        // Save & restart button
        styleButton(saveSubdomainBtn);
        saveSubdomainBtn.setAlignmentX(LEFT_ALIGNMENT);
        saveSubdomainBtn.addActionListener(e -> {
            String val = subdomainField.getText().trim();
            if (configManager != null)
                configManager.setConfiguration("osrsmcp", "relaySubdomain", val);
            if (restartCallback != null)
            {
                saveSubdomainBtn.setEnabled(false);
                saveSubdomainBtn.setText("Restarting...");
                new Thread(() -> {
                    restartCallback.run();
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        saveSubdomainBtn.setEnabled(true);
                        saveSubdomainBtn.setText("Save & restart");
                    });
                }, "osrs-mcp-subdomain-restart").start();
            }
        });
        p.add(saveSubdomainBtn);
        return p;
    }

    private void refreshSubdomainField()
    {
        SwingUtilities.invokeLater(() -> {
            if (configManager == null) return;
            String current = configManager.getConfiguration("osrsmcp", "relaySubdomain");
            if (current != null && !current.isEmpty())
                subdomainField.setText(current);
        });
    }

    private JPanel buildTailscaleSection()
    {
        JPanel outer = new JPanel();
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
        outer.setBackground(SECTION_BG);
        outer.setAlignmentX(LEFT_ALIGNMENT);
        outer.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        outer.setBorder(new CompoundBorder(
            new MatteBorder(1, 1, 1, 1, ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(6, 8, 8, 8)
        ));

        // Step 1 -- detect / install
        outer.add(buildStep("1", tailscaleStep1Label, buildTailscaleStep1Buttons()));
        outer.add(Box.createVerticalStrut(4));

        // Step 2 -- sign in on both devices
        outer.add(buildStep("2", tailscaleStep2Label, null));
        outer.add(Box.createVerticalStrut(4));

        // Step 3 -- restart server
        outer.add(buildStep("3", tailscaleStep3Label, null));
        return outer;
    }

    private JPanel buildTailscaleStep1Buttons()
    {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setBackground(SECTION_BG);
        p.setAlignmentX(LEFT_ALIGNMENT);
        styleButton(copyTailscaleUrlBtn);
        copyTailscaleUrlBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection("https://tailscale.com/download"), null);
            flash(copyTailscaleUrlBtn, "Copied!", "Copy tailscale.com");
        });
        p.add(copyTailscaleUrlBtn);
        return p;
    }

    public void refreshSectionsForMode(ConnectionMode mode)
    {
        SwingUtilities.invokeLater(() -> {
            currentMode = mode;
            refreshSections();
        });
    }

    private void refreshSections()
    {
        // Must be called on EDT
        boolean isRelay     = currentMode == ConnectionMode.CLOUD_RELAY;
        boolean isTailscale = currentMode == ConnectionMode.TAILSCALE;

        if (relayHeaderRef != null)     relayHeaderRef.setVisible(isRelay);
        if (relayPanelRef != null)      relayPanelRef.setVisible(isRelay);
        if (tailscaleHeaderRef != null) tailscaleHeaderRef.setVisible(isTailscale);
        if (tailscalePanelRef != null)  tailscalePanelRef.setVisible(isTailscale);

        refreshTailscaleSteps();

        // Tell the root container to recalculate layout
        if (rootPanel != null) { rootPanel.revalidate(); rootPanel.repaint(); }
        revalidate();
        repaint();
    }

    private void refreshTailscaleSteps()
    {
        if (tailscaleService == null) return;
        boolean running = tailscaleService.isRunning();
        String ip = tailscaleService.getTailscaleIp();

        if (running && ip != null)
        {
            // Tailscale is active -- all steps done
            tailscaleStep1Label.setText("<html>Tailscale running on this device</html>");
            tailscaleStep1Label.setForeground(STEP_DONE);
            copyTailscaleUrlBtn.setVisible(false);

            tailscaleStep2Label.setText("<html>Sign in with the same account on your other device</html>");
            tailscaleStep2Label.setForeground(STEP_DONE);

            tailscaleStep3Label.setText("<html>Active! Connect using: http://" + ip + ":" + currentPort + "/mcp</html>");
            tailscaleStep3Label.setForeground(STEP_DONE);
        }
        else
        {
            // Tailscale not detected -- show setup steps
            tailscaleStep1Label.setText("<html>Install Tailscale on this device (free)</html>");
            tailscaleStep1Label.setForeground(STEP_ACTIVE);
            copyTailscaleUrlBtn.setVisible(true);

            tailscaleStep2Label.setText("<html>Sign in with the same Tailscale account on both devices</html>");
            tailscaleStep2Label.setForeground(STEP_TODO);

            tailscaleStep3Label.setText("<html>Come back here and click Restart server</html>");
            tailscaleStep3Label.setForeground(STEP_TODO);
        }
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
            {"get_quest_states", "All quest progress"},
            {"get_diary_states", "Achievement diary tiers"},
            {"get_slayer_task",  "Current Slayer task"},
            {"get_clue_scroll",  "Active clue scroll tier"},
            {"get_ge_offers",          "GE offer slots"},
            {"get_installed_plugins",   "Installed RuneLite plugins"},
        };
        for (String[] t : tools) p.add(buildToolRow(t[0], t[1]));
        return p;
    }

    // ── STEP STATE ────────────────────────────────────────────────────────────

    public void refreshSteps()
    {
        SwingUtilities.invokeLater(() ->
        {
            if (relayKeyService == null) return;
            boolean hasKey = relayKeyService.keyExists();
            String sub = subdomainField.getText().trim();
            // "Stable URL active" only when the running URL actually contains the subdomain
            boolean stableActive = fullRelayUrl != null && !sub.isEmpty()
                && fullRelayUrl.toLowerCase().contains(sub.toLowerCase());
            boolean active = stableActive;

            // Step 1 — SSH key
            if (hasKey)
            {
                step1Label.setText("<html>SSH key ready</html>");
                step1Label.setForeground(STEP_DONE);
                genKeyBtn.setVisible(false);
                copyKeyBtn.setEnabled(true);
            }
            else
            {
                step1Label.setText("<html>Generate a dedicated SSH key for this plugin</html>");
                step1Label.setForeground(STEP_ACTIVE);
                genKeyBtn.setVisible(true);
                genKeyBtn.setText("Generate key");
                copyKeyBtn.setEnabled(false);
            }

            // Step 2 — Register SSH key on serveo
            step2Label.setText(hasKey
                ? "<html>Copy the register URL and open it in your browser. Sign in with Google or GitHub to register your SSH key with serveo.</html>"
                : "<html>Complete step 1 first</html>");
            step2Label.setForeground(hasKey ? STEP_ACTIVE : STEP_TODO);
            openRegBtn.setEnabled(hasKey);

            // Step 3 — Add domain on serveo
            String subHint = sub.isEmpty() ? "yourname-osrs-mcp" : sub;
            step3Label.setText(hasKey
                ? "<html>Copy the domain URL, open it in your browser, click Add Domain and enter <b>" + subHint + "</b></html>"
                : "<html>Complete steps 1 and 2 first</html>");
            step3Label.setForeground(hasKey ? STEP_ACTIVE : STEP_TODO);
            openDomainBtn.setEnabled(hasKey);

            // Step 4 — Enter subdomain in panel and save
            step4Label.setText(stableActive
                ? "<html>Stable URL active!</html>"
                : (fullRelayUrl != null && !sub.isEmpty() && !stableActive)
                    ? "<html>Random URL is active but subdomain not matched yet. Check your subdomain is registered on serveo, then Save & restart.</html>"
                    : "<html>Type your subdomain below and click Save & restart.</html>");
            step4Label.setForeground(stableActive ? STEP_DONE : (hasKey ? STEP_ACTIVE : STEP_TODO));
        });
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
            case TAILSCALE:
                String tsIp = currentLanIp != null ? currentLanIp : "YOUR_TAILSCALE_IP";
                url = "http://" + tsIp + ":" + currentPort + "/mcp";
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
        JSeparator s = new JSeparator();
        s.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        s.setBackground(ColorScheme.DARK_GRAY_COLOR);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        s.setAlignmentX(LEFT_ALIGNMENT);
        return s;
    }

    private JSeparator buildSeparatorInner()
    {
        JSeparator s = new JSeparator();
        s.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        s.setBackground(SECTION_BG);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        s.setAlignmentX(LEFT_ALIGNMENT);
        return s;
    }

    private JLabel buildSectionHeader(String text)
    {
        JLabel l = new JLabel(text);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setForeground(ColorScheme.BRAND_ORANGE);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JLabel buildStepHeader(String text)
    {
        JLabel l = new JLabel(text);
        l.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
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

    private void flash(JButton btn, String flashText, String original)
    {
        btn.setText(flashText);
        Timer t = new Timer(1500, e -> btn.setText(original));
        t.setRepeats(false);
        t.start();
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
            statusMode.setText(running ? "Mode: " + mode : "");
            if (running)
            {
                String displayUrl;
                if (mode == ConnectionMode.LOCAL)
                    displayUrl = "http://127.0.0.1:" + port + "/mcp";
                else if ((mode == ConnectionMode.LAN || mode == ConnectionMode.TAILSCALE) && lanIp != null)
                    displayUrl = "http://" + lanIp + ":" + port + "/mcp";
                else if (mode == ConnectionMode.TAILSCALE && lanIp == null)
                    displayUrl = "Tailscale not detected — install tailscale.com";
                else
                    displayUrl = "";
                localUrlLabel.setText(displayUrl);
            }
            else localUrlLabel.setText("");
            refreshSetupBlock();
            refreshSteps();
            refreshSections();
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
                    relayStatusText.setText("Disabled — set mode to Cloud relay");
                    relayUrlRow.setVisible(false);
                    fullRelayUrl = null;
                    break;
                case CONNECTING:
                    relayDot.setForeground(ColorScheme.BRAND_ORANGE);
                    relayStatusText.setText("Connecting...");
                    relayUrlRow.setVisible(false);
                    fullRelayUrl = null;
                    break;
                case ACTIVE:
                    relayDot.setForeground(GREEN);
                    relayStatusText.setText("Active");
                    fullRelayUrl = url;
                    String display = url != null && url.length() > 32
                        ? url.substring(0, 32) + "..." : url;
                    relayUrlLabel.setText(display);
                    relayUrlLabel.setToolTipText(url);
                    relayUrlRow.setVisible(true);
                    refreshSetupBlock();
                    break;
                case ERROR:
                    relayDot.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
                    relayStatusText.setText("Failed — check internet connection");
                    fullRelayUrl = null;
                    relayUrlRow.setVisible(false);
                    break;
                case NEEDS_REGISTRATION:
                    relayDot.setForeground(ColorScheme.BRAND_ORANGE);
                    relayStatusText.setText("SSH key not registered — see step 2 below");
                    fullRelayUrl = null;
                    relayUrlRow.setVisible(false);
                    break;
            }
            refreshSteps();
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
