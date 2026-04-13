package com.osrsmcp;


import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import com.osrsmcp.ConnectionMode;
import javax.inject.Singleton;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Singleton
public class McpServer
{
    private static final String MCP_VERSION    = "2024-11-05";
    private static final String SERVER_NAME    = "osrs-mcp";
    private static final String SERVER_VERSION = "1.0.0";

    @Inject private PlayerDataService playerDataService;
    @Inject private ClientThread clientThread;
    @Inject private OsrsMcpConfig config;

    private HttpServer server;
    @Inject private Gson gson;
    private final Set<SseClient> sseClients = ConcurrentHashMap.newKeySet();

    public void start(int port) throws IOException
    {
        server = HttpServer.create(new InetSocketAddress((config.connectionMode() == ConnectionMode.LAN || config.connectionMode() == ConnectionMode.TAILSCALE) ? "0.0.0.0" : "127.0.0.1", port), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/mcp",    this::handleMcp);
        server.createContext("/health", this::handleHealth);
        server.start();
        log.info("OSRS MCP MCP server started on http://127.0.0.1:{}/mcp", port);
    }

    public void stop()
    {
        if (server != null)
        {
            sseClients.forEach(SseClient::close);
            sseClients.clear();
            server.stop(1);
            log.info("OSRS MCP MCP server stopped");
        }
    }

    private void handleMcp(HttpExchange exchange) throws IOException
    {
        addCorsHeaders(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
        if (!isAuthorized(exchange)) { sendError(exchange, 401, "Unauthorized"); return; }
        switch (exchange.getRequestMethod())
        {
            case "POST": handlePost(exchange); break;
            case "GET":  handleSse(exchange);  break;
            default:     sendError(exchange, 405, "Method Not Allowed");
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException
    {
        addCorsHeaders(exchange);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "ok");
        status.put("server", SERVER_NAME);
        status.put("version", SERVER_VERSION);
        status.put("logged_in", playerDataService.isLoggedIn());
        sendJson(exchange, 200, status);
    }

    private void handlePost(HttpExchange exchange) throws IOException
    {
        String body;
        try (InputStream is = exchange.getRequestBody())
        {
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        JsonObject request;
        try { request = gson.fromJson(body, JsonObject.class); }
        catch (JsonSyntaxException e) { sendJsonRpcError(exchange, null, -32700, "Parse error"); return; }

        JsonElement idEl = request.get("id");
        String id = (idEl != null && !idEl.isJsonNull()) ? idEl.toString() : null;
        String method = request.has("method") ? request.get("method").getAsString() : "";

        switch (method)
        {
            case "initialize":              sendJsonRpcResult(exchange, id, buildInitializeResult()); break;
            case "notifications/initialized": exchange.sendResponseHeaders(202, -1); break;
            case "tools/list":              sendJsonRpcResult(exchange, id, buildToolsList()); break;
            case "tools/call":              handleToolCall(exchange, id, request); break;
            case "ping":                    sendJsonRpcResult(exchange, id, new JsonObject()); break;
            default: sendJsonRpcError(exchange, id, -32601, "Method not found: " + method);
        }
    }

    private void handleToolCall(HttpExchange exchange, String id, JsonObject request) throws IOException
    {
        JsonObject params = request.has("params") ? request.getAsJsonObject("params") : new JsonObject();
        String toolName = params.has("name") ? params.get("name").getAsString() : "";

        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        clientThread.invokeLater(() ->
        {
            JsonObject arguments = params.has("arguments") ? params.getAsJsonObject("arguments") : new JsonObject();
                try   { future.complete(dispatchTool(toolName, arguments)); }
            catch (Exception e) { future.completeExceptionally(e); }
        });

        Map<String, Object> toolResult;
        try { toolResult = future.get(5, TimeUnit.SECONDS); }
        catch (TimeoutException e) { sendJsonRpcError(exchange, id, -32603, "Timed out waiting for game thread"); return; }
        catch (Exception e)        { sendJsonRpcError(exchange, id, -32603, "Internal error: " + e.getMessage()); return; }

        JsonObject result  = new JsonObject();
        JsonArray  content = new JsonArray();
        JsonObject block   = new JsonObject();
        block.addProperty("type", "text");
        block.addProperty("text", gson.toJson(toolResult));
        content.add(block);
        result.add("content", content);
        result.addProperty("isError", toolResult.containsKey("error"));
        sendJsonRpcResult(exchange, id, result);
    }

    private Map<String, Object> dispatchTool(String toolName, JsonObject args)
    {
        switch (toolName)
        {
            case "get_player_stats": if (!config.shareStats())     return privacyError("stats");     return playerDataService.buildStats();
            case "get_equipment":    if (!config.shareEquipment()) return privacyError("equipment"); Map<String,Object> eq = new LinkedHashMap<>(); eq.put("equipment", playerDataService.buildEquipment()); return eq;
            case "get_inventory":    if (!config.shareInventory()) return privacyError("inventory"); Map<String,Object> inv = new LinkedHashMap<>(); inv.put("inventory", playerDataService.buildInventory()); return inv;
            case "get_location":     if (!config.shareLocation())  return privacyError("location");  return playerDataService.buildLocation();
            case "get_all":          return playerDataService.buildSnapshot();
            case "get_quest_states":  return playerDataService.buildQuestStates();
            case "get_diary_states":  return playerDataService.buildDiaryStates();
            case "get_slayer_task":   return playerDataService.buildSlayerTask();
            case "get_clue_scroll":   return playerDataService.buildClueScroll();
            case "get_nearby_npcs":       return playerDataService.buildNearbyNpcs();
            case "get_world_info":         return playerDataService.buildWorldInfo();
            case "get_prayers":           return playerDataService.buildPrayers();
            case "get_collection_log":    return playerDataService.buildCollectionLog();
            case "get_bank_classified":    return playerDataService.buildBankClassified();
            case "get_bank_summary":       return playerDataService.buildBankSummary();
            case "get_bank_top_value":     return playerDataService.buildBankTopValue();
            case "get_bank_coins":          return playerDataService.buildBankCoins();
            case "get_equipment_stats":    return playerDataService.buildEquipmentStats();
            case "get_npc_info":             {
                String npcName = args != null && args.has("name") ? args.get("name").getAsString() : "";
                return playerDataService.buildNpcInfo(npcName);
            }
            case "get_combat_context":     return playerDataService.buildCombatContext();
            case "get_boss_kc":             return playerDataService.buildBossKc();
            case "get_price_trends":        {
                java.util.List<Integer> ids = new java.util.ArrayList<>();
                if (args != null && args.has("item_ids"))
                    for (com.google.gson.JsonElement e : args.getAsJsonArray("item_ids"))
                        ids.add(e.getAsInt());
                return playerDataService.buildPriceTrends(ids);
            }
            case "get_item_prices":         {
                java.util.List<Integer> ids = new java.util.ArrayList<>();
                if (args != null && args.has("item_ids")) {
                    for (com.google.gson.JsonElement e : args.getAsJsonArray("item_ids"))
                        ids.add(e.getAsInt());
                }
                return playerDataService.buildItemPrices(ids);
            }
            case "get_flip_suggestions":    return playerDataService.buildFlipSuggestions();
            case "get_money_making_context":return playerDataService.buildMoneyMakingContext();
            case "get_installed_plugins": return playerDataService.buildInstalledPlugins();
            case "get_ge_offers":          { Map<String,Object> ge = new LinkedHashMap<>(); ge.put("offers", playerDataService.buildGeOffers()); return ge; }
            default: Map<String,Object> err = new LinkedHashMap<>(); err.put("error", "Unknown tool: " + toolName); return err;
        }
    }

    private void handleSse(HttpExchange exchange) throws IOException
    {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);
        SseClient client = new SseClient(exchange.getResponseBody());
        sseClients.add(client);
        client.send("endpoint", "/mcp");
        try
        {
            while (!client.isClosed()) { Thread.sleep(15_000); client.send("ping", "{}"); }
        }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        finally { sseClients.remove(client); client.close(); }
    }

    private JsonObject buildInitializeResult()
    {
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", MCP_VERSION);
        JsonObject info = new JsonObject(); info.addProperty("name", SERVER_NAME); info.addProperty("version", SERVER_VERSION);
        result.add("serverInfo", info);
        JsonObject caps = new JsonObject(); caps.add("tools", new JsonObject());
        result.add("capabilities", caps);
        return result;
    }

    private JsonObject buildToolsList()
    {
        JsonObject result = new JsonObject();
        JsonArray tools = new JsonArray();
        tools.add(buildTool("get_player_stats", "Get the player's current skill levels, XP, XP to next level, and combat level."));
        tools.add(buildTool("get_equipment",    "Get a list of items the player currently has equipped, organised by slot."));
        tools.add(buildTool("get_inventory",    "Get the contents of the player's inventory including item names and quantities."));
        tools.add(buildTool("get_location",     "Get the player's current in-game location including world coordinates and area name."));
        tools.add(buildTool("get_all",          "Get all available player data in a single call: stats, equipment, inventory and location."));
        tools.add(buildTool("get_quest_states",  "Get the state of all quests (completed, in progress, not started) and total quest points."));
        tools.add(buildTool("get_diary_states",  "Get completion status of all Achievement Diaries across all regions and tiers (easy/medium/hard/elite)."));
        tools.add(buildTool("get_slayer_task",   "Get current Slayer task: creature name, remaining count, location, points and streak."));
        tools.add(buildTool("get_clue_scroll",   "Check if the player has an active clue scroll in their inventory and which tier it is."));
        tools.add(buildTool("get_nearby_npcs",     "Get a list of NPCs currently visible to the player, sorted by combat level. Includes name, combat level, and approximate health."));
        tools.add(buildTool("get_world_info",      "Get the current world number and type (members, PvP, high risk, deadman, seasonal, skill total, etc.)."));
        tools.add(buildTool("get_prayers",         "Get currently active prayers and unlock status for special prayers (Preserve, Rigour, Augury, Chivalry, Piety)."));
        tools.add(buildTool("get_collection_log",  "Get the player's collection log progress: total unique items obtained, total possible, and a breakdown by category (bosses, raids, clues, minigames, other)."));
        tools.add(buildTool("get_bank_classified",   "Get bank items organised by category: equipment (with slot), food, potions, runes, ammo, materials, other. Includes Wiki examine text."));
        tools.add(buildTool("get_bank_summary",       "Get total bank value, item count and coin balance. Requires bank to have been opened this session."));
        tools.add(buildTool("get_bank_top_value",      "Get the top 100 items in the bank sorted by total GE value. Requires bank open."));
        tools.add(buildTool("get_bank_coins",           "Get coin totals across inventory and bank combined."));
        tools.add(buildTool("get_equipment_stats",  "Get full equipment stat bonuses for currently equipped gear: attack/defence/strength/prayer bonuses per slot, totals, and estimated max hit. Stats fetched live from OSRS Wiki."));
        tools.add(buildTool("get_npc_info",          "Fetch monster stats from the OSRS Wiki by name: combat level, hitpoints, defence bonuses, max hit, attack speed, weaknesses, immune to poison/venom. Pass name as the monster name."));
        tools.add(buildTool("get_combat_context",  "Get combat context: effective levels, attack style, spec energy, active prayers, potion detection, and current target NPC."));
        tools.add(buildTool("get_boss_kc",          "Get boss kill counts: game-tracked slayer boss KCs and profile-stored KCs from ChatCommands plugin."));
        tools.add(buildTool("get_price_trends",  "Get price trend data for specific items: current price, 5m and 1h averages, trade volume, and rising/falling/stable direction. Pass item_ids array."));
        tools.add(buildTool("get_item_prices",          "Get live Wiki GE prices for specific item IDs. Pass item_ids as an array of integers."));
        tools.add(buildTool("get_flip_suggestions",     "Get flip suggestions from bank items cross-referenced with live GE margins, filtered by coin budget."));
        tools.add(buildTool("get_money_making_context", "Get location, stats, coins and slayer task for money making method recommendations."));
        tools.add(buildTool("get_installed_plugins", "Get all installed RuneLite plugins (both built-in and Plugin Hub) with their enabled state. Use this to suggest relevant Plugin Hub plugins."));
        tools.add(buildTool("get_ge_offers",          "Get all active Grand Exchange offers including item, quantity, price and state."));
        result.add("tools", tools);
        return result;
    }

    private JsonObject buildTool(String name, String description)
    {
        JsonObject tool = new JsonObject();
        tool.addProperty("name", name);
        tool.addProperty("description", description);
        JsonObject schema = new JsonObject(); schema.addProperty("type", "object"); schema.add("properties", new JsonObject());
        tool.add("inputSchema", schema);
        return tool;
    }

    private boolean isAuthorized(HttpExchange exchange)
    {
        String token = config.authToken();
        if (token == null || token.isBlank()) return true;
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        return header != null && header.equals("Bearer " + token.trim());
    }

    private void sendJsonRpcResult(HttpExchange exchange, String id, JsonObject result) throws IOException
    {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        if (id != null) { try { response.addProperty("id", Integer.parseInt(id)); } catch (NumberFormatException e) { response.addProperty("id", id); } }
        response.add("result", result);
        sendJson(exchange, 200, response);
    }

    private void sendJsonRpcError(HttpExchange exchange, String id, int code, String message) throws IOException
    {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        if (id != null) { try { response.addProperty("id", Integer.parseInt(id)); } catch (NumberFormatException e) { response.addProperty("id", id); } }
        JsonObject error = new JsonObject(); error.addProperty("code", code); error.addProperty("message", message);
        response.add("error", error);
        sendJson(exchange, 200, response);
    }

    private void sendJson(HttpExchange exchange, int status, Object payload) throws IOException
    {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException
    {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private void addCorsHeaders(HttpExchange exchange)
    {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private Map<String, Object> privacyError(String type)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", "Access to '" + type + "' is disabled in the OSRS MCP plugin settings.");
        return m;
    }

    private static class SseClient
    {
        private final OutputStream out;
        private volatile boolean closed = false;
        SseClient(OutputStream out) { this.out = out; }
        void send(String event, String data)
        {
            try { out.write(("event: " + event + "\ndata: " + data + "\n\n").getBytes(StandardCharsets.UTF_8)); out.flush(); }
            catch (IOException e) { closed = true; }
        }
        boolean isClosed() { return closed; }
        void close() { closed = true; try { out.close(); } catch (IOException ignored) {} }
    }
}
