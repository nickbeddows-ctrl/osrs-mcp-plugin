package com.osrsmcp;

import net.runelite.api.*;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Prayer;
import net.runelite.api.NPC;
import net.runelite.api.WorldType;
import net.runelite.api.Varbits;
import net.runelite.api.VarPlayer;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Singleton
public class PlayerDataService
{
    @Inject private Client client;
    @Inject private ItemManager itemManager;
    @Inject private OsrsMcpConfig config;
    @Inject private PluginManager pluginManager;
    @Inject private WikiPriceService wikiPriceService;

    // Bank cache -- populated when player opens their bank
    private volatile Item[] cachedBankItems = null;

    public boolean isLoggedIn()
    {
        return client.getGameState() == GameState.LOGGED_IN;
    }

    public Map<String, Object> buildSnapshot()
    {
        Map<String, Object> data = new LinkedHashMap<>();
        if (!isLoggedIn()) { data.put("error", "Player is not logged in"); return data; }
        if (config.shareStats())     data.put("stats",     buildStats());
        if (config.shareEquipment()) data.put("equipment", buildEquipment());
        if (config.shareInventory()) data.put("inventory", buildInventory());
        if (config.shareLocation())  data.put("location",  buildLocation());
        data.put("quests",  buildQuestStates());
        data.put("diaries", buildDiaryStates());
        data.put("nearby_npcs",   buildNearbyNpcs());
        data.put("world",          buildWorldInfo());
        data.put("prayers",       buildPrayers());
        data.put("bank_summary",  buildBankSummary());
        data.put("collection_log", buildCollectionLog());
        data.put("plugins", buildInstalledPlugins());
        data.put("slayer",  buildSlayerTask());
        data.put("clue",    buildClueScroll());
        data.put("ge",      buildGeOffers());
        data.put("nearby_npcs",   buildNearbyNpcs());
        data.put("world",          buildWorldInfo());
        data.put("prayers",       buildPrayers());
        data.put("bank_summary",  buildBankSummary());
        data.put("collection_log", buildCollectionLog());
        data.put("plugins", buildInstalledPlugins());
        return data;
    }

    public Map<String, Object> buildStats()
    {
        if (!isLoggedIn()) return errorMap("Player is not logged in");
        Map<String, Object> result = new LinkedHashMap<>();
        if (config.shareUsername()) result.put("username", client.getLocalPlayer().getName());
        result.put("combat_level", client.getLocalPlayer().getCombatLevel());
        Map<String, Map<String, Object>> skills = new LinkedHashMap<>();
        for (Skill skill : Skill.values())
        {
            if (skill == Skill.OVERALL) continue;
            int level   = client.getRealSkillLevel(skill);
            int boosted = client.getBoostedSkillLevel(skill);
            int xp      = client.getSkillExperience(skill);
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("level", level);
            if (boosted != level) s.put("boosted_level", boosted);
            s.put("xp", xp);
            s.put("xp_to_next_level", xpToNextLevel(xp, level));
            skills.put(skill.getName().toLowerCase(), s);
        }
        result.put("skills", skills);
        return result;
    }

