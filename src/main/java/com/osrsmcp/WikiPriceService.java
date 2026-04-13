package com.osrsmcp;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches and caches live GE prices from the OSRS Wiki prices API.
 * Mapping (id/name/limit/examine) is loaded once at startup.
 * Live prices (high/low) are cached for 5 minutes.
 */
@Slf4j
@Singleton
public class WikiPriceService
{
    private static final String PRICES_URL  = "https://prices.runescape.wiki/api/v1/osrs/latest";
    private static final String MAPPING_URL = "https://prices.runescape.wiki/api/v1/osrs/mapping";
    private static final String USER_AGENT  = "osrs-mcp-plugin/1.0 (github.com/nickbeddows-ctrl/osrs-mcp-plugin)";
    private static final long   CACHE_TTL   = 5 * 60 * 1000L; // 5 minutes

    @Inject private OkHttpClient httpClient;
    @Inject private Gson gson;

    // id -> { name, examine, limit, highalch, lowalch }
    private final Map<Integer, ItemMeta> itemMeta  = new ConcurrentHashMap<>();
    // id -> { high, low, highTime, lowTime }
    private final Map<Integer, PriceData>  priceCache    = new ConcurrentHashMap<>();
    private final Map<Integer, VolumeData> volumeCache5m = new ConcurrentHashMap<>();
    private final Map<Integer, VolumeData> volumeCache1h = new ConcurrentHashMap<>();
    volatile long lastPriceFetch = 0;
    private volatile long last5mFetch    = 0;
    private volatile long last1hFetch    = 0;
    private static final long TTL_5M     = 5  * 60 * 1000L;
    private static final long TTL_1H     = 60 * 60 * 1000L;
    private volatile boolean mappingLoaded = false;

    // ── Data classes ──────────────────────────────────────────────────────────

    public static class ItemMeta
    {
        public int    id;
        public String name;
        public String examine;
        public int    limit;
        public int    highalch;
        public int    lowalch;
    }

    public static class PriceData
    {
        public int  high;
        public int  low;
        public long highTime;
        public long lowTime;

        public int margin()      { return high > 0 && low > 0 ? high - low : 0; }
        public double marginPct(){ return low  > 0 ? (margin() * 100.0 / low) : 0; }
    }

    public static class VolumeData
    {
        public int avgHigh;
        public int avgLow;
        public int highVol;
        public int lowVol;

        public int totalVol() { return highVol + lowVol; }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns price data for a specific item, fetching if cache is stale. */
    public PriceData getPrice(int itemId)
    {
        ensurePricesLoaded();
        return priceCache.get(itemId);
    }

    /** Returns prices for a list of item IDs. */
    public Map<Integer, PriceData> getPrices(List<Integer> itemIds)
    {
        ensurePricesLoaded();
        Map<Integer, PriceData> result = new java.util.LinkedHashMap<>();
        for (int id : itemIds)
        {
            PriceData p = priceCache.get(id);
            if (p != null) result.put(id, p);
        }
        return result;
    }

    public VolumeData getVolume5m(int itemId)
    {
        ensureVolumeLoaded(false);
        return volumeCache5m.get(itemId);
    }

    public VolumeData getVolume1h(int itemId)
    {
        ensureVolumeLoaded(true);
        return volumeCache1h.get(itemId);
    }

    public long getAge5mMs() { return System.currentTimeMillis() - last5mFetch; }
    public long getAge1hMs() { return System.currentTimeMillis() - last1hFetch; }

    public ItemMeta getMeta(int itemId)
    {
        ensureMappingLoaded();
        return itemMeta.get(itemId);
    }

    public Map<Integer, ItemMeta> getAllMeta()
    {
        ensureMappingLoaded();
        return itemMeta;
    }

