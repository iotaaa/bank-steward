package com.banksteward;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemEquipmentStats;

/**
 * Built-in item rules. Pure functions of an item's name and properties, so they can be unit tested.
 */
final class ItemRules
{
	private static final Pattern SUFFIX = Pattern.compile("\\s*\\((\\d+|p\\+*|unf|u|i|t|g|or|e|l|nz|cr|uncharged|empty|full)\\)$");
	private static final Pattern NUMBER_SUFFIX = Pattern.compile("\\((\\d+)\\)$");

	private static final Pattern THROWN = Pattern.compile("(dart|knife|javelin|thrownaxe|chinchompa)s?(\\(p\\+*\\))?$");
	private static final Pattern RUNE = Pattern.compile("^[a-z]+ rune$");
	private static final Pattern SKILL_CAPE = Pattern.compile("^(attack|strength|defence|ranging|prayer|magic|runecraft|construct\\.|hitpoints|agility|herblore|thieving|crafting|fletching|slayer|hunter|mining|smithing|fishing|cooking|firemaking|woodcutting|farming|sailing|achievement diary|quest point|music|max) cape(\\(t\\))?$");
	private static final Pattern AXE = Pattern.compile("(^|\\s)(pick)?axe(\\s|$|\\()");
	private static final Pattern CLUE = Pattern.compile("^(clue scroll|challenge scroll|reward casket|casket|clue bottle|clue nest|clue geode|scroll box|puzzle box|light box|clue scroll \\()");

	private static final List<String> MONEY_EXACT = Arrays.asList("coins", "platinum token", "looting bag", "bank key");

	private static final List<String> TELEPORT_JEWELLERY = Arrays.asList(
		"games necklace", "ring of dueling", "amulet of glory", "skills necklace", "combat bracelet",
		"ring of wealth", "digsite pendant", "necklace of passage", "burning amulet", "slayer ring",
		"amulet of eternal glory", "ring of returning", "ring of the elements", "ring of shadows",
		"camulet", "pendant of ates");

	private static final List<String> TELEPORT_ITEMS = Arrays.asList(
		"xeric's talisman", "drakan's medallion", "royal seed pod", "ectophial", "enchanted lyre",
		"pharaoh's sceptre", "chronicle", "teleport crystal", "ardougne cloak", "dramen staff", "lunar staff",
		"karamja gloves",
		"desert amulet", "morytania legs", "fremennik sea boots", "kandarin headgear", "western banner", "wilderness sword",
		"rada's blessing", "explorer's ring", "ghommal's hilt", "kharedst's memoirs", "book of the dead",
		"master scroll book", "quetzal whistle", "construct. cape",
		"crystal of memories", "eternal teleport crystal", "mythical cape");

	private static final List<String> SKILLING_TOOLS = Arrays.asList(
		"harpoon", "fishing rod", "fly fishing rod", "small fishing net", "big fishing net", "lobster pot",
		"chisel", "hammer", "knife", "tinderbox", "secateurs", "magic secateurs", "rake", "seed dibber",
		"trowel", "watering can", "gricoller's can", "saw", "amy's saw", "crystal saw", "needle", "thread",
		"glassblowing pipe", "pestle and mortar", "gem bag", "coal bag", "herb sack", "seed box", "log basket",
		"fish barrel", "bonecrusher", "ash sanctifier", "rope", "plant cure", "bottomless compost bucket",
		"noose wand", "bird snare", "box trap", "butterfly net", "magic butterfly net", "rabbit snare",
		"teasing stick", "imcando hammer", "dragon harpoon", "infernal harpoon", "crystal harpoon",
		"barbarian rod", "pearl fishing rod", "pearl fly fishing rod", "pearl barbarian rod",
		"tackle box", "essence pouch", "colossal pouch", "small pouch", "medium pouch",
		"large pouch", "giant pouch", "forestry kit", "reagent pouch", "huntsman's kit", "ammo mould",
		"bolt mould", "amulet mould", "ring mould", "necklace mould", "bracelet mould", "tiara mould");

	private static final List<String> OUTFITS = Arrays.asList(
		"angler", "lumberjack", "prospector", "graceful", "rogue ", "pyromancer", "farmer's", "carpenter's",
		"smiths ", "zealot's", "spirit angler", "raiments of the eye", "forestry", "larupia", "graahk", "kyatt",
		"camouflage", "fishbowl", "diving apparatus", "golden apron", "brown apron", "white apron",
		"hat of the eye", "robe top of the eye", "robe bottoms of the eye",
		"boots of the eye", "guild hunter", "agility cape", "boots of lightness", "penance gloves",
		"spottier cape", "spotted cape", "ice gloves", "goldsmith gauntlets", "cooking gauntlets",
		"chaos gauntlets", "mining gloves", "gauntlets of goldsmithing", "celestial ring", "varrock armour");

	private static final List<String> HERBS = Arrays.asList(
		"guam leaf", "marrentill", "tarromin", "harralander", "ranarr weed", "toadflax", "irit leaf",
		"avantoe", "kwuarm", "huasca", "snapdragon", "cadantine", "lantadyme", "dwarf weed", "torstol");

	private static final List<String> SECONDARIES = Arrays.asList(
		"eye of newt", "limpwurt root", "red spiders' eggs", "white berries", "snape grass", "wine of zamorak",
		"jangerberries", "crushed nest", "potato cactus", "mort myre fungus", "dragon scale dust",
		"unicorn horn dust", "unicorn horn", "chocolate dust", "chocolate bar", "zulrah's scales",
		"amylase crystal", "crystal dust", "nail beast nails", "goat horn dust", "desert goat horn",
		"lava scale shard", "lava scale", "blue dragon scale", "ground mud runes", "ashes", "volcanic ash",
		"poison ivy berries", "cactus spine", "toad's legs", "magic roots", "yew roots", "cave nightshade",
		"bird nest", "frog spawn", "kebbit teeth dust", "gorak claw powder", "ancient essence", "araxyte venom sack",
		"vial of water", "vial", "vial of blood", "coconut milk", "swamp tar");

	private static final String[] GEM_ORDER = {
		"opal", "jade", "red topaz", "sapphire", "emerald", "ruby", "diamond", "dragonstone", "onyx", "zenyte"};
	private static final List<String> GEMS = Arrays.asList(GEM_ORDER);

