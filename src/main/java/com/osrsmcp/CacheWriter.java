package com.osrsmcp;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Item;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Writes plugin cache data as human-readable markdown files to ~/.runelite/osrs-mcp/
 * so that AI assistants can read them directly between sessions without calling MCP tools.
 *
 * Files are updated automatically whenever the relevant in-game data changes.
 */
@Slf4j
@Singleton
public class CacheWriter
{
    private static final String CACHE_DIR =
        System.getProperty("user.home") + File.separator + ".runelite" + File.separator + "osrs-mcp";
    private static final DateTimeFormatter TIMESTAMP_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final NumberFormat NUM_FMT = NumberFormat.getInstance();

    @Inject private ItemManager itemManager;
    @Inject private WikiPriceService wikiPriceService;

    private File cacheDir;

    public void init()
    {
        cacheDir = new File(CACHE_DIR);
        if (!cacheDir.exists() && !cacheDir.mkdirs())
            log.warn("OSRS MCP: Could not create cache directory: {}", CACHE_DIR);
        else
            log.info("OSRS MCP: Cache directory: {}", CACHE_DIR);
    }

    private String now() { return LocalDateTime.now().format(TIMESTAMP_FMT); }
    private String gp(long v) { return NUM_FMT.format(v) + " gp"; }

    // ── CHARACTER ─────────────────────────────────────────────────────────────

