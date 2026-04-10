# OSRS MCP

Exposes your RuneLite client data via a local [MCP (Model Context Protocol)](https://modelcontextprotocol.io) server, letting any MCP-compatible AI assistant see your stats, gear, inventory and location to give context-aware in-game advice.

> **Origin:** This plugin was originally built with [Claude AI](https://claude.ai) in mind, but MCP is an open standard supported by a growing number of AI tools. Any MCP-compatible assistant can connect to it.

## What it does

Ask your AI assistant things like:
- *"What gear upgrades should I prioritise with 2M GP?"*
- *"Is my current setup good for my Slayer task?"*
- *"What's the fastest way to get from 70 to 80 Slayer?"*

## Available tools

| Tool | Description |
|------|-------------|
| `get_all` | All player data in one call |
| `get_player_stats` | Skill levels, XP, and XP to next level |
| `get_equipment` | Equipped items by slot |
| `get_inventory` | Inventory contents and quantities |
| `get_location` | World coordinates and area name |

---

## Connection modes

The plugin supports three connection modes depending on your setup. Start with the simplest one that fits your situation.

### Mode 1 — Local (same machine)

The default. RuneLite and your AI tool are on the same machine. No extra configuration needed.

1. Download [Claude Desktop](https://claude.ai/download)
2. Open the config file:
   - **macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`
   - **Windows:** `%APPDATA%\Claude\claude_desktop_config.json`
3. Add:

```json
{
  "mcpServers": {
    "osrs": {
      "command": "npx",
      "args": ["mcp-remote", "http://127.0.0.1:8282/mcp"]
    }
  }
}
```

4. Restart Claude Desktop.

---

### Mode 2 — LAN (same network, different devices)

Use this when RuneLite is on one device and your AI tool is on another, and both are on the **same router/subnet** (e.g. both on the same WiFi, or both on Ethernet from the same router).

1. In the plugin settings, set **Connection mode** to **LAN** in settings, then click **Restart server** in the plugin panel (or disable and re-enable the plugin)
2. Find the local IP of the machine running RuneLite:
   - **macOS:** `ipconfig getifaddr en0` in Terminal
   - **Windows:** `ipconfig` in Command Prompt, look for IPv4 Address
3. The plugin panel will automatically show your LAN IP and the correct config snippet — just copy it directly from there.
4. Or manually set it on the other device:

```json
{
  "mcpServers": {
    "osrs": {
      "command": "npx",
      "args": ["mcp-remote", "http://192.168.x.x:8282/mcp", "--allow-http"]
    }
  }
}
```

> **Note:** The `--allow-http` flag is required by `mcp-remote` for non-localhost HTTP URLs.

> **Tip:** Set an Auth Token in the plugin settings when using LAN mode.

#### Troubleshooting: devices can't reach each other

If the connection times out even with LAN mode enabled, your devices are likely on **different subnets**. This commonly happens when one device is on Ethernet and another is on WiFi going through a different router or access point. Check both IPs — if they're on different ranges (e.g. `192.168.0.x` vs `192.168.68.x`) they cannot communicate directly. Use Mode 3 instead.

---

### Mode 3 — Cloud Relay (different networks, no extra software)

Use this when your devices are on different networks or subnets and you don't want to install extra software like Tailscale or ngrok.

The plugin uses **SSH reverse tunnelling** — SSH is built into macOS and Windows 10/11, so nothing extra needs to be installed. It creates a public HTTPS URL that routes traffic through to your local MCP server.

**How it works:** The plugin spawns `ssh -R 80:localhost:8282 serveo.net` as a background process. [Serveo](https://serveo.net) is a free public SSH relay. If Serveo is unavailable, the plugin automatically falls back to [localhost.run](https://localhost.run), which works identically. Both are free with no account required for basic use.

#### Step 1 — Random URL (quick start)

1. Set **Connection mode** to **Cloud relay** in plugin settings
2. Click **Restart server** in the plugin panel
3. Once connected, the panel shows a URL like `https://abc123.serveousercontent.com/mcp`
4. Use the **Copy config** button to copy the full JSON snippet
5. Paste it into your Claude Desktop config and restart Claude Desktop

> The random URL changes every time the plugin restarts. If that's fine for occasional use, you're done. For a permanent URL, see Step 2.

#### Step 2 — Stable URL (recommended for regular use)

To get a URL that never changes across restarts:

1. Click **Register on serveo** in the plugin panel — this copies `https://console.serveo.net` to your clipboard
2. Open that URL in your browser and sign in with Google or GitHub — this registers your SSH public key with serveo
3. In plugin settings, set **Relay subdomain** to something unique (e.g. `yourname-osrs-mcp`)
4. Click **Restart server** — your URL is now always `https://yourname-osrs-mcp.serveousercontent.com/mcp`
5. Set your config once and never change it again:

```json
{
  "mcpServers": {
    "osrs": {
      "command": "npx",
      "args": ["mcp-remote", "https://yourname-osrs-mcp.serveousercontent.com/mcp"]
    }
  }
}
```

> **Why registration?** Serveo requires a one-time SSH key registration to reserve a custom subdomain. This links the subdomain to your specific machine's SSH key so no one else can claim it. It is a simple browser sign-in and takes under a minute.

> **Note:** The subdomain must be unique on serveo. If it's already taken, choose a different name. Use something specific to you — your username plus a suffix works well.

> **Note:** The stable subdomain only works with serveo, not the localhost.run fallback. If serveo is unavailable the plugin falls back to a random localhost.run URL automatically.

>  **Tip:** Set an Auth Token in the plugin settings for extra security when using cloud relay from a shared or untrusted network.

#### Known limitations with stable URLs

The stable subdomain feature depends on serveo's SSH key authentication, which has shown intermittent reliability issues during development and testing. Symptoms include the plugin falling back to a random URL even when the key and domain are correctly registered on serveo.

Likely causes include:
- Serveo rate-limiting repeated connection attempts during setup
- Deleted keys or domains not fully clearing on serveo's backend immediately
- Stale tunnel sessions still counting toward the 3-tunnel free tier limit

If you run into this, waiting a few hours and trying again often resolves it. The feature has worked correctly when serveo's backend is in a clean state.

**This will be revisited once the plugin is live on the Plugin Hub** and tested across a wider range of setups. If stable URLs are critical for your use case, [Tailscale](https://tailscale.com) remains the most reliable cross-device option -- it's free and takes about 2 minutes to set up.

---

### Other AI tools

The plugin works with any MCP-compatible tool, not just Claude Desktop.

**Cursor** — add to `~/.cursor/mcp.json`:
```json
{ "mcpServers": { "osrs": { "url": "http://127.0.0.1:8282/mcp" } } }
```

**Windsurf** — add to `~/.codeium/windsurf/mcp_config.json`:
```json
{ "mcpServers": { "osrs": { "serverUrl": "http://127.0.0.1:8282/mcp" } } }
```

**Any other tool** — point it at `http://127.0.0.1:8282/mcp` using streamable HTTP transport, or use `npx mcp-remote http://127.0.0.1:8282/mcp` as a stdio bridge.

---

## Privacy

- The server only binds to `127.0.0.1` (localhost) by default
- Per-toggle controls for stats, equipment, inventory, location and username in plugin settings
- Optional auth token for LAN and relay setups
- Read-only — the plugin never sends commands to the game

## Configuration

| Setting | Default | Description |
|---------|---------|-------------|
| Port | 8282 | Port the MCP server listens on |
| Connection mode | Local | Local, LAN, or Cloud relay (mutually exclusive) |
| Relay subdomain | (empty) | Optional unique name for a stable cloud relay URL (e.g. `yourname-osrs-mcp`) |
| Auth Token | (empty) | Optional Bearer token |
| Share skill levels | On | Allow the AI to read your skills |
| Share equipment | On | Allow the AI to see equipped gear |
| Share inventory | On | Allow the AI to see inventory |
| Share location | On | Allow the AI to see your location |
| Share username | On | Include your RSN in responses |