	// Resource types, in bank order. Each type is ordered by its own tier list.
	static final int GROUP_ALLOTMENT_SEEDS = 0;
	static final int GROUP_HERB_SEEDS = 1;
	static final int GROUP_TREE_SEEDS = 2;
	static final int GROUP_FRUIT_SEEDS = 3;
	static final int GROUP_OTHER_SEEDS = 4;
	static final int GROUP_FARMING = 5;
	static final int GROUP_GRIMY_HERBS = 6;
	static final int GROUP_CLEAN_HERBS = 7;
	static final int GROUP_UNF_POTIONS = 8;
	static final int GROUP_SECONDARIES = 9;
	static final int GROUP_ORES = 10;
	static final int GROUP_BARS = 11;
	static final int GROUP_LOGS = 12;
	static final int GROUP_PLANKS = 13;
	static final int GROUP_FLETCHING_BASICS = 14;
	static final int GROUP_ARROWTIPS = 15;
	static final int GROUP_DART_TIPS = 16;
	static final int GROUP_JAVELIN_HEADS = 17;
	static final int GROUP_UNF_BOLTS = 18;
	static final int GROUP_BOLT_TIPS = 19;
	static final int GROUP_UNSTRUNG_BOWS = 20;
	static final int GROUP_LIMBS = 21;
	static final int GROUP_STOCKS = 22;
	static final int GROUP_UNCUT_GEMS = 23;
	static final int GROUP_CUT_GEMS = 24;
	static final int GROUP_DRAGONHIDE = 25;
	static final int GROUP_DRAGON_LEATHER = 26;
	static final int GROUP_HIDES = 27;
	static final int GROUP_CRAFTING = 28;
	static final int GROUP_BONES = 29;
	static final int GROUP_ASHES = 30;
	static final int GROUP_ENSOULED = 31;
	static final int GROUP_RAW_FOOD = 32;
	static final int GROUP_ESSENCE = 33;
	static final int GROUP_TALISMANS = 34;
	static final int GROUP_TIARAS = 35;
	static final int GROUP_CONTAINERS = 36;

	// Leading words that make a group of their own: all burnt food together, all broken items together
	private static final List<String> PREFIX_FAMILIES = Arrays.asList("burnt", "broken", "damaged", "rotten", "ruined");

	// Containers that come empty and filled ("Bucket", "Bucket of milk"). Vials stay with Herblore.
	private static final List<String> CONTAINERS = Arrays.asList(
		"bucket", "jug", "bowl", "pot", "cup", "basket", "sack", "jar", "barrel", "pie dish", "waterskin");

	// Runes & ammo types
	static final int AMMO_POUCH = 0;
	static final int AMMO_RUNES = 1;
	static final int AMMO_ARROWS = 2;
	static final int AMMO_BOLTS = 3;
	static final int AMMO_JAVELINS = 4;
	static final int AMMO_DARTS = 5;
	static final int AMMO_KNIVES = 6;
	static final int AMMO_THROWNAXES = 7;
	static final int AMMO_CHINCHOMPAS = 8;
	static final int AMMO_OTHER = 9;

	// Skilling tool types, then outfits
	static final int TOOL_AXES = 0;
	static final int TOOL_PICKAXES = 1;
	static final int TOOL_HARPOONS = 2;
	static final int TOOL_FISHING = 3;
	static final int TOOL_FARMING = 4;
	static final int TOOL_CRAFTING = 5;
	static final int TOOL_HUNTER = 6;
	static final int TOOL_STORAGE = 7;
	static final int TOOL_OTHER = 8;
	static final int OUTFITS_GROUP = 9;
	static final int SKILL_ITEMS_GROUP = 60;

	// Skills in bank order: each gathering skill next to the skill that uses what it gathers
	static final int SKILL_MINING = 0;
	static final int SKILL_SMITHING = 1;
	static final int SKILL_WOODCUTTING = 2;
	static final int SKILL_FLETCHING = 3;
	static final int SKILL_FIREMAKING = 4;
	static final int SKILL_CONSTRUCTION = 5;
	static final int SKILL_FISHING = 6;
	static final int SKILL_COOKING = 7;
	static final int SKILL_FARMING = 8;
	static final int SKILL_HERBLORE = 9;
	static final int SKILL_CRAFTING = 10;
	static final int SKILL_HUNTER = 11;
	static final int SKILL_PRAYER = 12;
	static final int SKILL_RUNECRAFT = 13;
	static final int SKILL_AGILITY = 14;
	static final int SKILL_THIEVING = 15;
	static final int SKILL_MAGIC = 16;
	static final int SKILL_OTHER = 17;

	private static final Map<String, Integer> TOOL_SKILL = ImmutableMap.<String, Integer>builder()
		.put("hammer", SKILL_SMITHING).put("imcando hammer", SKILL_SMITHING).put("ammo mould", SKILL_SMITHING)
		.put("bolt mould", SKILL_SMITHING).put("chisel", SKILL_CRAFTING).put("needle", SKILL_CRAFTING)
		.put("thread", SKILL_CRAFTING).put("glassblowing pipe", SKILL_CRAFTING).put("ring mould", SKILL_CRAFTING)
		.put("necklace mould", SKILL_CRAFTING).put("amulet mould", SKILL_CRAFTING).put("bracelet mould", SKILL_CRAFTING)
		.put("tiara mould", SKILL_RUNECRAFT).put("knife", SKILL_FLETCHING).put("saw", SKILL_CONSTRUCTION)
		.put("amy's saw", SKILL_CONSTRUCTION).put("crystal saw", SKILL_CONSTRUCTION).put("tinderbox", SKILL_FIREMAKING)
		.put("pestle and mortar", SKILL_HERBLORE).put("small pouch", SKILL_RUNECRAFT).put("medium pouch", SKILL_RUNECRAFT)
		.put("large pouch", SKILL_RUNECRAFT).put("giant pouch", SKILL_RUNECRAFT).put("colossal pouch", SKILL_RUNECRAFT)
		.put("essence pouch", SKILL_RUNECRAFT).put("gem bag", SKILL_MINING).put("coal bag", SKILL_MINING)
		.put("herb sack", SKILL_HERBLORE).put("seed box", SKILL_FARMING).put("log basket", SKILL_WOODCUTTING)
		.put("forestry kit", SKILL_WOODCUTTING).put("fish barrel", SKILL_FISHING).put("reagent pouch", SKILL_HERBLORE)
		.put("bonecrusher", SKILL_PRAYER).put("ash sanctifier", SKILL_PRAYER)
		.build();

