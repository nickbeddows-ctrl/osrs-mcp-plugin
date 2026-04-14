package com.osrsmcp;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.*;

/**
 * Reads herb patch states from the Time Tracking plugin's stored config.
 * Works even when the player is not near the patches.
 * Also reads live from varbits when available.
 */
@Slf4j
@Singleton
public class FarmingPatchService
{
    private static final String TT_GROUP = "timetracking";
    // Minutes per growth stage for herbs
    private static final int HERB_STAGE_MINUTES = 20;
    private static final int HERB_STAGES = 4;

    @Inject private net.runelite.api.Client client;
    @Inject private ConfigManager configManager;
    @Inject private net.runelite.client.game.ItemManager itemManager;
    @Inject private WikiPriceService wikiPriceService;

    // Herb patch definitions: name, regionID, varbitID
    private static final int[][] HERB_PATCHES = {
        // { regionID, varbitID } -- varbit IDs from VarbitID.FARMING_TRANSMIT_*
        {10548, 4774}, // Ardougne         (TRANSMIT_D)
        {11062, 4774}, // Catherby          (TRANSMIT_D)
        { 6192, 4774}, // Civitas illa Fortis (TRANSMIT_D)
        {12083, 4774}, // Falador           (TRANSMIT_D)
        { 6967, 4774}, // Kourend           (TRANSMIT_D)
        {14391, 4774}, // Morytania         (TRANSMIT_D)
        {11321, 4771}, // Troll Stronghold  (TRANSMIT_A)
        {11325, 4771}, // Weiss             (TRANSMIT_A)
        { 5021, 4775}, // Farming Guild     (TRANSMIT_E)
        {15148, 4772}, // Harmony           (TRANSMIT_B)
    };

    private static final String[] PATCH_NAMES = {
        "Ardougne", "Catherby", "Civitas illa Fortis", "Falador",
        "Kourend", "Morytania", "Troll Stronghold", "Weiss",
        "Farming Guild", "Harmony"
    };

    // Herb varbit decode table -- each entry: {minVal, maxVal, herbName, isHarvestable}
    // Pattern: 4 growing stages then 3 harvestable stages per herb, 7 values each
    // Diseased/dead ranges are 100+ for herbs (simplified: treat as diseased/dead)
    private static final Object[][] HERB_DECODE = {
        {0,  3,  "Weeds",        false},
        {4,  7,  "Guam",         false},
        {8,  10, "Guam",         true},
        {11, 14, "Marrentill",   false},
        {15, 17, "Marrentill",   true},
        {18, 21, "Tarromin",     false},
        {22, 24, "Tarromin",     true},
        {25, 28, "Harralander",  false},
        {29, 31, "Harralander",  true},
        {32, 35, "Ranarr",       false},
        {36, 38, "Ranarr",       true},
        {39, 42, "Toadflax",     false},
        {43, 45, "Toadflax",     true},
        {46, 49, "Irit",         false},
        {50, 52, "Irit",         true},
        {53, 56, "Avantoe",      false},
        {57, 59, "Avantoe",      true},
        {60, 63, "Huasca",       false},
        {64, 66, "Huasca",       true},
        {67, 67, "Weeds",        false},
        {68, 71, "Kwuarm",       false},
        {72, 74, "Kwuarm",       true},
        {75, 78, "Snapdragon",   false},
        {79, 81, "Snapdragon",   true},
        {82, 85, "Cadantine",    false},
        {86, 88, "Cadantine",    true},
        {89, 92, "Lantadyme",    false},
        {93, 95, "Lantadyme",    true},
        {96, 99, "Dwarf weed",   false},
        {100,102,"Dwarf weed",   true},
        {103,106,"Torstol",      false},
        {107,109,"Torstol",      true},
    };

    public Map<String, Object> buildFarmingPatches()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> patches = new ArrayList<>();
        int readyCount = 0, growingCount = 0, emptyCount = 0, otherCount = 0;

