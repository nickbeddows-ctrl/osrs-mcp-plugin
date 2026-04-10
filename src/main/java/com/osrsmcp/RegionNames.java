package com.osrsmcp;

import java.util.HashMap;
import java.util.Map;

public final class RegionNames
{
    private RegionNames() {}
    private static final Map<Integer, String> NAMES = new HashMap<>();
    static
    {
        NAMES.put(12850, "Lumbridge");
        NAMES.put(12849, "Lumbridge Swamp");
        NAMES.put(13106, "Draynor Village");
        NAMES.put(13362, "Varrock");
        NAMES.put(13363, "Varrock");
        NAMES.put(13361, "Varrock West");
        NAMES.put(12854, "Al Kharid");
        NAMES.put(11828, "Falador");
        NAMES.put(11829, "Falador East");
        NAMES.put(11572, "Port Sarim");
        NAMES.put(11571, "Rimmington");
        NAMES.put(12084, "Taverley");
        NAMES.put(11340, "Burthorpe");
        NAMES.put(11341, "Burthorpe");
        NAMES.put(11339, "Death Plateau");
        NAMES.put(11085, "Troll Stronghold");
        NAMES.put(11086, "Troll Stronghold");
        NAMES.put(11068, "Trollheim");
        NAMES.put(10549, "Ardougne North");
        NAMES.put(10547, "Ardougne South");
        NAMES.put(10291, "Yanille");
        NAMES.put(10033, "Seers Village");
        NAMES.put(10034, "Camelot");
        NAMES.put(10553, "Rellekka");
        NAMES.put(10809, "Fremennik Slayer Dungeon");
        NAMES.put(13621, "Canifis");
        NAMES.put(13877, "Barrows");
        NAMES.put(13365, "Paterdomus / River Salve");
        NAMES.put(13623, "Slayer Tower");
        NAMES.put(12589, "Shantay Pass");
        NAMES.put(13357, "Pollnivneach");
        NAMES.put(13613, "Nardah");
        NAMES.put(13101, "Sophanem");
        NAMES.put(11310, "Karamja / Brimhaven");
        NAMES.put(11054, "Musa Point");
        NAMES.put(11053, "Shilo Village");
        NAMES.put(9775,  "Feldip Hills");
        NAMES.put(6972,  "Shayzien");
        NAMES.put(6971,  "Hosidius");
        NAMES.put(6970,  "Lovakengj");
        NAMES.put(6968,  "Arceuus");
        NAMES.put(6710,  "Piscarilius");
        NAMES.put(9804,  "God Wars Dungeon");
        NAMES.put(9805,  "God Wars Dungeon");
        NAMES.put(11929, "Catacombs of Kourend");
        NAMES.put(8978,  "Zulrah's Shrine");
        NAMES.put(7509,  "Pest Control");
        NAMES.put(10296, "Barbarian Assault");
        NAMES.put(9363,  "TzHaar / Fight Cave");
        NAMES.put(13214, "Edgeville / Wilderness border");
        NAMES.put(12703, "Chaos Temple (Wilderness)");
        NAMES.put(11668, "Taverley Dungeon");
    }
    public static String getName(int regionId) { return NAMES.get(regionId); }
}