	// Skill rewards and currencies that aren't tools or materials, filed under their skill in Skilling
	private static final Map<String, Integer> SKILL_ITEMS = ImmutableMap.<String, Integer>builder()
		.put("mark of grace", SKILL_AGILITY).put("hallowed mark", SKILL_AGILITY).put("agility arena ticket", SKILL_AGILITY)
		.put("brimhaven voucher", SKILL_AGILITY).put("stardust", SKILL_MINING).put("unidentified minerals", SKILL_MINING)
		.put("pay-dirt", SKILL_MINING).put("numulite", SKILL_MINING).put("minnow", SKILL_FISHING)
		.put("molch pearl", SKILL_FISHING).put("spirit flakes", SKILL_FISHING).put("fish chunks", SKILL_FISHING)
		.put("fishing bait", SKILL_FISHING).put("sandworms", SKILL_FISHING).put("abyssal pearls", SKILL_RUNECRAFT)
		.put("guardian fragments", SKILL_RUNECRAFT).put("elemental guardian stone", SKILL_RUNECRAFT)
		.put("catalytic guardian stone", SKILL_RUNECRAFT).put("polyelemental guardian stone", SKILL_RUNECRAFT)
		.put("coin pouch", SKILL_THIEVING).put("lockpick", SKILL_THIEVING).put("burnt page", SKILL_FIREMAKING)
		.put("bruma root", SKILL_FIREMAKING).put("bruma kindling", SKILL_FIREMAKING).put("supply crate", SKILL_FIREMAKING)
		.put("bronze nails", SKILL_CONSTRUCTION).put("iron nails", SKILL_CONSTRUCTION).put("steel nails", SKILL_CONSTRUCTION)
		.put("black nails", SKILL_CONSTRUCTION).put("mithril nails", SKILL_CONSTRUCTION)
		.put("adamantite nails", SKILL_CONSTRUCTION).put("rune nails", SKILL_CONSTRUCTION)
		.put("bolt of cloth", SKILL_CONSTRUCTION).put("kebbit fur", SKILL_HUNTER).put("hunters' loot sack", SKILL_HUNTER)
		.put("blessed bone shards", SKILL_PRAYER).put("anima-infused bark", SKILL_WOODCUTTING)
		.put("golden tench", SKILL_FISHING).put("tithe farm points", SKILL_FARMING).put("soul fragment", SKILL_RUNECRAFT)
		.build();

	private static final Map<String, Integer> OUTFIT_SKILL = ImmutableMap.<String, Integer>builder()
		.put("angler", SKILL_FISHING).put("spirit angler", SKILL_FISHING).put("fishbowl", SKILL_FISHING)
		.put("diving apparatus", SKILL_FISHING).put("lumberjack", SKILL_WOODCUTTING).put("forestry", SKILL_WOODCUTTING)
		.put("prospector", SKILL_MINING).put("mining gloves", SKILL_MINING).put("celestial ring", SKILL_MINING)
		.put("varrock armour", SKILL_MINING).put("graceful", SKILL_AGILITY).put("agility cape", SKILL_AGILITY)
		.put("boots of lightness", SKILL_AGILITY).put("spottier cape", SKILL_AGILITY).put("spotted cape", SKILL_AGILITY)
		.put("penance gloves", SKILL_OTHER).put("rogue ", SKILL_THIEVING).put("pyromancer", SKILL_FIREMAKING)
		.put("farmer's", SKILL_FARMING).put("carpenter's", SKILL_CONSTRUCTION).put("smiths ", SKILL_SMITHING)
		.put("goldsmith gauntlets", SKILL_SMITHING).put("gauntlets of goldsmithing", SKILL_SMITHING)
		.put("ice gloves", SKILL_SMITHING).put("zealot's", SKILL_PRAYER).put("raiments of the eye", SKILL_RUNECRAFT)
		.put("hat of the eye", SKILL_RUNECRAFT).put("robe top of the eye", SKILL_RUNECRAFT)
		.put("robe bottoms of the eye", SKILL_RUNECRAFT).put("boots of the eye", SKILL_RUNECRAFT)
		.put("larupia", SKILL_HUNTER).put("graahk", SKILL_HUNTER).put("kyatt", SKILL_HUNTER)
		.put("camouflage", SKILL_HUNTER).put("guild hunter", SKILL_HUNTER).put("cooking gauntlets", SKILL_COOKING)
		.put("white apron", SKILL_COOKING).put("golden apron", SKILL_CRAFTING).put("brown apron", SKILL_CRAFTING)
		.put("chaos gauntlets", SKILL_MAGIC)
		.build();

	private static final String[] METALS = {
		"bronze", "iron", "blurite", "steel", "black", "white", "mithril", "adamant", "rune", "amethyst", "gilded",
		"dragon", "3rd age", "infernal", "crystal"};

	private static final String[] WOODS = {"oak", "willow", "teak", "maple", "mahogany", "yew", "magic", "redwood"};

	private static final String[] ALLOTMENT_SEEDS = {
		"potato seed", "onion seed", "cabbage seed", "tomato seed", "sweetcorn seed", "strawberry seed",
		"watermelon seed", "snape grass seed", "barley seed", "hammerstone seed", "asgarnian seed", "jute seed",
		"yanillian seed", "krandorian seed", "wildblood seed", "marigold seed", "rosemary seed", "nasturtium seed",
		"woad seed", "limpwurt seed", "white lily seed", "redberry seed", "cadavaberry seed", "dwellberry seed",
		"jangerberry seed", "whiteberry seed", "poison ivy seed"};

	private static final String[] HERB_SEEDS = {
		"guam seed", "marrentill seed", "tarromin seed", "harralander seed", "ranarr seed", "toadflax seed",
		"irit seed", "avantoe seed", "kwuarm seed", "huasca seed", "snapdragon seed", "cadantine seed",
		"lantadyme seed", "dwarf weed seed", "torstol seed"};

	private static final String[] TREE_SEEDS = {
		"acorn", "willow seed", "teak seed", "maple seed", "mahogany seed", "yew seed", "magic seed", "redwood tree seed",
		"oak sapling", "willow sapling", "teak sapling", "maple sapling", "mahogany sapling", "yew sapling",
		"magic sapling", "redwood sapling"};

	private static final String[] FRUIT_SEEDS = {
		"apple tree seed", "banana tree seed", "orange tree seed", "curry tree seed", "pineapple seed",
		"papaya tree seed", "palm tree seed", "dragonfruit tree seed", "apple sapling", "banana sapling",
		"orange sapling", "curry sapling", "pineapple sapling", "papaya sapling", "palm sapling", "dragonfruit sapling"};

	private static final String[] FLETCHING_BASICS = {
		"arrow shaft", "feather", "headless arrow", "flax", "bow string", "crossbow string"};

	private static final String[] DRAGONHIDE_COLOURS = {"green", "blue", "red", "black"};

	private static final String[] FISHING_TOOLS = {
		"small fishing net", "big fishing net", "fishing rod", "fly fishing rod", "pearl fishing rod",
		"pearl fly fishing rod", "barbarian rod", "pearl barbarian rod", "lobster pot", "tackle box"};