    public boolean isPricesCacheStale()
    {
        return System.currentTimeMillis() - lastPriceFetch > CACHE_TTL;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void ensureVolumeLoaded(boolean oneHour)
    {
        String url  = "https://prices.runescape.wiki/api/v1/osrs/" + (oneHour ? "1h" : "5m");
        Map<Integer, VolumeData> cache = oneHour ? volumeCache1h : volumeCache5m;
        long lastFetch = oneHour ? last1hFetch : last5mFetch;
        long ttl       = oneHour ? TTL_1H : TTL_5M;

        if (System.currentTimeMillis() - lastFetch < ttl) return;
        try
        {
            String body = get(url);
            JsonObject root = new com.google.gson.JsonParser().parse(body).getAsJsonObject();
            JsonObject data = root.getAsJsonObject("data");
            if (data == null) return;

            for (Map.Entry<String, JsonElement> entry : data.entrySet())
            {
                try
                {
                    int id = Integer.parseInt(entry.getKey());
                    JsonObject obj = entry.getValue().getAsJsonObject();
                    VolumeData vd  = new VolumeData();
                    vd.avgHigh  = obj.has("avgHighPrice") && !obj.get("avgHighPrice").isJsonNull() ? obj.get("avgHighPrice").getAsInt() : 0;
                    vd.avgLow   = obj.has("avgLowPrice")  && !obj.get("avgLowPrice").isJsonNull()  ? obj.get("avgLowPrice").getAsInt()  : 0;
                    vd.highVol  = obj.has("highPriceVolume") ? obj.get("highPriceVolume").getAsInt() : 0;
                    vd.lowVol   = obj.has("lowPriceVolume")  ? obj.get("lowPriceVolume").getAsInt()  : 0;
                    cache.put(id, vd);
                }
                catch (Exception ignored) {}
            }
            if (oneHour) last1hFetch = System.currentTimeMillis();
            else         last5mFetch = System.currentTimeMillis();
            log.debug("OSRS MCP: Loaded {} volume entries ({})", cache.size(), oneHour ? "1h" : "5m");
        }
        catch (Exception e)
        {
            log.warn("OSRS MCP: Failed to fetch {} volume data: {}", oneHour ? "1h" : "5m", e.getMessage());
        }
    }

    private void ensureMappingLoaded()
    {
        if (mappingLoaded) return;
        try
        {
            String body = get(MAPPING_URL);
            List<ItemMeta> list = gson.fromJson(body,
                new TypeToken<List<ItemMeta>>(){}.getType());
            for (ItemMeta m : list) itemMeta.put(m.id, m);
            mappingLoaded = true;
            log.debug("OSRS MCP: Loaded {} item mappings from Wiki", itemMeta.size());
        }
        catch (Exception e)
        {
            log.warn("OSRS MCP: Failed to load Wiki item mapping: {}", e.getMessage());
        }
    }

    private void ensurePricesLoaded()
    {
        if (!isPricesCacheStale()) return;
        try
        {
            String body = get(PRICES_URL);
            JsonObject root = gson.fromJson(body, JsonObject.class);
            JsonObject data = root.getAsJsonObject("data");
            if (data == null) return;

            for (Map.Entry<String, JsonElement> entry : data.entrySet())
            {
                try
                {
                    int id = Integer.parseInt(entry.getKey());
                    JsonObject obj = entry.getValue().getAsJsonObject();
                    PriceData pd  = new PriceData();
                    pd.high       = obj.has("high")     && !obj.get("high").isJsonNull()     ? obj.get("high").getAsInt()     : 0;
                    pd.low        = obj.has("low")      && !obj.get("low").isJsonNull()      ? obj.get("low").getAsInt()      : 0;
                    pd.highTime   = obj.has("highTime") && !obj.get("highTime").isJsonNull() ? obj.get("highTime").getAsLong(): 0;
                    pd.lowTime    = obj.has("lowTime")  && !obj.get("lowTime").isJsonNull()  ? obj.get("lowTime").getAsLong() : 0;
                    priceCache.put(id, pd);
                }
                catch (Exception ignored) {}
            }
            lastPriceFetch = System.currentTimeMillis();
            log.debug("OSRS MCP: Refreshed prices for {} items", priceCache.size());
        }
        catch (Exception e)
        {
            log.warn("OSRS MCP: Failed to fetch Wiki prices: {}", e.getMessage());
        }
    }

    private String get(String url) throws Exception
    {
        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build();
        try (Response response = httpClient.newCall(request).execute())
        {
            if (!response.isSuccessful())
                throw new Exception("HTTP " + response.code());
            return response.body().string();
        }
    }
}
