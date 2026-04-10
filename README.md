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

Use this when your devices are on different networks or subnets, and you don't want to install extra software like Tailscale or ngrok.

The plugin uses **SSH reverse tunnelling** — SSH is built into macOS and Windows 10/11, so nothing extra needs to be installed. It creates a temporary public HTTPS URL that routes traffic through to your local MCP server.

1. In the plugin settings, enable **Cloud relay**
2. The plugin panel will show a URL like `https://abc123.serveo.net/mcp` once connected
3. Use that URL in your config on any device (no `--allow-http` needed — it's HTTPS):

```json
{
  "mcpServers": {
    "osrs": {
      "command": "npx",
      "args": ["mcp-remote", "https://abc123.serveo.net/mcp"]
    }
  }
}
```

The panel also has a **Copy** button so you don't have to type the URL manually.

**How it works:** The plugin runs `ssh -R 80:localhost:8282 serveo.net` as a background process. Serveo is a free public SSH relay service. If Serveo is unavailable, it automatically falls back to `localhost.run`, which works identically. Both are free and require no account.

> **Note:** By default the relay URL is random and changes every time the plugin restarts. See below for how to get a stable URL.

> **Tip:** Set an Auth Token in the plugin settings for extra security when using cloud relay.

#### Getting a stable URL (recommended)

By default, serveo assigns a random URL each time the tunnel starts, which means you have to update your config every time RuneLite restarts. You can fix this by setting a **Relay subdomain** in the plugin settings.

1. In the plugin settings, set **Relay subdomain** to something unique — e.g. `yourname-osrs-mcp`
2. Your URL will always be `https://yourname-osrs-mcp.serveo.net/mcp` — it never changes
3. Set your config once and never touch it again:

```json
{
  "mcpServers": {
    "osrs": {
      "command": "npx",
      "args": ["mcp-remote", "https://yourname-osrs-mcp.serveo.net/mcp"]
    }
  }
}
```

> **Note:** The subdomain must be unique on serveo.net. If someone else has already claimed it, the plugin will show an error and prompt you to choose a different name. Use something specific to you — your username plus a suffix works well.

> **Note:** The subdomain only works with serveo.net, not the localhost.run fallback. If serveo is unavailable, the plugin falls back to a random localhost.run URL automatically.

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