	private static final String[] FARMING_TOOLS = {
		"rake", "seed dibber", "spade", "trowel", "secateurs", "magic secateurs", "watering can", "gricoller's can",
		"plant cure", "bottomless compost bucket"};

	private static final String[] CRAFTING_TOOLS = {
		"hammer", "imcando hammer", "chisel", "knife", "saw", "amy's saw", "crystal saw", "needle", "thread",
		"tinderbox", "glassblowing pipe", "pestle and mortar", "ring mould", "necklace mould", "amulet mould",
		"bracelet mould", "tiara mould", "bolt mould", "ammo mould", "rope"};

	private static final String[] HUNTER_TOOLS = {
		"bird snare", "rabbit snare", "box trap", "butterfly net", "magic butterfly net", "noose wand",
		"teasing stick", "huntsman's kit"};

	private static final String[] STORAGE = {
		"small pouch", "medium pouch", "large pouch", "giant pouch", "colossal pouch", "essence pouch", "gem bag",
		"coal bag", "herb sack", "seed box", "log basket", "fish barrel", "forestry kit", "reagent pouch",
		"bonecrusher", "ash sanctifier"};

	// Runecrafting level order, for talismans and tiaras
	private static final String[] ALTAR_ORDER = {
		"air", "mind", "water", "earth", "fire", "body", "cosmic", "chaos", "astral", "nature", "law", "death",
		"blood", "soul", "wrath", "elemental", "catalytic", "omni"};

	// Tier order, lowest first. Bars follow the same metals as the ores so the two blocks line up.
	private static final String[] ORE_ORDER = {
		"copper ore", "tin ore", "blurite ore", "iron ore", "silver ore", "coal", "gold ore", "mithril ore",
		"lovakite ore", "adamantite ore", "runite ore", "amethyst"};

	private static final String[] BAR_ORDER = {
		"bronze bar", "blurite bar", "iron bar", "steel bar", "silver bar", "elemental metal", "gold bar",
		"perfect gold bar", "lovakite bar", "mithril bar", "adamantite bar", "runite bar"};

	private static final String[] LOG_ORDER = {
		"logs", "achey tree logs", "oak logs", "willow logs", "teak logs", "juniper logs", "maple logs",
		"arctic pine logs", "mahogany logs", "yew logs", "blisterwood logs", "magic logs", "redwood logs"};

	private static final String[] PLANK_ORDER = {"plank", "oak plank", "teak plank", "mahogany plank"};

	private static final String[] RUNE_ORDER = {
		"air rune", "water rune", "earth rune", "fire rune", "mind rune", "body rune", "cosmic rune",
		"chaos rune", "nature rune", "law rune", "death rune", "astral rune", "blood rune", "soul rune",
		"wrath rune", "sunfire rune", "aether rune", "mist rune", "dust rune", "mud rune", "smoke rune",
		"steam rune", "lava rune"};

	private static final String[] POTION_ORDER = {
		"divine super combat potion", "super combat potion", "combat potion", "divine super attack potion",
		"super attack", "attack potion", "divine super strength potion", "super strength", "strength potion",
		"divine super defence potion", "super defence", "defence potion", "divine ranging potion",
		"ranging potion", "divine bastion potion", "bastion potion", "divine magic potion", "magic potion",
		"divine battlemage potion", "battlemage potion", "saradomin brew", "super restore", "sanfew serum",
		"prayer potion", "prayer regeneration potion", "stamina potion", "super energy", "energy potion",
		"extended super antifire", "super antifire potion", "extended antifire", "antifire potion",
		"anti-venom+", "anti-venom", "antidote++", "antidote+", "superantipoison", "antipoison",
		"menaphite remedy", "zamorak brew", "ancient brew", "forgotten brew", "guthix rest", "relicym's balm"};

	private static final Map<String, Integer> FOOD_HEAL = ImmutableMap.<String, Integer>builder()
		.put("manta ray", 22).put("dark crab", 22).put("tuna potato", 22).put("anglerfish", 22)
		.put("sea turtle", 21).put("cooked moonlight antelope", 26)
		.put("cooked sunlight antelope", 24).put("cooked dashing kebbit", 23).put("shark", 20)
		.put("mushroom potato", 20).put("karambwan", 18).put("cooked karambwan", 18).put("curry", 19)
		.put("monkfish", 16).put("swordfish", 14).put("potato with cheese", 16).put("egg potato", 16)
		.put("bass", 13).put("lobster", 12).put("tuna", 10).put("salmon", 9).put("pike", 8)
		.put("trout", 7).put("cod", 7).put("mackerel", 6).put("herring", 5).put("sardine", 4)
		.put("anchovies", 1).put("shrimps", 3).put("cake", 4).put("chocolate cake", 5)
		.put("jug of wine", 11).put("purple sweets", 3).put("cooked chompy", 10).put("pineapple pizza", 11)
		.put("anchovy pizza", 9).put("meat pizza", 8).put("plain pizza", 7).put("wild pie", 11)
		.put("summer pie", 11).put("admiral pie", 8).put("mushroom pie", 8).put("botanical pie", 7)
		.put("bowl of chilli con carne", 5).put("cooked crab meat", 2).put("cooked jubbly", 15)
		.put("cooked oomlie wrap", 14).put("cooked fish cake", 11).put("halibut", 23)
		.put("bluefin", 22).put("marlin", 24).put("swordtip squid", 15).put("giant krill", 17)
		.build();

	private ItemRules()
	{
	}

	static String baseName(String lowerName)
	{
		return SUFFIX.matcher(lowerName).replaceAll("").trim();
	}

	static int suffixNumber(String lowerName)
	{
		Matcher m = NUMBER_SUFFIX.matcher(lowerName);
		return m.find() ? Integer.parseInt(m.group(1)) : 0;
	}

	static boolean hasCombatStats(ItemEquipmentStats s)
	{
		return s != null && (s.getAstab() != 0 || s.getAslash() != 0 || s.getAcrush() != 0 || s.getAmagic() != 0
			|| s.getArange() != 0 || s.getDstab() != 0 || s.getDslash() != 0 || s.getDcrush() != 0
			|| s.getDmagic() != 0 || s.getDrange() != 0 || s.getStr() != 0 || s.getRstr() != 0
			|| s.getMdmg() != 0 || s.getPrayer() != 0);
	}

