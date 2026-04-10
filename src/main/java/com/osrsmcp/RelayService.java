package com.osrsmcp;

import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Singleton
public class RelayService
{
    private static final String[] RELAY_COMMANDS = {
        "serveo.net",
        "nokey@localhost.run"
    };

    // Broad pattern: matches any https:// URL in relay output.
    // Serveo previously used *.serveo.net but now uses *.serveousercontent.com.
    // localhost.run uses *.lhr.life. Using a general pattern future-proofs both.
    private static final Pattern URL_PATTERN =
        Pattern.compile("https://[a-zA-Z0-9][a-zA-Z0-9.\\-]+");

    @Inject private OsrsMcpConfig config;

    private Process process;
    private ExecutorService executor;
    private volatile String relayUrl;
    private volatile boolean running;
    private Consumer<String> onUrlAssigned;
    private Consumer<String> onError;
    private int relayIndex = 0;

    public void start(int port, Consumer<String> onUrlAssigned, Consumer<String> onError)
    {
        this.onUrlAssigned = onUrlAssigned;
        this.onError = onError;
        this.relayIndex = 0;
        this.running = true;
        tryRelay(port);
    }

    private void tryRelay(int port)
    {
        if (!running || relayIndex >= RELAY_COMMANDS.length)
        {
            if (onError != null)
                onError.accept("All relay services failed. Check your internet connection.");
            return;
        }

        String host = RELAY_COMMANDS[relayIndex];
        log.info("OSRS MCP: Trying relay via {}", host);

        executor = Executors.newSingleThreadExecutor(r ->
        {
            Thread t = new Thread(r, "osrs-mcp-relay");
            t.setDaemon(true);
            return t;
        });

        executor.submit(() -> spawnTunnel(port, host));
    }

    private void spawnTunnel(int port, String host)
    {
        try
        {
            ProcessBuilder pb = new ProcessBuilder(
                "ssh",
                "-o", "StrictHostKeyChecking=no",
                "-o", "ServerAliveInterval=30",
                "-o", "ExitOnForwardFailure=yes",
                "-R", "80:localhost:" + port,
                host
            );
            pb.redirectErrorStream(true);
            process = pb.start();

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null)
            {
                log.debug("OSRS MCP relay: {}", line);
                // Strip ANSI escape codes before matching
                String clean = line.replaceAll("\\x1B\\[[0-9;]*m", "");
                Matcher m = URL_PATTERN.matcher(clean);
                if (m.find())
                {
                    relayUrl = m.group() + "/mcp";
                    log.info("OSRS MCP: Relay URL assigned: {}", relayUrl);
                    if (onUrlAssigned != null)
                        onUrlAssigned.accept(relayUrl);
                }
            }

            process.waitFor();
            if (running)
            {
                log.warn("OSRS MCP: Relay via {} ended, trying next...", host);
                relayIndex++;
                tryRelay(port);
            }
        }
        catch (Exception e)
        {
            if (running)
            {
                log.warn("OSRS MCP: Relay via {} failed: {}", host, e.getMessage());
                relayIndex++;
                tryRelay(port);
            }
        }
    }

    public void stop()
    {
        running = false;
        relayUrl = null;
        if (process != null)
        {
            process.destroy();
            try { process.waitFor(3, TimeUnit.SECONDS); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            process = null;
        }
        if (executor != null)
        {
            executor.shutdownNow();
            executor = null;
        }
    }

    public String getRelayUrl() { return relayUrl; }
    public boolean isRunning()  { return running && relayUrl != null; }
}
