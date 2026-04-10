# Claude OSRS

Connects your RuneLite client to [Claude AI](https://claude.ai) via a local MCP (Model Context Protocol) server, letting Claude see your stats, gear, inventory and location to give context-aware advice.

## What it does

Ask Claude things like:
- *"What gear upgrades should I prioritise with 2M GP?"*
- *"Is my current setup good for my Slayer task?"*
- *"What's the fastest way to get from 70 to 80 Slayer?"*

Claude can see your actual in-game data when answering, so advice is specific to your character rather than generic.

## Setup

### 1. Install Claude Desktop
Download from [claude.ai/download](https://claude.ai/download).

### 2. Add the MCP bridge
Add the following to your Claude Desktop config file:

**macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`
**Windows:** `%APPDATA%\Claude\claude_desktop_config.json`

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

### 3. Restart Claude Desktop
Claude will now have access to your in-game data whenever RuneLite is running.

## Available tools

| Tool | Description |
|------|-------------|
| `get_all` | All player data in one call |
| `get_player_stats` | Skill levels, XP, and XP to next level |
| `get_equipment` | Equipped items by slot |
| `get_inventory` | Inventory contents and quantities |
| `get_location` | World coordinates and area name |

## Privacy

- The MCP server only binds to `127.0.0.1` (localhost) by default — never exposed to the internet
- Per-toggle controls for stats, equipment, inventory, location and username in plugin settings
- Optional auth token for shared machines
- Enable **Allow LAN connections** in settings to use Claude on another device on the same network

## Configuration

| Setting | Default | Description |
|---------|---------|-------------|
| Port | 8282 | Port the MCP server listens on |
| Allow LAN connections | Off | Bind to all interfaces for cross-device use |
| Auth Token | (empty) | Optional Bearer token for authentication |
| Share skill levels | On | Allow Claude to read your skills |
| Share equipment | On | Allow Claude to see equipped gear |
| Share inventory | On | Allow Claude to see inventory |
| Share location | On | Allow Claude to see your location |
| Share username | On | Include your RSN in responses |