	private static boolean startsWithAny(String s, List<String> list)
	{
		for (String p : list)
		{
			if (s.startsWith(p))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Which outfit an item belongs to: the longest matching name, so "spirit angler" isn't filed under "angler".
	 */
	private static int outfitSet(String n)
	{
		int best = -1;
		for (int i = 0; i < OUTFITS.size(); i++)
		{
			if (n.contains(OUTFITS.get(i)) && (best < 0 || OUTFITS.get(i).length() > OUTFITS.get(best).length()))
			{
				best = i;
			}
		}
		return best;
	}

	private static int outfitPiece(int slot)
	{
		if (slot == EquipmentInventorySlot.HEAD.getSlotIdx())
		{
			return 0;
		}
		if (slot == EquipmentInventorySlot.CAPE.getSlotIdx())
		{
			return 1;
		}
		if (slot == EquipmentInventorySlot.AMULET.getSlotIdx())
		{
			return 2;
		}
		if (slot == EquipmentInventorySlot.BODY.getSlotIdx())
		{
			return 3;
		}
		if (slot == EquipmentInventorySlot.LEGS.getSlotIdx())
		{
			return 4;
		}
		if (slot == EquipmentInventorySlot.GLOVES.getSlotIdx())
		{
			return 5;
		}
		if (slot == EquipmentInventorySlot.BOOTS.getSlotIdx())
		{
			return 6;
		}
		return 7;
	}

	private static boolean containsAny(String s, List<String> list)
	{
		for (String p : list)
		{
			if (s.contains(p))
			{
				return true;
			}
		}
		return false;
	}

	private static int indexOf(String[] order, String s)
	{
		for (int i = 0; i < order.length; i++)
		{
			if (order[i].equals(s))
			{
				return i;
			}
		}
		return -1;
	}

	/**
	 * Works out the built-in rule, group and rank for an item.
	 *
	 * @param name        item name
	 * @param equipable   whether the item can be worn
	 * @param stats       equipment stats, may be null
	 * @param actions     inventory actions, entries may be null
	 * @param storePrice  shop value, used as a rough tier
	 */
	static ItemInfo classify(int id, String name, boolean equipable, ItemEquipmentStats stats, String[] actions, int storePrice)
	{
		String n = name.toLowerCase();
		String base = baseName(n);
		int suffix = suffixNumber(n);
		boolean eat = false, drink = false;
		if (actions != null)
		{
			for (String a : actions)
			{
				if ("Eat".equals(a))
				{
					eat = true;
				}
				else if ("Drink".equals(a))
				{
					drink = true;
				}
			}
		}
		boolean combat = equipable && hasCombatStats(stats);
		int slot = stats != null ? stats.getSlot() : -1;

		Rule rule;
		int group = 0;
		int rank = 0;
		String family = "";

		if (MONEY_EXACT.contains(base) || CLUE.matcher(n).find())
		{
			rule = Rule.MONEY;
			group = base.equals("coins") || base.equals("platinum token") ? 0 : clueTier(n);
		}
		else if (base.equals("rune pouch") || base.equals("divine rune pouch"))
		{
			rule = Rule.RUNES;
			group = AMMO_POUCH;
		}
		else if (RUNE.matcher(base).matches())
		{
			rule = Rule.RUNES;
			group = AMMO_RUNES;
			int i = indexOf(RUNE_ORDER, base);
			rank = i < 0 ? RUNE_ORDER.length : i;
		}
		else if (slot == EquipmentInventorySlot.AMMO.getSlotIdx() && !base.contains("blessing") && !base.contains("book")
			|| equipable && THROWN.matcher(n).find())
		{
			rule = Rule.RUNES;
			group = ammoType(base);
			rank = ammoRank(group, base, storePrice);
		}
		else if (SKILL_CAPE.matcher(n).matches())
		{
			rule = Rule.TELEPORTS;
			group = 0;
		}
		else if (startsWithAny(base, TELEPORT_JEWELLERY))
		{
			// each kind of jewellery together, most charges first
			rule = Rule.TELEPORTS;
			group = 1;
			rank = startIndex(base, TELEPORT_JEWELLERY) * 1000 - suffix;
		}
		else if (!equipable && (n.contains("teleport") || n.contains("tablet")))
		{
			rule = Rule.TELEPORTS;
			group = 2;
		}
		else if (startsWithAny(base, TELEPORT_ITEMS) || hasTeleportAction(actions, equipable))
		{
			// diary rewards, capes and anything else with a teleport option; listed ones first, the rest by name
			rule = Rule.TELEPORTS;
			group = 3;
			rank = startIndex(base, TELEPORT_ITEMS);
		}
		else if (toolType(n, base) >= 0)
		{
			// by skill; within a skill: tools, then the outfit, then storage (bags, pouches)
			rule = Rule.SKILLING;
			int type = toolType(n, base);
			group = toolSkill(type, base) * 100 + (type == TOOL_STORAGE ? 50 : type);
			rank = toolRank(type, base, storePrice);
		}
		else if (!equipable && SKILL_ITEMS.containsKey(base))
		{
			// skill rewards (marks of grace, stardust...) after the skill's tools, outfit and storage
			rule = Rule.SKILLING;
			group = SKILL_ITEMS.get(base) * 100 + SKILL_ITEMS_GROUP;
		}
		else if (equipable && containsAny(n, OUTFITS))
		{
			// with its skill's tools; each set together, pieces from head to boots
			rule = Rule.SKILLING;
			int set = outfitSet(n);
			group = OUTFIT_SKILL.getOrDefault(OUTFITS.get(set), SKILL_OTHER) * 100 + OUTFITS_GROUP;
			rank = set * 10 + outfitPiece(slot);
		}
		else if (drink && suffix >= 1 && suffix <= 4 && !equipable)
		{
			// potions not in the list go after it, by name; doses of one potion stay together
			rule = Rule.POTIONS;
			int i = indexOf(POTION_ORDER, base);
			group = i < 0 ? POTION_ORDER.length : i;
		}
		else if (eat || drink)
		{
			rule = Rule.FOOD;
			Integer heal = FOOD_HEAL.get(base);
			rank = -(heal != null ? heal * 1000 : Math.min(storePrice, 999));
		}
		else if (combat)
		{
			rule = Rule.GEAR;
		}
		else if (equipable)
		{
			rule = Rule.COSMETIC;
			group = slot;
			family = headNoun(base);
			rank = tierOrPrice(prefixTier(base, METALS), METALS.length, 0);
		}
		else if (resourceGroup(n, base) >= 0)
		{
			// by skill, keeping each skill's types in their own order
			rule = Rule.RESOURCES;
			int type = resourceGroup(n, base);
			group = resourceSkill(type) * 100 + type;
			rank = resourceRank(type, base, storePrice);
			if (type == GROUP_CONTAINERS)
			{
				// each kind of container together, the empty one first
				family = headNoun(base);
				rank = CONTAINERS.contains(base) ? 0 : 1;
			}
		}
		else
		{
			// similar names together (all cannonballs, all pages), metal ones by tier
			rule = Rule.MISC;
			group = 0;
			family = headNoun(base);
			rank = tierOrPrice(prefixTier(base, METALS), METALS.length, 0);
		}

		return new ItemInfo(id, name, base, suffix, rule, group, rank, storePrice, stats, family);
	}

	/**
	 * True if one of the item's options teleports: any option with "teleport" in it, or "Rub" on wearable
	 * jewellery (lamps are rubbed too, but can't be worn).
	 */
	static boolean hasTeleportAction(String[] actions, boolean equipable)
	{
		if (actions == null)
		{
			return false;
		}
		for (String a : actions)
		{
			if (a == null)
			{
				continue;
			}
			if (a.toLowerCase().contains("teleport") || equipable && a.equals("Rub"))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean isAxe(String n)
	{
		return AXE.matcher(n).find() && !n.contains("battleaxe") && !n.contains("thrownaxe");
	}

	private static int clueTier(String n)
	{
		if (n.contains("beginner"))
		{
			return 1;
		}
		if (n.contains("easy"))
		{
			return 2;
		}
		if (n.contains("medium"))
		{
			return 3;
		}
		if (n.contains("hard"))
		{
			return 4;
		}
		if (n.contains("elite"))
		{
			return 5;
		}
		if (n.contains("master"))
		{
			return 6;
		}
		return 7;
	}

	private static int startIndex(String base, List<String> list)
	{
		for (int i = 0; i < list.size(); i++)
		{
			if (base.startsWith(list.get(i)))
			{
				return i;
			}
		}
		return list.size();
	}

	/**
	 * Index of the entry the name starts with as a whole word (the longest one), or -1.
	 * "adamant" also matches "adamantite" and "rune" matches "runite".
	 */
	static int prefixTier(String n, String[] order)
	{
		int best = -1;
		for (int i = 0; i < order.length; i++)
		{
			String p = order[i];
			boolean match = n.startsWith(p + " ")
				|| p.equals("adamant") && n.startsWith("adamantite ")
				|| p.equals("rune") && n.startsWith("runite ");
			if (match && (best < 0 || p.length() > order[best].length()))
			{
				best = i;
			}
		}
		return best;
	}

	/**
	 * Groups without a fixed tier order, where items are grouped by the words they share with other items
	 * in the bank. Groups with their own order (ores, herbs, gems, runes, gear...) keep it.
	 */
	static boolean isLoose(Rule rule, int group)
	{
		switch (rule)
		{
			case MONEY:
			case FOOD:
			case COSMETIC:
			case MISC:
				return true;
			case POTIONS:
				return group == POTION_ORDER.length;
			case TELEPORTS:
				// the listed teleport items (group 3) keep the list's order
				return group == 0 || group == 2;
			case RUNES:
				return group == AMMO_OTHER || group == AMMO_CHINCHOMPAS;
			case SKILLING:
				return group % 100 == TOOL_OTHER || group % 100 == SKILL_ITEMS_GROUP;
			case RESOURCES:
				switch (group % 100)
				{
					case GROUP_OTHER_SEEDS:
					case GROUP_FARMING:
					case GROUP_SECONDARIES:
					case GROUP_HIDES:
					case GROUP_CRAFTING:
					case GROUP_BONES:
					case GROUP_ASHES:
					case GROUP_ENSOULED:
					case GROUP_RAW_FOOD:
					case GROUP_CONTAINERS:
						return true;
					default:
						return false;
				}
			default:
				return false;
		}
	}

	private static final List<String> STOP_WORDS = Arrays.asList("of", "the", "and", "with", "for");

	/**
	 * The words of a name that can link it to other items: no brackets, numbers or little words.
	 */
	static List<String> words(String base)
	{
		List<String> out = new java.util.ArrayList<>();
		for (String w : base.replaceAll("\\(.*?\\)", " ").split("[^a-z0-9'+-]+"))
		{
			if (w.length() >= 3 && !STOP_WORDS.contains(w) && !w.matches("[0-9/+-]+"))
			{
				out.add(w);
			}
		}
		return out;
	}

	/**
	 * The main word of a name: the last word, ignoring anything in brackets and trailing numbers,
	 * without a plural "s". "Granite cannonball" and "Cannonball" both give "cannonball".
	 */
	static String headNoun(String base)
	{
		String b = base.replaceAll("\\(.*?\\)", " ").trim();
		// "bucket of sand" is a bucket, "ring of wealth" a ring
		int of = b.indexOf(" of ");
		if (of > 0)
		{
			b = b.substring(0, of);
		}
		String[] words = b.split("\\s+");
		// "burnt shark", "broken pickaxe": the state word groups them
		if (words.length > 1 && PREFIX_FAMILIES.contains(words[0]))
		{
			return words[0];
		}
		int i = words.length - 1;
		while (i > 0 && words[i].matches("\\d+|[ivx]+"))
		{
			i--;
		}
		String w = words[i];
		if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss"))
		{
			w = w.substring(0, w.length() - 1);
		}
		return w;
	}

	/**
	 * The metal an item is made of ("rune", "dragon"...), or null.
	 */
	static String metalOf(String base)
	{
		int i = prefixTier(base, METALS);
		return i < 0 ? null : METALS[i];
	}

	private static int tierOrPrice(int tier, int listLength, int storePrice)
	{
		return tier >= 0 ? tier : listLength + Math.min(storePrice, 1_000_000);
	}

	static int ammoType(String base)
	{
		if (base.endsWith("arrow") || base.endsWith("arrows"))
		{
			return AMMO_ARROWS;
		}
		if (base.endsWith("bolts") || base.endsWith("bolt"))
		{
			return AMMO_BOLTS;
		}
		if (base.endsWith("javelin"))
		{
			return AMMO_JAVELINS;
		}
		if (base.endsWith("dart"))
		{
			return AMMO_DARTS;
		}
		if (base.endsWith("knife"))
		{
			return AMMO_KNIVES;
		}
		if (base.endsWith("thrownaxe"))
		{
			return AMMO_THROWNAXES;
		}
		if (base.contains("chinchompa"))
		{
			return AMMO_CHINCHOMPAS;
		}
		return AMMO_OTHER;
	}

	private static int ammoRank(int type, String base, int storePrice)
	{
		if (type == AMMO_BOLTS)
		{
			// metal bolts first, then gem-tipped bolts by gem, each with its dragon version straight after
			int gem = prefixTier(base, GEM_ORDER);
			if (gem >= 0)
			{
				return 100 + gem * 2 + (base.contains("dragon bolts") ? 1 : 0);
			}
			return tierOrPrice(prefixTier(base, METALS), METALS.length, storePrice);
		}
		if (type == AMMO_OTHER || type == AMMO_CHINCHOMPAS)
		{
			return storePrice;
		}
		return tierOrPrice(prefixTier(base, METALS), METALS.length, storePrice);
	}

	static int toolSkill(int type, String base)
	{
		switch (type)
		{
			case TOOL_AXES:
				return SKILL_WOODCUTTING;
			case TOOL_PICKAXES:
				return SKILL_MINING;
			case TOOL_HARPOONS:
			case TOOL_FISHING:
				return SKILL_FISHING;
			case TOOL_FARMING:
				return SKILL_FARMING;
			case TOOL_HUNTER:
				return SKILL_HUNTER;
			default:
				return TOOL_SKILL.getOrDefault(base, SKILL_OTHER);
		}
	}

	static int resourceSkill(int type)
	{
		switch (type)
		{
			case GROUP_ALLOTMENT_SEEDS:
			case GROUP_HERB_SEEDS:
			case GROUP_TREE_SEEDS:
			case GROUP_FRUIT_SEEDS:
			case GROUP_OTHER_SEEDS:
			case GROUP_FARMING:
				return SKILL_FARMING;
			case GROUP_GRIMY_HERBS:
			case GROUP_CLEAN_HERBS:
			case GROUP_UNF_POTIONS:
			case GROUP_SECONDARIES:
				return SKILL_HERBLORE;
			case GROUP_ORES:
				return SKILL_MINING;
			case GROUP_BARS:
				return SKILL_SMITHING;
			case GROUP_LOGS:
				return SKILL_WOODCUTTING;
			case GROUP_PLANKS:
				return SKILL_CONSTRUCTION;
			case GROUP_FLETCHING_BASICS:
			case GROUP_ARROWTIPS:
			case GROUP_DART_TIPS:
			case GROUP_JAVELIN_HEADS:
			case GROUP_UNF_BOLTS:
			case GROUP_BOLT_TIPS:
			case GROUP_UNSTRUNG_BOWS:
			case GROUP_LIMBS:
			case GROUP_STOCKS:
				return SKILL_FLETCHING;
			case GROUP_UNCUT_GEMS:
			case GROUP_CUT_GEMS:
			case GROUP_DRAGONHIDE:
			case GROUP_DRAGON_LEATHER:
			case GROUP_HIDES:
			case GROUP_CRAFTING:
				return SKILL_CRAFTING;
			case GROUP_BONES:
			case GROUP_ASHES:
			case GROUP_ENSOULED:
				return SKILL_PRAYER;
			case GROUP_RAW_FOOD:
				return SKILL_COOKING;
			case GROUP_ESSENCE:
			case GROUP_TALISMANS:
			case GROUP_TIARAS:
				return SKILL_RUNECRAFT;
			default:
				return SKILL_OTHER;
		}
	}

	/**
	 * Skilling tool type, or -1 if the item isn't a tool.
	 */
	static int toolType(String n, String base)
	{
		if (base.endsWith("pickaxe"))
		{
			return TOOL_PICKAXES;
		}
		if (isAxe(n))
		{
			return TOOL_AXES;
		}
		if (base.endsWith("harpoon"))
		{
			return TOOL_HARPOONS;
		}
		if (indexOf(FISHING_TOOLS, base) >= 0)
		{
			return TOOL_FISHING;
		}
		if (indexOf(FARMING_TOOLS, base) >= 0)
		{
			return TOOL_FARMING;
		}
		if (indexOf(CRAFTING_TOOLS, base) >= 0)
		{
			return TOOL_CRAFTING;
		}
		if (indexOf(HUNTER_TOOLS, base) >= 0)
		{
			return TOOL_HUNTER;
		}
		if (indexOf(STORAGE, base) >= 0)
		{
			return TOOL_STORAGE;
		}
		return SKILLING_TOOLS.contains(base) ? TOOL_OTHER : -1;
	}

	private static int toolRank(int type, String base, int storePrice)
	{
		switch (type)
		{
			case TOOL_AXES:
				// felling axes straight after the matching axe
				return tierOrPrice(prefixTier(base, METALS), METALS.length, storePrice) * 2 + (base.contains("felling") ? 1 : 0);
			case TOOL_PICKAXES:
			case TOOL_HARPOONS:
				return tierOrPrice(prefixTier(base, METALS), METALS.length, storePrice);
			case TOOL_FISHING:
				return indexOf(FISHING_TOOLS, base);
			case TOOL_FARMING:
				return indexOf(FARMING_TOOLS, base);
			case TOOL_CRAFTING:
				return indexOf(CRAFTING_TOOLS, base);
			case TOOL_HUNTER:
				return indexOf(HUNTER_TOOLS, base);
			case TOOL_STORAGE:
				return indexOf(STORAGE, base);
			default:
				return 0;
		}
	}

	/**
	 * Tier inside a resource type; anything not listed goes after, by shop price.
	 */
	static int resourceRank(int group, String base, int storePrice)
	{
		switch (group)
		{
			case GROUP_ALLOTMENT_SEEDS:
				return indexOf(ALLOTMENT_SEEDS, base);
			case GROUP_HERB_SEEDS:
				return indexOf(HERB_SEEDS, base);
			case GROUP_TREE_SEEDS:
				return indexOf(TREE_SEEDS, base);
			case GROUP_FRUIT_SEEDS:
				return indexOf(FRUIT_SEEDS, base);
			case GROUP_GRIMY_HERBS:
				return HERBS.indexOf(base.substring(6));
			case GROUP_CLEAN_HERBS:
				return HERBS.indexOf(base);
			case GROUP_UNF_POTIONS:
				return tierOrPrice(herbIndex(base), HERBS.size(), storePrice);
			case GROUP_SECONDARIES:
				return SECONDARIES.indexOf(base);
			case GROUP_ORES:
				return tierOrPrice(indexOf(ORE_ORDER, base), ORE_ORDER.length, storePrice);
			case GROUP_BARS:
				return tierOrPrice(indexOf(BAR_ORDER, base), BAR_ORDER.length, storePrice);
			case GROUP_LOGS:
				return tierOrPrice(indexOf(LOG_ORDER, base), LOG_ORDER.length, storePrice);
			case GROUP_PLANKS:
				return tierOrPrice(indexOf(PLANK_ORDER, base), PLANK_ORDER.length, storePrice);
			case GROUP_FLETCHING_BASICS:
				return indexOf(FLETCHING_BASICS, base);
			case GROUP_ARROWTIPS:
			case GROUP_DART_TIPS:
			case GROUP_JAVELIN_HEADS:
			case GROUP_UNF_BOLTS:
			case GROUP_LIMBS:
				return tierOrPrice(prefixTier(base, METALS), METALS.length, storePrice);
			case GROUP_BOLT_TIPS:
				return tierOrPrice(prefixTier(base, GEM_ORDER), GEM_ORDER.length, storePrice);
			case GROUP_UNSTRUNG_BOWS:
				// shortbow then longbow for each wood, plain wood first
				return (prefixTier(base, WOODS) + 1) * 2 + (base.contains("longbow") ? 1 : 0);
			case GROUP_STOCKS:
				return prefixTier(base, WOODS) + 1;
			case GROUP_UNCUT_GEMS:
				return indexOf(GEM_ORDER, base.substring(6));
			case GROUP_CUT_GEMS:
				return indexOf(GEM_ORDER, base);
			case GROUP_DRAGONHIDE:
			case GROUP_DRAGON_LEATHER:
				return tierOrPrice(prefixTier(base, DRAGONHIDE_COLOURS), DRAGONHIDE_COLOURS.length, storePrice);
			case GROUP_TALISMANS:
			case GROUP_TIARAS:
				return tierOrPrice(indexOf(ALTAR_ORDER, base.substring(0, base.lastIndexOf(' '))), ALTAR_ORDER.length, storePrice);
			default:
				// bones, ashes, raw food and the rest: shop price is a fair tier
				return storePrice;
		}
	}

	/**
	 * Herb tier for an unfinished potion, e.g. "ranarr potion" is ranarr weed.
	 */
	private static int herbIndex(String base)
	{
		String herb = base.endsWith(" potion") ? base.substring(0, base.length() - 7) : base;
		for (int i = 0; i < HERBS.size(); i++)
		{
			if (HERBS.get(i).startsWith(herb))
			{
				return i;
			}
		}
		return -1;
	}

	/**
	 * Resource type, roughly by skill. Returns -1 for items that aren't resources.
	 */
	static int resourceGroup(String n, String base)
	{
		if (indexOf(HERB_SEEDS, base) >= 0)
		{
			return GROUP_HERB_SEEDS;
		}
		if (indexOf(ALLOTMENT_SEEDS, base) >= 0)
		{
			return GROUP_ALLOTMENT_SEEDS;
		}
		if (indexOf(TREE_SEEDS, base) >= 0)
		{
			return GROUP_TREE_SEEDS;
		}
		if (indexOf(FRUIT_SEEDS, base) >= 0)
		{
			return GROUP_FRUIT_SEEDS;
		}
		if (n.contains("seed") || n.contains("sapling"))
		{
			return GROUP_OTHER_SEEDS;
		}
		if (n.contains("compost") || n.equals("plant pot") || n.equals("filled plant pot"))
		{
			return GROUP_FARMING;
		}
		if (n.startsWith("grimy ") && HERBS.contains(base.substring(6)))
		{
			return GROUP_GRIMY_HERBS;
		}
		if (HERBS.contains(base))
		{
			return GROUP_CLEAN_HERBS;
		}
		if (n.endsWith("(unf)") && !n.contains("bolt"))
		{
			return GROUP_UNF_POTIONS;
		}
		if (SECONDARIES.contains(base))
		{
			return GROUP_SECONDARIES;
		}
		if (CONTAINERS.contains(headNoun(base)) && (CONTAINERS.contains(base) || base.contains(" of ")))
		{
			return GROUP_CONTAINERS;
		}
		if (n.endsWith(" ore") || n.equals("coal") || n.equals("amethyst") || n.contains("nugget"))
		{
			return GROUP_ORES;
		}
		if (n.endsWith(" bar") || n.equals("elemental metal"))
		{
			return GROUP_BARS;
		}
		if (n.endsWith("logs") || n.equals("bark"))
		{
			return GROUP_LOGS;
		}
		if (n.endsWith("plank"))
		{
			return GROUP_PLANKS;
		}
		if (indexOf(FLETCHING_BASICS, base) >= 0)
		{
			return GROUP_FLETCHING_BASICS;
		}
		if (n.endsWith("arrowtips"))
		{
			return GROUP_ARROWTIPS;
		}
		if (n.endsWith("dart tip"))
		{
			return GROUP_DART_TIPS;
		}
		if (n.endsWith("javelin heads"))
		{
			return GROUP_JAVELIN_HEADS;
		}
		if (n.contains("bolts (unf)") || n.contains("unfinished bolts"))
		{
			return GROUP_UNF_BOLTS;
		}
		if (n.endsWith("bolt tips"))
		{
			return GROUP_BOLT_TIPS;
		}
		if (n.endsWith("bow (u)"))
		{
			return GROUP_UNSTRUNG_BOWS;
		}
		if (n.endsWith(" limbs"))
		{
			return GROUP_LIMBS;
		}
		if (n.endsWith(" stock"))
		{
			return GROUP_STOCKS;
		}
		if (n.startsWith("uncut ") && GEMS.contains(base.substring(6)))
		{
			return GROUP_UNCUT_GEMS;
		}
		if (GEMS.contains(base))
		{
			return GROUP_CUT_GEMS;
		}
		if (n.endsWith("dragonhide"))
		{
			return GROUP_DRAGONHIDE;
		}
		if (n.endsWith("dragon leather"))
		{
			return GROUP_DRAGON_LEATHER;
		}
		if (n.contains("hide") || n.contains("leather"))
		{
			return GROUP_HIDES;
		}
		if (n.equals("molten glass") || n.equals("bucket of sand") || n.equals("soda ash") || n.contains("seaweed")
			|| n.equals("ball of wool") || n.equals("wool") || n.contains("clay") || n.equals("gold leaf")
			|| n.equals("marble block") || n.equals("limestone brick") || n.startsWith("unpowered orb"))
		{
			return GROUP_CRAFTING;
		}
		if (n.contains("bones"))
		{
			return GROUP_BONES;
		}
		if (n.endsWith(" ashes"))
		{
			return GROUP_ASHES;
		}
		if (n.startsWith("ensouled") || n.endsWith(" remains"))
		{
			return GROUP_ENSOULED;
		}
		if (n.startsWith("raw ") || n.equals("pot of flour") || n.equals("bucket of milk") || n.equals("egg")
			|| n.equals("grapes") || n.equals("cheese"))
		{
			return GROUP_RAW_FOOD;
		}
		if (n.contains("essence"))
		{
			return GROUP_ESSENCE;
		}
		if (n.endsWith(" talisman"))
		{
			return GROUP_TALISMANS;
		}
		if (n.endsWith(" tiara"))
		{
			return GROUP_TIARAS;
		}
		return -1;
	}
}
