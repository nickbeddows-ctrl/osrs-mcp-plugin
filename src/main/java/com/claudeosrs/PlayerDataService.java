package com.claudeosrs;

import net.runelite.api.*;
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
    @Inject private ClaudeOsrsConfig config;

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
}