        for (int i = 0; i < HERB_PATCHES.length; i++)
        {
            int regionId = HERB_PATCHES[i][0];
            int varbitId = HERB_PATCHES[i][1];
            String patchName = PATCH_NAMES[i];

            String key = regionId + "." + varbitId;
            String stored = configManager.getRSProfileConfiguration(TT_GROUP, key);

            Map<String, Object> patch = new LinkedHashMap<>();
            patch.put("location", patchName);

            if (stored == null)
            {
                patch.put("state",   "unknown");
                patch.put("message", "Visit this patch to update its state");
                otherCount++;
            }
            else
            {
                String[] parts = stored.split(":");
                int varbitVal = 0;
                long storedTime = 0;
                try
                {
                    varbitVal  = Integer.parseInt(parts[0]);
                    if (parts.length > 1) storedTime = Long.parseLong(parts[1]);
                }
                catch (NumberFormatException ignored) {}

                String[] decoded = decodeHerbVarbit(varbitVal);
                String herbName   = decoded[0];
                String cropState  = decoded[1];

                patch.put("herb",  herbName);
                patch.put("state", cropState);

                long ageSeconds = Instant.now().getEpochSecond() - storedTime;
                patch.put("data_age_minutes", ageSeconds / 60);

                if ("growing".equals(cropState))
                {
                    int stage = varbitVal % 4; // approximate stage within herb
                    int stagesLeft = HERB_STAGES - stage;
                    long minutesLeft = (long) stagesLeft * HERB_STAGE_MINUTES - (ageSeconds / 60);
                    patch.put("est_minutes_remaining", Math.max(0, minutesLeft));
                    growingCount++;
                }
                else if ("harvestable".equals(cropState))
                {
                    // Attach GE price for the herb
                    attachHerbPrice(patch, herbName);
                    readyCount++;
                }
                else if ("empty".equals(cropState))
                {
                    emptyCount++;
                }
                else
                {
                    otherCount++;
                }
            }
            patches.add(patch);
        }

        result.put("ready",        readyCount);
        result.put("growing",      growingCount);
        result.put("empty",        emptyCount);
        result.put("other",        otherCount);
        result.put("patches",      patches);
        result.put("note",         "State is cached from last visit. Visit patches to refresh.");
        return result;
    }

    private String[] decodeHerbVarbit(int value)
    {
        // Diseased range (simplified -- actual ranges are 110-168 for diseased, 169+ for dead)
        if (value >= 110 && value <= 168) return new String[]{"Unknown herb", "diseased"};
        if (value >= 169 && value <= 228) return new String[]{"Unknown herb", "dead"};

        for (Object[] row : HERB_DECODE)
        {
            int min = (int) row[0], max = (int) row[1];
            if (value >= min && value <= max)
            {
                String herb  = (String) row[2];
                boolean harv = (boolean) row[3];
                if ("Weeds".equals(herb)) return new String[]{"Weeds", "empty"};
                return new String[]{herb, harv ? "harvestable" : "growing"};
            }
        }
        return new String[]{"Unknown", "unknown"};
    }

    private void attachHerbPrice(Map<String, Object> patch, String herbName)
    {
        // Try to find the grimy herb item name
        String grimyName = "Grimy " + herbName.toLowerCase();
        Map<Integer, WikiPriceService.ItemMeta> allMeta = wikiPriceService.getAllMeta();
        for (Map.Entry<Integer, WikiPriceService.ItemMeta> entry : allMeta.entrySet())
        {
            WikiPriceService.ItemMeta meta = entry.getValue();
            if (meta == null || meta.name == null) continue;
            if (meta.name.equalsIgnoreCase(grimyName))
            {
                WikiPriceService.PriceData pd = wikiPriceService.getPrice(entry.getKey());
                if (pd != null && pd.low > 0)
                {
                    patch.put("herb_ge_price", pd.low);
                    patch.put("herb_item_name", meta.name);
                }
                break;
            }
        }
    }
}