    public void writeCharacter(String username, int combatLevel,
                               Map<String, Integer> skills, String location)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("# Character\n");
        sb.append("**Last updated:** ").append(now()).append("\n\n");
        sb.append("| Field | Value |\n|-------|-------|\n");
        sb.append("| Username | ").append(username).append(" |\n");
        sb.append("| Combat level | ").append(combatLevel).append(" |\n");
        if (location != null) sb.append("| Location | ").append(location).append(" |\n");
        sb.append("\n## Skills\n");
        sb.append("| Skill | Level |\n|-------|-------|\n");
        for (Map.Entry<String, Integer> e : skills.entrySet())
            sb.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n");
        write("character.md", sb.toString());
    }

    // ── EQUIPMENT ─────────────────────────────────────────────────────────────

    public void writeEquipment(Map<String, String> slotToItem,
                               EquipmentStatsService equipmentStatsService)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("# Equipped Gear\n");
        sb.append("**Last updated:** ").append(now()).append("\n\n");
        sb.append("| Slot | Item | Str | Slash | Crush | Stab | Magic | Ranged | Prayer |\n");
        sb.append("|------|------|-----|-------|-------|------|-------|--------|--------|\n");
        int totalStr = 0, totalSlash = 0;
        for (Map.Entry<String, String> e : slotToItem.entrySet())
        {
            String slot = e.getKey();
            String name = e.getValue();
            EquipmentStatsService.EquipmentStats stats =
                equipmentStatsService != null ? equipmentStatsService.getStats(name) : null;
            if (stats != null)
            {
                totalStr   += stats.str;
                totalSlash += stats.aslash;
                sb.append("| ").append(slot).append(" | ").append(name)
                  .append(" | ").append(stats.str)
                  .append(" | ").append(stats.aslash)
                  .append(" | ").append(stats.acrush)
                  .append(" | ").append(stats.astab)
                  .append(" | ").append(stats.amagic)
                  .append(" | ").append(stats.arange)
                  .append(" | ").append(stats.prayer)
                  .append(" |\n");
            }
            else
            {
                sb.append("| ").append(slot).append(" | ").append(name)
                  .append(" | - | - | - | - | - | - | - |\n");
            }
        }
        sb.append("\n**Total strength bonus:** ").append(totalStr)
          .append("  **Total slash bonus:** ").append(totalSlash).append("\n");
        write("equipment.md", sb.toString());
    }

    // ── BANK ──────────────────────────────────────────────────────────────────

    public void writeBank(Item[] items)
    {
        if (items == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("# Bank Contents\n");
        sb.append("**Last updated:** ").append(now()).append("\n\n");

        long totalValue = 0;
        int coins = 0;
        List<String[]> rows = new ArrayList<>();

        for (Item item : items)
        {
            if (item.getId() <= 0 || item.getQuantity() <= 0) continue;
            if (item.getId() == 995) { coins = item.getQuantity(); continue; }
            String name = itemManager.getItemComposition(item.getId()).getName();
            if (name == null || name.equals("null")) continue;
            WikiPriceService.PriceData pd = wikiPriceService.getPrice(item.getId());
            long price = pd != null ? pd.low : 0;
            long stackVal = price * item.getQuantity();
            totalValue += stackVal;
            rows.add(new String[]{name, String.valueOf(item.getQuantity()),
                price > 0 ? gp(price) : "?", price > 0 ? gp(stackVal) : "?"});
        }

        // Sort by stack value desc
        rows.sort((a, b) -> {
            long av = parseLong(a[3]); long bv = parseLong(b[3]);
            return Long.compare(bv, av);
        });

        sb.append("**Total value:** ").append(gp(totalValue))
          .append("  **Coins:** ").append(gp(coins))
          .append("  **Unique items:** ").append(rows.size()).append("\n\n");
        sb.append("| Item | Qty | Price Each | Total Value |\n");
        sb.append("|------|-----|------------|-------------|\n");
        for (String[] row : rows)
            sb.append("| ").append(row[0]).append(" | ").append(row[1])
              .append(" | ").append(row[2]).append(" | ").append(row[3]).append(" |\n");
        write("bank.md", sb.toString());
    }

    // ── SEED VAULT ────────────────────────────────────────────────────────────

    public void writeSeedVault(Item[] items)
    {
        if (items == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("# Seed Vault\n");
        sb.append("**Last updated:** ").append(now()).append("\n\n");
        sb.append("| Item | Qty | Price Each | Total Value |\n");
        sb.append("|------|-----|------------|-------------|\n");
        for (Item item : items)
        {
            if (item.getId() <= 0 || item.getQuantity() <= 0) continue;
            String name = itemManager.getItemComposition(item.getId()).getName();
            if (name == null || name.equals("null")) continue;
            WikiPriceService.PriceData pd = wikiPriceService.getPrice(item.getId());
            long price = pd != null ? pd.low : 0;
            sb.append("| ").append(name).append(" | ").append(item.getQuantity())
              .append(" | ").append(price > 0 ? gp(price) : "?")
              .append(" | ").append(price > 0 ? gp(price * item.getQuantity()) : "?")
              .append(" |\n");
        }
        write("seed_vault.md", sb.toString());
    }

    // ── QUESTS ────────────────────────────────────────────────────────────────

    public void writeQuests(int questPoints, List<String> completed,
                            List<String> inProgress, List<String> notStarted)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("# Quests\n");
        sb.append("**Last updated:** ").append(now()).append("\n");
        sb.append("**Quest points:** ").append(questPoints).append("\n\n");
        sb.append("## Completed (").append(completed.size()).append(")\n");
        for (String q : completed) sb.append("- ").append(q).append("\n");
        sb.append("\n## In Progress (").append(inProgress.size()).append(")\n");
        for (String q : inProgress) sb.append("- ").append(q).append("\n");
        sb.append("\n## Not Started (").append(notStarted.size()).append(")\n");
        for (String q : notStarted) sb.append("- ").append(q).append("\n");
        write("quests.md", sb.toString());
    }

    // ── FARMING ───────────────────────────────────────────────────────────────

    public void writeFarming(Map<String, Object> patchData)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("# Herb Patches\n");
        sb.append("**Last updated:** ").append(now()).append("\n\n");
        sb.append("| Location | Herb | State | Est. Minutes Remaining | GE Price |\n");
        sb.append("|----------|------|-------|------------------------|----------|\n");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> patches = (List<Map<String, Object>>) patchData.get("patches");
        if (patches != null)
        {
            for (Map<String, Object> p : patches)
            {
                String loc   = (String) p.getOrDefault("location", "?");
                String herb  = (String) p.getOrDefault("herb", "?");
                String state = (String) p.getOrDefault("state", "?");
                Object mins  = p.get("est_minutes_remaining");
                Object price = p.get("herb_ge_price");
                sb.append("| ").append(loc)
                  .append(" | ").append(herb)
                  .append(" | ").append(state)
                  .append(" | ").append(mins != null ? mins : "-")
                  .append(" | ").append(price != null ? gp(((Number) price).longValue()) : "-")
                  .append(" |\n");
            }
        }
        write("farming.md", sb.toString());
    }

    // ── IO ────────────────────────────────────────────────────────────────────

    public String getCacheDir() { return CACHE_DIR; }

    private void write(String filename, String content)
    {
        if (cacheDir == null) return;
        File file = new File(cacheDir, filename);
        try (FileWriter fw = new FileWriter(file))
        {
            fw.write(content);
            log.debug("OSRS MCP: Wrote cache file: {}", filename);
        }
        catch (IOException e)
        {
            log.warn("OSRS MCP: Failed to write cache file {}: {}", filename, e.getMessage());
        }
    }

    private long parseLong(String s)
    {
        if (s == null || s.equals("?")) return 0;
        try { return Long.parseLong(s.replaceAll("[^0-9]", "")); }
        catch (NumberFormatException e) { return 0; }
    }
}
