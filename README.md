# OSRS MCP

Exposes your RuneLite client data via a local [MCP (Model Context Protocol)](https://modelcontextprotocol.io) server, letting any MCP-compatible AI assistant see your stats, gear, quests, diary progress and more to give context-aware in-game advice.

> MCP is an open standard supported by Claude, Cursor, Windsurf, and a growing number of AI tools.

## What it does

Ask your AI assistant things like:
- *"What gear upgrades should I prioritise with 2M GP?"*
- *"Is my current setup good for my Slayer task?"*
- *"What quests do I still need for the Inferno?"*
- *"How far am I from 70 Prayer and what bones should I use?"*

## Available tools

| Tool | Description |
|------|-------------|
| `get_all` | All player data in one call |
| `get_player_stats` | Skill levels, XP, XP to next level, combat level |
| `get_equipment` | Equipped items by slot |
| `get_inventory` | Inventory contents and quantities |
| `get_location` | World coordinates and area name |
| `get_quest_states` | All quests (completed / in progress / not started) and total quest points |
| `get_diary_states` | All 12 Achievement Diary regions with easy/medium/hard/elite completion |
| `get_slayer_task` | Current task, kills remaining, location, points and streak |
| `get_clue_scroll` | Whether you have an active clue scroll and which tier |
| `get_ge_offers` | All active Grand Exchange offers |
| `get_installed_plugins` | All installed RuneLite plugins (built-in and Plugin Hub) with enabled state |
| `get_bank_summary` | Total bank value, item count and coin balance (requires opening bank first) |
| `get_bank_top_value` | Top 100 items in your bank sorted by total GE value |
| `get_bank_coins` | Coin totals across inventory and bank combined |
| `get_bank_classified` | Bank items split by category: equipment (with slot), food, potions, runes, ammo, materials. Includes Wiki examine text |
| `get_collection_log` | Collection log progress: total unique items and per-category breakdown |
| `get_prayers` | Currently active prayers and unlock status for Preserve, Rigour, Augury, Piety |
| `get_nearby_npcs` | NPCs currently visible, sorted by combat level |
| `get_world_info` | Current world number and type (members, PvP, high risk, deadman, etc.) |
| `get_item_prices` | Live OSRS Wiki GE prices for specific item IDs (pass `item_ids` array) |
| `get_flip_suggestions` | Top flip candidates from your bank cross-referenced with live GE margins and coin budget |
| `get_money_making_context` | Your location, stats, coins and slayer task for money making advice |

---

## Connection modes

The plugin supports four connection modes. Start with the simplest one that fits your setup.

### Mode 1 — Local (same machine)

The default. RuneLite and your AI tool are on the same machine.

1. Install the plugin from the Plugin Hub
2. Open your Claude Desktop config file:
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

Use this when RuneLite is on one device and your AI tool is on another, both on the same network.

1. Set **Connection mode** to **LAN** in plugin settings, then click **Restart server**
2. The plugin panel automatically shows your LAN IP and the correct config snippet — copy it directly from there
3. Or manually use:

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

> **Note:** The `--allow-http` flag is required for non-localhost HTTP URLs.

> **Troubleshooting:** If devices can't reach each other, they may be on different subnets (e.g. one on Ethernet, one on WiFi through a different router). Check both IPs — if they're on different ranges, use Tailscale instead.

---

### Mode 3 — Tailscale (different networks, recommended)

Use this when your devices are on different networks. [Tailscale](https://tailscale.com) is free and gives you a stable permanent IP across all your devices.

1. Set **Connection mode** to **Tailscale** in plugin settings
2. The plugin panel will guide you through setup:
   - If Tailscale is not installed: shows an install link and steps
   - If Tailscale is running: shows your Tailscale IP and the config snippet to copy
3. Install Tailscale on both devices and sign in with the same account
4. Click **Restart server** — the panel will show your config snippet once connected

---

### Mode 4 — Cloud Relay (different networks, no extra software)

Use this when you can't install Tailscale and your devices are on different networks. Uses SSH reverse tunnelling — SSH is built into macOS and Windows 10/11.

1. Set **Connection mode** to **Cloud relay** in plugin settings
2. Click **Restart server** — the panel shows a public HTTPS URL once connected
3. Use the **Copy config** button to copy the JSON snippet and paste it into your AI tool config

The tunnel uses [serveo.net](https://serveo.net) with automatic fallback to [localhost.run](https://localhost.run). Both are free with no account required for basic use.

> **Note:** The URL changes every time the plugin restarts. See below for a stable URL.

#### Getting a stable URL (optional)

1. Type a unique subdomain in the **Stable URL setup** field in the plugin panel (e.g. `yourname-osrs-mcp`)
2. Click **Copy register URL** and open it in your browser — sign in with Google or GitHub to register your SSH key
3. Click **Copy domain URL**, open it in your browser, click **Add Domain** and enter your subdomain
4. Click **Save & restart** — your URL will now always be `https://yourname-osrs-mcp.serveousercontent.com/mcp`

#### Known limitations with stable URLs

Serveo's SSH key authentication has shown intermittent reliability issues. Symptoms include falling back to a random URL even when the key and domain are correctly registered. This is likely caused by serveo rate-limiting repeated connections or stale sessions counting toward the 3-tunnel free tier limit. Waiting a few hours and trying again usually resolves it. This will be revisited once the plugin has wider usage. Tailscale is the more reliable option if a stable URL matters.

---

### Other AI tools

The plugin works with any MCP-compatible tool.

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

- The server only binds to `127.0.0.1` by default — never exposed externally unless you choose LAN, Tailscale or Cloud Relay mode
- Individual toggles for stats, equipment, inventory, location and username in plugin settings
- Optional auth token for LAN and relay setups
- Read-only — the plugin never sends commands to the game

## Configuration

| Setting | Default | Description |
|---------|---------|-------------|
| Port | 8282 | Port the MCP server listens on |
| Connection mode | Local | Local / LAN / Tailscale / Cloud relay |
| Auth Token | (empty) | Optional Bearer token for extra security |
| Share skill levels | On | Allow the AI to read your skills |
| Share equipment | On | Allow the AI to see equipped gear |
| Share inventory | On | Allow the AI to see inventory |
| Share location | On | Allow the AI to see your location |
| Share username | On | Include your RSN in responses |
| Stable subdomain | (empty) | Cloud Relay only — subdomain for a permanent URL |
