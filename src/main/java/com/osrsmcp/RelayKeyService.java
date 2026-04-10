package com.osrsmcp;

import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import java.io.*;
import java.nio.file.*;

/**
 * Manages a dedicated SSH keypair for the OSRS MCP cloud relay.
 * Uses ~/.ssh/osrs_mcp_ed25519 so it never touches the user's existing keys.
 */
@Slf4j
@Singleton
public class RelayKeyService
{
    private static final String KEY_PATH = System.getProperty("user.home") + "/.ssh/osrs_mcp_ed25519";
    private static final String PUB_PATH = KEY_PATH + ".pub";

    public boolean keyExists()
    {
        return new File(KEY_PATH).exists() && new File(PUB_PATH).exists();
    }

    /**
     * Generates a new ed25519 keypair at ~/.ssh/osrs_mcp_ed25519.
     * Returns true on success.
     */
    public boolean generateKey()
    {
        try
        {
            // Ensure ~/.ssh exists
            new File(System.getProperty("user.home") + "/.ssh").mkdirs();

            Process p = new ProcessBuilder(
                "ssh-keygen",
                "-t", "ed25519",
                "-f", KEY_PATH,
                "-N", "",
                "-C", "osrs-mcp"
            ).redirectErrorStream(true).start();

            String output = new String(p.getInputStream().readAllBytes());
            int exitCode  = p.waitFor();
            log.info("OSRS MCP: ssh-keygen exit={} output={}", exitCode, output.trim());
            return exitCode == 0;
        }
        catch (Exception e)
        {
            log.error("OSRS MCP: Failed to generate SSH key", e);
            return false;
        }
    }

    /**
     * Returns the public key string, or null if the key doesn't exist.
     */
    public String getPublicKey()
    {
        try
        {
            if (!keyExists()) return null;
            return Files.readString(Path.of(PUB_PATH)).trim();
        }
        catch (Exception e)
        {
            log.error("OSRS MCP: Failed to read public key", e);
            return null;
        }
    }

    /**
     * Returns the fingerprint shown in serveo's registration URL, or null.
     */
    public String getFingerprint()
    {
        try
        {
            Process p = new ProcessBuilder(
                "ssh-keygen", "-lf", PUB_PATH
            ).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            // Format: 256 SHA256:xxxx comment (ED25519)
            // Extract SHA256:xxxx
            int start = out.indexOf("SHA256:");
            if (start < 0) return null;
            int end = out.indexOf(" ", start);
            return end > 0 ? out.substring(start, end) : out.substring(start);
        }
        catch (Exception e)
        {
            log.warn("OSRS MCP: Could not get key fingerprint", e);
            return null;
        }
    }

    /**
     * Returns the full serveo registration URL for this key.
     */
    public String getRegistrationUrl()
    {
        String fp = getFingerprint();
        if (fp == null) return "https://console.serveo.net";
        return "https://console.serveo.net/ssh/keys?add=" + fp.replace(":", "%3A");
    }

    public String getKeyPath() { return KEY_PATH; }
}
