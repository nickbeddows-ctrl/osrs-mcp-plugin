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
    private static final String[] RELAY_HOSTS = {
        "serveo.net",
        "nokey@localhost.run"
    };

    private static final Pattern URL_PATTERN =
        Pattern.compile("https://[a-zA-Z0-9][a-zA-Z0-9.\\-]+");

    private static final Pattern REGISTER_PATTERN =
        Pattern.compile("https://console\\.serveo\\.net/ssh/keys\\?add=[A-Za-z0-9%:+/=]+");

    @Inject private OsrsMcpConfig config;

    private Process        process;
    private ExecutorService executor;
    private volatile String  relayUrl;
    private volatile boolean running;
    private Consumer<String> onUrlAssigned;
    private Consumer<String> onError;
    private Consumer<String> onRegistrationRequired;
    private int relayIndex = 0;

    public void start(int port,
                      Consumer<String> onUrlAssigned,
                      Consumer<String> onError,
                      Consumer<String> onRegistrationRequired)
    {
        this.onUrlAssigned          = onUrlAssigned;
        this.onError                = onError;
        this.onRegistrationRequired = onRegistrationRequired;
        this.relayIndex             = 0;
        this.running                = true;
        tryRelay(port);
    }

    private void tryRelay(int port)
    {
        if (!running || relayIndex >= RELAY_HOSTS.length)
        {
            if (onError != null)
                onError.accept("All relay services failed. Check your internet connection.");
            return;
        }

        String host = RELAY_HOSTS[relayIndex];
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
            String  subdomain    = config.relaySubdomain().trim();
            boolean hasSubdomain = !subdomain.isEmpty() && host.equals("serveo.net");
            String  forwardArg   = hasSubdomain
                ? subdomain + ":80:localhost:" + port
                : "80:localhost:" + port;

            ProcessBuilder pb = new ProcessBuilder(
                "ssh",
                "-o", "StrictHostKeyChecking=no",
                "-o", "ServerAliveInterval=30",
                "-o", "ExitOnForwardFailure=yes",
                "-R", forwardArg,
                host
            );
            pb.redirectErrorStream(true);
            process = pb.start();

            boolean registrationPending = false;
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null)
            {
                log.debug("OSRS MCP relay: {}", line);
                String clean = line.replaceAll("\\x1B\\[[0-9;]*m", "");

                // Check for registration requirement
                Matcher regMatcher = REGISTER_PATTERN.matcher(clean);
                if (regMatcher.find())
                {
                    log.warn("OSRS MCP: Serveo subdomain requires SSH key registration");
                    registrationPending = true;
                    if (onRegistrationRequired != null)
                        onRegistrationRequired.accept(regMatcher.group());
                }

                // Only accept a relay URL if registration is not pending
                if (!registrationPending)
                {
                    Matcher urlMatcher = URL_PATTERN.matcher(clean);
                    if (urlMatcher.find())
                    {
                        String found = urlMatcher.group();
                        if (!found.contains("console.serveo.net"))
                        {
                            relayUrl = found + "/mcp";
                            log.info("OSRS MCP: Relay URL assigned: {}", relayUrl);
                            if (onUrlAssigned != null)
                                onUrlAssigned.accept(relayUrl);
                        }
                    }
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

    public String  getRelayUrl() { return relayUrl; }
    public boolean isRunning()   { return running && relayUrl != null; }
}