    public List<Map<String, Object>> buildEquipment()
    {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!isLoggedIn()) return result;
        ItemContainer container = client.getItemContainer(InventoryID.EQUIPMENT);
        if (container == null) return result;
        String[] slotNames = {"head","cape","amulet","weapon","body","shield","unused","legs","gloves","boots","ring","ammo"};
        Item[] items = container.getItems();
        for (int i = 0; i < items.length; i++)
        {
            if (items[i].getId() <= 0) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("slot", (i < slotNames.length) ? slotNames[i] : "slot_" + i);
            entry.put("id", items[i].getId());
            entry.put("name", itemManager.getItemComposition(items[i].getId()).getName());
            entry.put("quantity", items[i].getQuantity());
            result.add(entry);
        }
        return result;
    }

    public List<Map<String, Object>> buildInventory()
    {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!isLoggedIn()) return result;
        ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);
        if (container == null) return result;
        Map<String, Map<String, Object>> collapsed = new LinkedHashMap<>();
        for (Item item : container.getItems())
        {
            if (item.getId() <= 0) continue;
            String name = itemManager.getItemComposition(item.getId()).getName();
            if (collapsed.containsKey(name))
            {
                int qty = (int) collapsed.get(name).get("quantity");
                collapsed.get(name).put("quantity", qty + item.getQuantity());
            }
            else
            {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", item.getId()); entry.put("name", name); entry.put("quantity", item.getQuantity());
                collapsed.put(name, entry);
            }
        }
        result.addAll(collapsed.values());
        return result;
    }

    public Map<String, Object> buildLocation()
    {
        if (!isLoggedIn()) return errorMap("Player is not logged in");
        Map<String, Object> result = new LinkedHashMap<>();
        WorldPoint wp = client.getLocalPlayer().getWorldLocation();
        result.put("x", wp.getX()); result.put("y", wp.getY()); result.put("plane", wp.getPlane());
        result.put("region_id", wp.getRegionID());
        String area = RegionNames.getName(wp.getRegionID());
        if (area != null) result.put("area", area);
        return result;
    }

    public Map<String, Object> buildQuestStates()
    {
        if (!isLoggedIn()) return errorMap("Player is not logged in");
        Map<String, Object> result = new LinkedHashMap<>();

        List<Map<String, Object>> finished    = new ArrayList<>();
        List<Map<String, Object>> inProgress  = new ArrayList<>();
        List<Map<String, Object>> notStarted  = new ArrayList<>();
        for (Quest quest : Quest.values())
        {
            QuestState state = quest.getState(client);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", quest.getName());
            entry.put("state", state.name().toLowerCase());
            switch (state)
            {
                case FINISHED:   finished.add(entry);  break;
                case IN_PROGRESS: inProgress.add(entry); break;
                case NOT_STARTED: notStarted.add(entry); break;
            }
        }

        result.put("quest_points", client.getVarpValue(VarPlayer.QUEST_POINTS));
        result.put("completed_count", finished.size());
        result.put("in_progress_count", inProgress.size());
        result.put("not_started_count", notStarted.size());
        result.put("completed", finished);
        result.put("in_progress", inProgress);
        result.put("not_started", notStarted);
        return result;
    }

    public Map<String, Object> buildDiaryStates()
    {
        if (!isLoggedIn()) return errorMap("Player is not logged in");

        // Each entry: region -> { easy, medium, hard, elite } (1 = complete, 0 = not)
        int[][] diaries = {
            {Varbits.DIARY_ARDOUGNE_EASY,  Varbits.DIARY_ARDOUGNE_MEDIUM,  Varbits.DIARY_ARDOUGNE_HARD,  Varbits.DIARY_ARDOUGNE_ELITE},
            {Varbits.DIARY_DESERT_EASY,    Varbits.DIARY_DESERT_MEDIUM,    Varbits.DIARY_DESERT_HARD,    Varbits.DIARY_DESERT_ELITE},
            {Varbits.DIARY_FALADOR_EASY,   Varbits.DIARY_FALADOR_MEDIUM,   Varbits.DIARY_FALADOR_HARD,   Varbits.DIARY_FALADOR_ELITE},
            {Varbits.DIARY_FREMENNIK_EASY, Varbits.DIARY_FREMENNIK_MEDIUM, Varbits.DIARY_FREMENNIK_HARD, Varbits.DIARY_FREMENNIK_ELITE},
            {Varbits.DIARY_KANDARIN_EASY,  Varbits.DIARY_KANDARIN_MEDIUM,  Varbits.DIARY_KANDARIN_HARD,  Varbits.DIARY_KANDARIN_ELITE},
            {Varbits.DIARY_KARAMJA_EASY,   Varbits.DIARY_KARAMJA_MEDIUM,   Varbits.DIARY_KARAMJA_HARD,   Varbits.DIARY_KARAMJA_ELITE},
            {Varbits.DIARY_KOUREND_EASY,   Varbits.DIARY_KOUREND_MEDIUM,   Varbits.DIARY_KOUREND_HARD,   Varbits.DIARY_KOUREND_ELITE},
            {Varbits.DIARY_LUMBRIDGE_EASY, Varbits.DIARY_LUMBRIDGE_MEDIUM, Varbits.DIARY_LUMBRIDGE_HARD, Varbits.DIARY_LUMBRIDGE_ELITE},
            {Varbits.DIARY_MORYTANIA_EASY, Varbits.DIARY_MORYTANIA_MEDIUM, Varbits.DIARY_MORYTANIA_HARD, Varbits.DIARY_MORYTANIA_ELITE},
            {Varbits.DIARY_VARROCK_EASY,   Varbits.DIARY_VARROCK_MEDIUM,   Varbits.DIARY_VARROCK_HARD,   Varbits.DIARY_VARROCK_ELITE},
            {Varbits.DIARY_WESTERN_EASY,   Varbits.DIARY_WESTERN_MEDIUM,   Varbits.DIARY_WESTERN_HARD,   Varbits.DIARY_WESTERN_ELITE},
            {Varbits.DIARY_WILDERNESS_EASY,Varbits.DIARY_WILDERNESS_MEDIUM,Varbits.DIARY_WILDERNESS_HARD,Varbits.DIARY_WILDERNESS_ELITE},
        };
        String[] regions = {"ardougne","desert","falador","fremennik","kandarin","karamja",
                             "kourend","lumbridge","morytania","varrock","western_provinces","wilderness"};
        String[] tiers   = {"easy","medium","hard","elite"};

        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < regions.length; i++)
        {
            Map<String, Object> region = new LinkedHashMap<>();
            for (int j = 0; j < tiers.length; j++)
                region.put(tiers[j], client.getVarbitValue(diaries[i][j]) == 1);
            result.put(regions[i], region);
        }
        return result;
    }

        public Map<String, Object> buildSlayerTask()
    {
        if (!isLoggedIn()) return errorMap("Player is not logged in");
        Map<String, Object> result = new LinkedHashMap<>();

        int remaining = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
        if (remaining <= 0)
        {
            result.put("task", null);
            result.put("remaining", 0);
        }
        else
        {
            // Look up task name from game DB
            String taskName = null;
            try
            {
                int taskId = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
                List<Integer> taskRows = client.getDBRowsByValue(
                    DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, taskId);
                if (!taskRows.isEmpty())
                    taskName = (String) client.getDBTableField(
                        taskRows.get(0), DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)[0];
            }
            catch (Exception e) { /* task name unavailable */ }

            result.put("task", taskName);
            result.put("remaining", remaining);
            result.put("initial_amount", client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL));

            // Location (e.g. "Catacombs of Kourend")
            try
            {
                int areaId = client.getVarpValue(VarPlayerID.SLAYER_AREA);
                if (areaId > 0)
                {
                    List<Integer> areaRows = client.getDBRowsByValue(
                        DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, 0, areaId);
                    if (!areaRows.isEmpty())
                        result.put("location", client.getDBTableField(
                            areaRows.get(0), DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, 0)[0]);
                }
            }
            catch (Exception e) { /* location unavailable */ }
        }

        result.put("points", client.getVarbitValue(VarbitID.SLAYER_POINTS));
        result.put("streak", client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED));
        return result;
    }

    public Map<String, Object> buildClueScroll()
    {
        if (!isLoggedIn()) return errorMap("Player is not logged in");
        Map<String, Object> result = new LinkedHashMap<>();

        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        String tier = null;

        if (inventory != null)
        {
            for (Item item : inventory.getItems())
            {
                if (item.getId() <= 0) continue;
                String name = itemManager.getItemComposition(item.getId()).getName().toLowerCase();
                if (!name.contains("clue scroll")) continue;
                if      (name.contains("beginner")) { tier = "beginner"; break; }
                else if (name.contains("easy"))     { tier = "easy";     break; }
                else if (name.contains("medium"))   { tier = "medium";   break; }
                else if (name.contains("hard"))     { tier = "hard";     break; }
                else if (name.contains("elite"))    { tier = "elite";    break; }
                else if (name.contains("master"))   { tier = "master";   break; }
            }
        }

        result.put("active", tier != null);
        result.put("tier", tier);
        return result;
    }

    public List<Map<String, Object>> buildGeOffers()
    {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!isLoggedIn()) return result;

        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        if (offers == null) return result;

        for (int i = 0; i < offers.length; i++)
        {
            GrandExchangeOffer offer = offers[i];
            if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("slot", i + 1);
            entry.put("state", offer.getState().name().toLowerCase());
            entry.put("item_id", offer.getItemId());
            entry.put("item_name", itemManager.getItemComposition(offer.getItemId()).getName());
            entry.put("quantity_traded", offer.getQuantitySold());
            entry.put("total_quantity", offer.getTotalQuantity());
            entry.put("price_per_item", offer.getPrice());
            entry.put("total_spent", offer.getSpent());
            result.add(entry);
        }
        return result;
    }

        public Map<String, Object> buildInstalledPlugins()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> pluginList = new ArrayList<>();

        for (Plugin plugin : pluginManager.getPlugins())
        {
            PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
            if (descriptor == null) continue;
            if (descriptor.hidden()) continue;

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", descriptor.name());
            entry.put("enabled", pluginManager.isPluginEnabled(plugin));

            // Distinguish built-in vs Plugin Hub by package name
            String pkg = plugin.getClass().getPackageName();
            entry.put("type", pkg.startsWith("net.runelite.client.plugins") ? "builtin" : "hub");

            pluginList.add(entry);
        }

        // Sort: hub plugins first, then alphabetically
        pluginList.sort((a, b) -> {
            int typeCompare = ((String) a.get("type")).compareTo((String) b.get("type"));
            if (typeCompare != 0) return typeCompare;
            return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
        });

        result.put("total", pluginList.size());
        result.put("enabled_count", pluginList.stream().filter(p -> (Boolean) p.get("enabled")).count());
        result.put("plugins", pluginList);
        return result;
    }

        /** Called by OsrsMcpPlugin when a bank container event fires. */
    public void onBankChanged(ItemContainerChanged event)
    {
        if (event.getContainerId() == InventoryID.BANK.getId())
            cachedBankItems = event.getItemContainer().getItems();
    }

    public Map<String, Object> buildCollectionLog()
    {
        if (!isLoggedIn()) return errorMap("Player is not logged in");
        Map<String, Object> result = new LinkedHashMap<>();

        int total    = client.getVarpValue(VarPlayerID.COLLECTION_COUNT);
        int totalMax = client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX);

        result.put("unique_obtained", total);
        result.put("unique_total",    totalMax);
        result.put("completion_percent", totalMax > 0
            ? Math.round((total * 1000.0 / totalMax)) / 10.0 : 0.0);

        // Per-category breakdown
        Map<String, Object> categories = new LinkedHashMap<>();
        addCategory(categories, "bosses",    VarPlayerID.COLLECTION_COUNT_BOSSES,    VarPlayerID.COLLECTION_COUNT_BOSSES_MAX);
        addCategory(categories, "raids",     VarPlayerID.COLLECTION_COUNT_RAIDS,     VarPlayerID.COLLECTION_COUNT_RAIDS_MAX);
        addCategory(categories, "clues",     VarPlayerID.COLLECTION_COUNT_CLUES,     VarPlayerID.COLLECTION_COUNT_CLUES_MAX);
        addCategory(categories, "minigames", VarPlayerID.COLLECTION_COUNT_MINIGAMES, VarPlayerID.COLLECTION_COUNT_MINIGAMES_MAX);
        addCategory(categories, "other",     VarPlayerID.COLLECTION_COUNT_OTHER,     VarPlayerID.COLLECTION_COUNT_OTHER_MAX);
        result.put("categories", categories);

        return result;
    }

    private void addCategory(Map<String, Object> map, String name, int obtainedVar, int maxVar)
    {
        int obtained = client.getVarpValue(obtainedVar);
        int max      = client.getVarpValue(maxVar);
        Map<String, Object> cat = new LinkedHashMap<>();
        cat.put("obtained", obtained);
        cat.put("total",    max);
        map.put(name, cat);
    }

        public Map<String, Object> buildPrayers()
    {
        if (!isLoggedIn()) return errorMap("Player is not logged in");

        // Active prayers (currently toggled on)
        List<String> active = new ArrayList<>();
        for (Prayer prayer : Prayer.values())
        {
            if (client.getVarbitValue(prayer.getVarbit()) == 1)
                active.add(formatPrayerName(prayer.name()));
        }

        // Unlock status for prayers that require specific unlocks beyond Prayer level
        Map<String, Object> unlocks = new LinkedHashMap<>();
        unlocks.put("preserve", client.getVarbitValue(VarbitID.PRAYER_PRESERVE_UNLOCKED) == 1);
        unlocks.put("rigour",   client.getVarbitValue(VarbitID.PRAYER_RIGOUR_UNLOCKED)   == 1);
        unlocks.put("augury",   client.getVarbitValue(VarbitID.PRAYER_AUGURY_UNLOCKED)   == 1);
        // Chivalry and Piety unlock via King's Ransom + Knight Waves Training Grounds
        boolean kingsRansomDone = Quest.KINGS_RANSOM.getState(client) == QuestState.FINISHED;
        unlocks.put("chivalry_and_piety_quest_done", kingsRansomDone);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active_prayers", active);
        result.put("special_unlocks", unlocks);
        return result;
    }

    private String formatPrayerName(String enumName)
    {
        // Convert THICK_SKIN -> Thick Skin, RP_REJUVENATION -> Ruinous Powers: Rejuvenation
        if (enumName.startsWith("RP_"))
        {
            String rest = enumName.substring(3).replace("_", " ");
            return "Ruinous Powers: " + capitalise(rest);
        }
        return capitalise(enumName.replace("_", " "));
    }

    private String capitalise(String s)
    {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        for (String word : s.split(" "))
        {
            if (!word.isEmpty())
            {
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1).toLowerCase());
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }

        public Map<String, Object> buildNearbyNpcs()
    {
        if (!isLoggedIn()) return errorMap("Player is not logged in");

        List<NPC> npcs = client.getNpcs();
        List<Map<String, Object>> result = new ArrayList<>();

        for (NPC npc : npcs)
        {
            if (npc == null || npc.getName() == null) continue;
            // Skip dead NPCs and untargettable NPCs (id -1)
            if (npc.isDead() || npc.getId() < 0) continue;

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", npc.getName());
            entry.put("combat_level", npc.getCombatLevel());
            entry.put("id", npc.getId());

            // Health ratio (approximate %, -1 if unknown)
            int healthRatio = npc.getHealthRatio();
            int healthScale = npc.getHealthScale();
            if (healthRatio >= 0 && healthScale > 0)
                entry.put("health_percent", Math.round(healthRatio * 100.0 / healthScale));

            // Location
            if (npc.getWorldLocation() != null)
            {
                entry.put("x", npc.getWorldLocation().getX());
                entry.put("y", npc.getWorldLocation().getY());
            }

            result.add(entry);
        }

        // Sort by combat level descending, then name
        result.sort((a, b) -> {
            int cl = Integer.compare((int) b.get("combat_level"), (int) a.get("combat_level"));
            return cl != 0 ? cl : ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
        });

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", result.size());
        out.put("npcs", result);
        return out;
    }

    public Map<String, Object> buildWorldInfo()
    {
        if (!isLoggedIn()) return errorMap("Player is not logged in");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("world", client.getWorld());

        java.util.EnumSet<WorldType> types = client.getWorldType();
        result.put("members",    types.contains(WorldType.MEMBERS));
        result.put("pvp",        WorldType.isPvpWorld(types));
        result.put("high_risk",  types.contains(WorldType.HIGH_RISK));
        result.put("deadman",    types.contains(WorldType.DEADMAN));
        result.put("seasonal",   types.contains(WorldType.SEASONAL));
        result.put("skill_total",types.contains(WorldType.SKILL_TOTAL));
        result.put("bounty",     types.contains(WorldType.BOUNTY));

        // Friendly world type label
        String label = "Standard";
        if (types.contains(WorldType.DEADMAN))         label = "Deadman";
        else if (types.contains(WorldType.SEASONAL))   label = "Seasonal";
        else if (WorldType.isPvpWorld(types))          label = "PvP";
        else if (types.contains(WorldType.HIGH_RISK))  label = "High Risk";
        else if (types.contains(WorldType.BOUNTY))     label = "Bounty";
        else if (types.contains(WorldType.SKILL_TOTAL)) label = "Skill Total";
        result.put("type_label", label);

        return result;
    }

        private static final int[] XP_TABLE = {
        0,83,174,276,388,512,650,801,969,1154,1358,1584,1833,2107,2411,2746,3115,3523,3973,4470,
        5018,5624,6291,7028,7842,8740,9730,10824,12031,13363,14833,16456,18247,20224,22406,24815,
        27473,30408,33648,37224,41171,45529,50339,55649,61512,67983,75127,83014,91721,101333,
        111945,123660,136594,150872,166636,184040,203254,224466,247886,273742,302288,333804,
        368599,407015,449428,496254,547953,605032,668051,737627,814445,898160,989541,1089405,
        1199645,1320268,1453332,1598958,1758394,1931443,2119909,2325765,2551086,2798000,3068002,
        3363802,3687199,4041004,4428042,4857053,5330438,5851170,6424087,7053430,7743043,8504299,
        9334418,10240095,11221117,12282218,13428671,14664990
    };

    private int xpToNextLevel(int xp, int level)
    {
        if (level >= 99) return 0;
        return Math.max(0, XP_TABLE[level] - xp);
    }

    private Map<String, Object> errorMap(String message)
    {
        Map<String, Object> m = new LinkedHashMap<>(); m.put("error", message); return m;
    }

    public Map<String, Object> buildPriceTrends(List<Integer> itemIds)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();

        for (int id : itemIds)
        {
            WikiPriceService.PriceData  latest = wikiPriceService.getPrice(id);
            WikiPriceService.VolumeData vol5m  = wikiPriceService.getVolume5m(id);
            WikiPriceService.VolumeData vol1h  = wikiPriceService.getVolume1h(id);
            WikiPriceService.ItemMeta   meta   = wikiPriceService.getMeta(id);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id",   id);
            item.put("name", meta != null ? meta.name : itemManager.getItemComposition(id).getName());

            if (latest != null)
            {
                item.put("current_buy",  latest.high);
                item.put("current_sell", latest.low);
                item.put("margin",       latest.margin());
            }
            if (vol5m != null)
            {
                item.put("avg_buy_5m",    vol5m.avgHigh);
                item.put("avg_sell_5m",   vol5m.avgLow);
                item.put("volume_5m",     vol5m.totalVol());
            }
            if (vol1h != null)
            {
                item.put("avg_buy_1h",    vol1h.avgHigh);
                item.put("avg_sell_1h",   vol1h.avgLow);
                item.put("volume_1h",     vol1h.totalVol());

                // Trend direction based on buy price: 5m avg vs 1h avg
                if (vol5m != null && vol5m.avgHigh > 0 && vol1h.avgHigh > 0)
                {
                    double changePct = (vol5m.avgHigh - vol1h.avgHigh) * 100.0 / vol1h.avgHigh;
                    String direction = changePct > 1.0 ? "rising" : changePct < -1.0 ? "falling" : "stable";
                    item.put("trend",            direction);
                    item.put("trend_change_pct", Math.round(changePct * 10) / 10.0);
                }
            }
            if (meta != null) item.put("ge_limit", meta.limit);
            items.add(item);
        }

        result.put("items",              items);
        result.put("prices_age_seconds", wikiPriceService.getAge5mMs() / 1000);
        return result;
    }

        // ── BANK CLASSIFICATION (Phase 10) ───────────────────────────────────────

    public Map<String, Object> buildBankClassified()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        if (cachedBankItems == null)
        {
            result.put("cached", false);
            result.put("message", "Bank not yet opened this session.");
            return result;
        }

        List<Map<String, Object>> equipment   = new ArrayList<>();
        List<Map<String, Object>> food        = new ArrayList<>();
        List<Map<String, Object>> potions     = new ArrayList<>();
        List<Map<String, Object>> runes       = new ArrayList<>();
        List<Map<String, Object>> ammo        = new ArrayList<>();
        List<Map<String, Object>> materials   = new ArrayList<>();
        List<Map<String, Object>> other       = new ArrayList<>();

        for (Item item : cachedBankItems)
        {
            if (item.getId() <= 0 || item.getQuantity() <= 0) continue;
            net.runelite.api.ItemComposition comp = itemManager.getItemComposition(item.getId());
            String name = comp.getName();
            if (name == null || name.equals("null")) continue;

            String nameLower = name.toLowerCase();
            String[] actions = comp.getInventoryActions();
            boolean hasWear   = hasAction(actions, "Wear");
            boolean hasWield  = hasAction(actions, "Wield");
            boolean hasEat    = hasAction(actions, "Eat");
            boolean hasDrink  = hasAction(actions, "Drink");

            Map<String, Object> entry = buildBankEntry(item, comp, name, nameLower);

            if (isRune(nameLower))                           runes.add(entry);
            else if (isAmmo(nameLower))                      ammo.add(entry);
            else if (hasEat)                                 food.add(entry);
            else if (hasDrink || isPotion(nameLower))        potions.add(entry);
            else if (hasWear || hasWield)                    { entry.put("slot", inferSlot(nameLower, hasWield)); equipment.add(entry); }
            else if (comp.isStackable() && !comp.isTradeable()) other.add(entry);
            else if (comp.isStackable())                     materials.add(entry);
            else                                             other.add(entry);
        }

        // Sort each category by quantity desc
        Comparator<Map<String,Object>> byQty = (a, b) ->
            Integer.compare((int) b.get("quantity"), (int) a.get("quantity"));

        equipment.sort(Comparator.comparing(e -> (String) e.get("name")));
        food.sort(byQty);
        potions.sort(byQty);
        runes.sort(byQty);
        ammo.sort(byQty);
        materials.sort(byQty);
        other.sort(Comparator.comparing(e -> (String) e.get("name")));

        result.put("cached", true);
        result.put("equipment",  equipment);
        result.put("food",       food);
        result.put("potions",    potions);
        result.put("runes",      runes);
        result.put("ammo",       ammo);
        result.put("materials",  materials);
        result.put("other",      other);
        return result;
    }

    private Map<String, Object> buildBankEntry(Item item, net.runelite.api.ItemComposition comp, String name, String nameLower)
    {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name",     name);
        entry.put("id",       item.getId());
        entry.put("quantity", item.getQuantity());
        // Attach Wiki examine text if available
        WikiPriceService.ItemMeta meta = wikiPriceService.getMeta(item.getId());
        if (meta != null && meta.examine != null && !meta.examine.isEmpty())
            entry.put("examine", meta.examine);
        return entry;
    }

    private boolean hasAction(String[] actions, String target)
    {
        if (actions == null) return false;
        for (String a : actions)
            if (target.equals(a)) return true;
        return false;
    }

    private boolean isRune(String name)
    {
        return name.endsWith("rune") || name.equals("death rune") || name.equals("blood rune")
            || name.equals("soul rune") || name.equals("wrath rune") || name.equals("rune");
    }

    private boolean isAmmo(String name)
    {
        return name.contains("arrow") || name.contains("bolt") || name.contains("dart")
            || name.contains("knife") || name.contains("thrownaxe") || name.contains("javelin")
            || name.contains("cannonball") || name.contains("chinchompa");
    }

    private boolean isPotion(String name)
    {
        return name.contains("potion") || name.contains("brew") || name.contains("restore")
            || name.contains("flask") || name.contains("mix") || name.contains("antipoison")
            || name.contains("antidote") || name.contains("antifire") || name.contains("prayer pot")
            || name.contains("stamina") || name.contains("divine") || name.contains("ancient brew");
    }

    private String inferSlot(String name, boolean wield)
    {
        if (wield) return name.contains("shield") || name.contains("defender") || name.contains("book")
                              || name.contains("buckler") || name.contains("ward") ? "shield" : "weapon";
        if (name.contains("helm") || name.contains("hat") || name.contains("hood") || name.contains("coif")
            || name.contains("mask") || name.contains("tiara") || name.contains("crown") || name.contains("berserker helm")) return "head";
        if (name.contains("cape") || name.contains("cloak") || name.contains("backpack")) return "cape";
        if (name.contains("amulet") || name.contains("necklace") || name.contains("pendant")
            || name.contains("salve") || name.contains("torture") || name.contains("fury")) return "amulet";
        if (name.contains("platebody") || name.contains("chainbody") || name.contains("hauberk")
            || name.contains(" top") || name.contains("chestplate") || name.contains(" body")
            || name.contains("tabard") || name.contains("tunic") || name.contains("robetop")) return "body";
        if (name.contains("platelegs") || name.contains("skirt") || name.contains("chaps")
            || name.contains("tassets") || name.contains("robebottom") || name.contains("trousers")) return "legs";
        if (name.contains("gloves") || name.contains("gauntlets") || name.contains("vambraces")) return "gloves";
        if (name.contains("boots") || name.contains("shoes") || name.contains("sandals")) return "boots";
        if (name.contains("ring")) return "ring";
        return "equipment";
    }

        // ── BANK TOOLS (Phase 9) ──────────────────────────────────────────────────

    public Map<String, Object> buildBankSummary()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        if (cachedBankItems == null)
        {
            result.put("cached", false);
            result.put("message", "Bank not yet opened this session.");
            result.put("coins",   getCoins());
            return result;
        }
        long totalValue  = 0;
        int  uniqueItems = 0;
        for (Item item : cachedBankItems)
        {
            if (item.getId() <= 0 || item.getQuantity() <= 0) continue;
            totalValue += (long) itemManager.getItemPrice(item.getId()) * item.getQuantity();
            uniqueItems++;
        }
        result.put("cached",      true);
        result.put("total_value", totalValue);
        result.put("unique_items",uniqueItems);
        result.put("coins",       getCoins());
        return result;
    }

    public Map<String, Object> buildBankTopValue()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        if (cachedBankItems == null)
        {
            result.put("cached", false);
            result.put("message", "Bank not yet opened this session.");
            return result;
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Item item : cachedBankItems)
        {
            if (item.getId() <= 0 || item.getQuantity() <= 0) continue;
            int  price      = itemManager.getItemPrice(item.getId());
            long stackValue = (long) price * item.getQuantity();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name",        itemManager.getItemComposition(item.getId()).getName());
            entry.put("id",          item.getId());
            entry.put("quantity",    item.getQuantity());
            entry.put("price_each",  price);
            entry.put("total_value", stackValue);
            items.add(entry);
        }
        items.sort((a, b) -> Long.compare((long) b.get("total_value"), (long) a.get("total_value")));
        List<Map<String, Object>> top100 = items.subList(0, Math.min(100, items.size()));
        result.put("cached",      true);
        result.put("item_count",  items.size());
        result.put("showing",     top100.size());
        result.put("items",       top100);
        return result;
    }

    public Map<String, Object> buildBankCoins()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("coins_inventory", getInventoryCoins());
        result.put("coins_bank",      getBankCoins());
        result.put("coins_total",     getCoins());
        return result;
    }

    private long getCoins()        { return getInventoryCoins() + getBankCoins(); }
    private long getInventoryCoins()
    {
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        if (inv == null) return 0;
        for (Item item : inv.getItems())
            if (item.getId() == 995) return item.getQuantity();
        return 0;
    }
    private long getBankCoins()
    {
        if (cachedBankItems == null) return 0;
        for (Item item : cachedBankItems)
            if (item.getId() == 995) return item.getQuantity();
        return 0;
    }

    // ── PRICES & FLIPPING (Phase 9) ───────────────────────────────────────────

    public Map<String, Object> buildItemPrices(List<Integer> itemIds)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> prices = new ArrayList<>();
        for (int id : itemIds)
        {
            WikiPriceService.PriceData pd   = wikiPriceService.getPrice(id);
            WikiPriceService.ItemMeta  meta = wikiPriceService.getMeta(id);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id",   id);
            entry.put("name", meta != null ? meta.name : itemManager.getItemComposition(id).getName());
            if (pd != null)
            {
                entry.put("buy_price",   pd.high);
                entry.put("sell_price",  pd.low);
                entry.put("margin",      pd.margin());
                entry.put("margin_pct",  Math.round(pd.marginPct() * 10) / 10.0);
                if (meta != null) entry.put("ge_limit", meta.limit);
            }
            else entry.put("error", "Price not available");
            prices.add(entry);
        }
        result.put("prices_age_seconds",
            (System.currentTimeMillis() - wikiPriceService.lastPriceFetch) / 1000);
        result.put("items", prices);
        return result;
    }

    public Map<String, Object> buildFlipSuggestions()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        if (cachedBankItems == null)
        {
            result.put("error", "Bank not yet opened this session. Open your bank first.");
            return result;
        }

        long coins = getCoins();
        result.put("coins_available", coins);

        List<Map<String, Object>> suggestions = new ArrayList<>();
        for (Item item : cachedBankItems)
        {
            if (item.getId() <= 0 || item.getQuantity() <= 0) continue;
            WikiPriceService.PriceData pd   = wikiPriceService.getPrice(item.getId());
            WikiPriceService.ItemMeta  meta = wikiPriceService.getMeta(item.getId());
            WikiPriceService.VolumeData vol = wikiPriceService.getVolume5m(item.getId());
            if (pd == null || pd.high <= 0 || pd.low <= 0) continue;
            int margin = pd.margin();
            if (margin <= 0) continue;

            boolean canAfford = coins >= pd.high;
            int totalVol5m = vol != null ? vol.totalVol() : 0;

            Map<String, Object> s = new LinkedHashMap<>();
            s.put("name",           meta != null ? meta.name : itemManager.getItemComposition(item.getId()).getName());
            s.put("id",             item.getId());
            s.put("buy_at",         pd.high);
            s.put("sell_at",        pd.low);
            s.put("margin",         margin);
            s.put("margin_pct",     Math.round(pd.marginPct() * 10) / 10.0);
            s.put("volume_5m",      totalVol5m);
            s.put("volume_hourly_est", totalVol5m * 12); // extrapolate 5m → 1h
            s.put("can_afford",     canAfford);
            if (meta != null)
            {
                s.put("ge_limit",       meta.limit);
                s.put("max_profit",     (long) margin * meta.limit);
                s.put("cost_full_flip", (long) pd.high * meta.limit);
                s.put("highalch",       meta.highalch);
            }
            suggestions.add(s);
        }

        // Sort: score = margin_pct * log(volume+1), deprioritise unaffordable
        suggestions.sort((a, b) -> {
            boolean aAfford = (boolean) a.get("can_afford");
            boolean bAfford = (boolean) b.get("can_afford");
            if (aAfford != bAfford) return bAfford ? 1 : -1;
            double aScore = (double) a.get("margin_pct") * Math.log1p((int) a.get("volume_5m"));
            double bScore = (double) b.get("margin_pct") * Math.log1p((int) b.get("volume_5m"));
            return Double.compare(bScore, aScore);
        });

        result.put("prices_age_seconds",
            (System.currentTimeMillis() - wikiPriceService.lastPriceFetch) / 1000);
        result.put("suggestion_count", suggestions.size());
        result.put("suggestions",      suggestions.subList(0, Math.min(20, suggestions.size())));
        return result;
    }

    public Map<String, Object> buildMoneyMakingContext()
    {
        if (!isLoggedIn()) return errorMap("Player is not logged in");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("coins",         getCoins());
        result.put("location",      buildLocation());
        result.put("combat_level",  client.getLocalPlayer().getCombatLevel());
        // Slayer task context
        Map<String, Object> slayer = buildSlayerTask();
        if (slayer.get("task") != null) result.put("slayer_task", slayer);
        // Key stats for common money making (Slayer, RC, Farming, Herblore, etc.)
        Map<String, Object> stats = new LinkedHashMap<>();
        for (net.runelite.api.Skill skill : net.runelite.api.Skill.values())
        {
            if (skill == net.runelite.api.Skill.OVERALL) continue;
            stats.put(skill.getName().toLowerCase(), client.getRealSkillLevel(skill));
        }
        result.put("stats",         stats);
        result.put("members_world", client.getWorldType().contains(net.runelite.api.WorldType.MEMBERS));
        return result;
    }
}
