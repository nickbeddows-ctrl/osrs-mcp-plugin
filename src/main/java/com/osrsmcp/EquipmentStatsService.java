package com.osrsmcp;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches and caches equipment stat bonuses from the OSRS Wiki.
 * Cache TTL is 24 hours -- stats change only during balance patches.
 */
@Slf4j
@Singleton
public class EquipmentStatsService
{
    private static final String USER_AGENT = "osrs-mcp-plugin/1.0 (github.com/nickbeddows-ctrl/osrs-mcp-plugin)";
    private static final long   CACHE_TTL  = 24 * 60 * 60 * 1000L;

    @Inject private OkHttpClient httpClient;

    public static class EquipmentStats
    {
        public String name;
        public String slot;
        public int astab, aslash, acrush, amagic, arange;
        public int dstab, dslash, dcrush, dmagic, drange;
        public int str, prayer, speed;
        public int rstr;   // ranged strength bonus
        public int mdmg;   // magic damage % bonus
        public long fetchedAt;

        public Map<String, Object> toMap()
        {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name",   name);
            m.put("slot",   slot);
            m.put("attack_bonuses",  bonusMap("stab", astab, "slash", aslash, "crush", acrush, "magic", amagic, "ranged", arange));
            m.put("defence_bonuses", bonusMap("stab", dstab, "slash", dslash, "crush", dcrush, "magic", dmagic, "ranged", drange));
            m.put("other_bonuses",   bonusMap("strength", str, "ranged_strength", rstr, "magic_damage_pct", mdmg, "prayer", prayer));
            if (speed > 0) m.put("attack_speed", speed);
            return m;
        }

        private Map<String, Object> bonusMap(Object... kv)
        {
            Map<String, Object> m = new LinkedHashMap<>();
            for (int i = 0; i < kv.length - 1; i += 2)
                m.put((String) kv[i], kv[i + 1]);
            return m;
        }
    }

    // item name (lowercase) -> cached stats
    private final Map<String, EquipmentStats> cache = new ConcurrentHashMap<>();

    public EquipmentStats getStats(String itemName)
    {
        if (itemName == null || itemName.isEmpty() || itemName.equals("null")) return null;
        String key = itemName.toLowerCase();
        EquipmentStats cached = cache.get(key);
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < CACHE_TTL)
            return cached;

        try
        {
            String pageTitle = toPageTitle(itemName);
            String url = "https://oldschool.runescape.wiki/w/Special:Export/" + pageTitle;
            Request req = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();
            String body;
            try (Response resp = httpClient.newCall(req).execute())
            {
                if (!resp.isSuccessful()) return null;
                body = resp.body().string();
            }

            String wikiText = extractWikiText(body);
            if (wikiText == null) return null;

            EquipmentStats stats = new EquipmentStats();
            stats.name      = itemName;
            stats.fetchedAt = System.currentTimeMillis();
            stats.slot      = field(wikiText, "slot");
            stats.astab     = intField(wikiText, "astab");
            stats.aslash    = intField(wikiText, "aslash");
            stats.acrush    = intField(wikiText, "acrush");
            stats.amagic    = intField(wikiText, "amagic");
            stats.arange    = intField(wikiText, "arange");
            stats.dstab     = intField(wikiText, "dstab");
            stats.dslash    = intField(wikiText, "dslash");
            stats.dcrush    = intField(wikiText, "dcrush");
            stats.dmagic    = intField(wikiText, "dmagic");
            stats.drange    = intField(wikiText, "drange");
            stats.str       = intField(wikiText, "str");
            stats.prayer    = intField(wikiText, "prayer");
            stats.speed     = intField(wikiText, "speed");
            stats.rstr      = intField(wikiText, "rstr");
            stats.mdmg      = intField(wikiText, "mdmg");

            if (stats.slot != null)
            {
                cache.put(key, stats);
                log.debug("OSRS MCP: Cached equipment stats for {}", itemName);
                return stats;
            }
            return null;
        }
        catch (Exception e)
        {
            log.warn("OSRS MCP: Failed to fetch stats for {}: {}", itemName, e.getMessage());
            return null;
        }
    }

    private String toPageTitle(String name)
    {
        // Capitalise first letter of each word, replace spaces with underscores
        String[] words = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words)
            if (!w.isEmpty())
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append("_");
        String title = sb.toString();
        if (title.endsWith("_")) title = title.substring(0, title.length() - 1);
        return java.net.URLEncoder.encode(title, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String extractWikiText(String xml)
    {
        int s = xml.indexOf("<text");
        int e = xml.indexOf("</text>");
        if (s < 0 || e < 0) return null;
        int c = xml.indexOf(">", s) + 1;
        return xml.substring(c, e);
    }

    private String field(String text, String key)
    {
        Matcher m = Pattern.compile("\\|\\s*" + Pattern.quote(key) + "\\s*=\\s*([^\\|\\n\\}]+)").matcher(text);
        if (!m.find()) return null;
        String val = m.group(1).replaceAll("\\[\\[([^\\|\\]]+)\\|?[^\\]]*\\]\\]", "$1")
                               .replaceAll("\\{\\{[^\\}]+\\}\\}", "")
                               .replaceAll("<[^>]+>", "").trim();
        return val.isEmpty() || val.equals("N/A") ? null : val;
    }

    private int intField(String text, String key)
    {
        String val = field(text, key);
        if (val == null) return 0;
        try { return Integer.parseInt(val.replaceAll("[^\\-0-9]", "")); }
        catch (NumberFormatException e) { return 0; }
    }
}
