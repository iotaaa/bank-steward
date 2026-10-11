package com.banksteward;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.runelite.client.game.ItemEquipmentStats;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PlannerTest
{
	private static final Comparator<BankItem> BY_ID = Comparator.comparingInt(b -> b.getInfo().getId());

	private static int[] tabSizes(int... sizes)
	{
		return Arrays.copyOf(sizes, 10);
	}

	private static BankItem item(int slot, int id, int tab, Category category)
	{
		ItemInfo info = new ItemInfo(id, "Item " + id, "item " + id, 0, Rule.MISC, 0, 0, 0, null, "");
		return new BankItem(slot, id, tab, info, category);
	}

	private static Category cat(String id, int tab)
	{
		return new Category(id, id, 0, tab, "");
	}

	@Test
	public void lisFindsLongestRun()
	{
		assertArrayEquals(new int[]{0, 2, 3}, Planner.lis(new int[]{0, 3, 1, 2}));
		assertEquals(0, Planner.lis(new int[0]).length);
		assertEquals(4, Planner.lis(new int[]{0, 1, 2, 3}).length);
	}

	/**
	 * Following the planner's REORDER steps with insert-mode moves always sorts the tab,
	 * in exactly (size - LIS) moves.
	 */
	@Test
	public void reorderStepsSortRandomTabs()
	{
		Random random = new Random(1);
		Category none = cat("x", Category.NO_TAB);
		for (int trial = 0; trial < 300; trial++)
		{
			int n = 1 + random.nextInt(25);
			List<Integer> ids = new ArrayList<>();
			for (int i = 0; i < n; i++)
			{
				ids.add(i);
			}
			Collections.shuffle(ids, random);

			int expectedMoves = n - Planner.lis(ids.stream().mapToInt(Integer::intValue).toArray()).length;
			int moves = 0;
			while (true)
			{
				List<BankItem> items = new ArrayList<>();
				for (int i = 0; i < n; i++)
				{
					items.add(item(i, ids.get(i), 0, none));
				}
				Planner.Plan plan = Planner.plan(items, tabSizes(), 0, BY_ID);
				if (plan.getNext().getType() == Planner.StepType.DONE)
				{
					break;
				}
				assertEquals(Planner.StepType.REORDER, plan.getNext().getType());
				// insert mode: the dragged item ends up at the target's slot
				int from = plan.getNext().getItem().getSlot();
				int to = plan.getNext().getTargetSlot();
				ids.add(to, ids.remove(from));
				moves++;
				assertTrue("too many moves", moves <= n);
			}
			for (int i = 0; i < n; i++)
			{
				assertEquals(i, (int) ids.get(i));
			}
			assertEquals(expectedMoves, moves);
		}
	}

	@Test
	public void movesBetweenTabsFirst()
	{
		Category gear = cat("gear", 1);
		Category food = cat("food", 2);
		// tab 1: gear, food. tab 2: food, gear
		List<BankItem> items = new ArrayList<>();
		items.add(item(0, 10, 1, gear));
		items.add(item(1, 20, 1, food));
		items.add(item(2, 21, 2, food));
		items.add(item(3, 11, 2, gear));
		Planner.Plan plan = Planner.plan(items, tabSizes(0, 2, 2), 0, BY_ID);
		assertEquals(2, plan.getWrongTab().size());
		Planner.Step step = plan.getNext();
		assertEquals(Planner.StepType.MOVE_TO_TAB, step.getType());
		// lowest target tab first
		assertEquals(1, step.getTab());
		assertEquals(11, step.getItem().getItemId());
	}

	@Test
	public void asksForAllTabWhenMoveIsHidden()
	{
		Category gear = cat("gear", 1);
		Category food = cat("food", 2);
		List<BankItem> items = new ArrayList<>();
		items.add(item(0, 10, 1, gear));
		items.add(item(1, 20, 2, food));
		items.add(item(2, 11, 2, gear));
		// viewing tab 1, but the wrong item is in tab 2
		Planner.Plan plan = Planner.plan(items, tabSizes(0, 1, 2), 1, BY_ID);
		assertEquals(Planner.StepType.OPEN_TAB, plan.getNext().getType());
		assertEquals(0, plan.getNext().getTab());
	}

	@Test
	public void makesNewTabAndWarnsAboutGaps()
	{
		Category gear = cat("gear", 1);
		Category food = cat("food", 2);
		Category runes = cat("runes", 4);
		List<BankItem> items = new ArrayList<>();
		items.add(item(0, 10, 1, gear));
		items.add(item(1, 20, 1, food));
		items.add(item(2, 30, 1, runes));
		Planner.Plan plan = Planner.plan(items, tabSizes(0, 3), 0, BY_ID);
		Planner.Step step = plan.getNext();
		assertEquals(Planner.StepType.MOVE_TO_TAB, step.getType());
		assertEquals(2, step.getTab());
		assertTrue(step.isNewTab());
		assertEquals(1, plan.getWarnings().size());
	}

	@Test
	public void avoidsEmptyingATab()
	{
		Category gear = cat("gear", 1);
		Category misc = cat("misc", 0);
		List<BankItem> items = new ArrayList<>();
		// tab 1 has only a misc item; moving it out first would delete tab 1
		items.add(item(0, 50, 1, misc));
		items.add(item(1, 10, 0, gear));
		Planner.Plan plan = Planner.plan(items, tabSizes(0, 1), 0, BY_ID);
		assertEquals(10, plan.getNext().getItem().getItemId());
	}

	@Test
	public void doneWhenSorted()
	{
		Category gear = cat("gear", 1);
		List<BankItem> items = new ArrayList<>();
		items.add(item(0, 10, 1, gear));
		items.add(item(1, 11, 1, gear));
		Planner.Plan plan = Planner.plan(items, tabSizes(0, 2), 1, BY_ID);
		assertEquals(Planner.StepType.DONE, plan.getNext().getType());
		assertEquals(0, plan.getOutOfPlace());
	}

	@Test
	public void classifiesCommonItems()
	{
		ItemEquipmentStats whip = ItemEquipmentStats.builder().slot(3).aslash(82).str(82).build();
		ItemEquipmentStats rune = ItemEquipmentStats.builder().slot(13).arange(0).build();
		ItemEquipmentStats glory = ItemEquipmentStats.builder().slot(2).astab(10).aslash(10).acrush(10).build();
		ItemEquipmentStats boots = ItemEquipmentStats.builder().slot(10).build();

		assertEquals(Rule.GEAR, ItemRules.classify(1, "Abyssal whip", true, whip, null, 120001).getRule());
		assertEquals(Rule.RUNES, ItemRules.classify(2, "Rune arrow", true, rune, null, 400).getRule());
		assertEquals(Rule.RUNES, ItemRules.classify(3, "Nature rune", false, null, null, 372).getRule());
		assertEquals(Rule.TELEPORTS, ItemRules.classify(4, "Amulet of glory(4)", true, glory, null, 17625).getRule());
		assertEquals(Rule.POTIONS, ItemRules.classify(5, "Prayer potion(3)", false, null, new String[]{"Drink", null}, 152).getRule());
		assertEquals(Rule.FOOD, ItemRules.classify(6, "Shark", false, null, new String[]{"Eat"}, 300).getRule());
		assertEquals(Rule.MONEY, ItemRules.classify(7, "Coins", false, null, null, 1).getRule());
		assertEquals(Rule.RESOURCES, ItemRules.classify(8, "Grimy ranarr weed", false, null, null, 25).getRule());
		assertEquals(Rule.RESOURCES, ItemRules.classify(9, "Dragon bones", false, null, null, 1).getRule());
		assertEquals(Rule.SKILLING, ItemRules.classify(10, "Dragon pickaxe", true, whip, null, 1).getRule());
		assertEquals(Rule.SKILLING, ItemRules.classify(11, "Hammer", false, null, null, 1).getRule());
		assertEquals(Rule.COSMETIC, ItemRules.classify(12, "Pink boots", true, boots, null, 1).getRule());
		assertEquals(Rule.TELEPORTS, ItemRules.classify(13, "Varrock teleport", false, null, null, 1).getRule());
		assertEquals(Rule.GEAR, ItemRules.classify(14, "Dragon battleaxe", true, whip, null, 1).getRule());
		assertEquals(Rule.MISC, ItemRules.classify(15, "Ghostspeak book", false, null, null, 1).getRule());
		assertEquals(Rule.TELEPORTS, ItemRules.classify(16, "Strength cape(t)", true, glory, null, 99000).getRule());
		ItemEquipmentStats body = ItemEquipmentStats.builder().slot(4).dstab(10).build();
		assertEquals(Rule.SKILLING, ItemRules.classify(17, "Varrock armour 4", true, body, null, 1).getRule());
		assertEquals(Rule.TELEPORTS, ItemRules.classify(18, "Ardougne cloak 4", true, boots, null, 1).getRule());
		// found by their options, not their names
		assertEquals(Rule.TELEPORTS, ItemRules.classify(19, "Some diary cape", true, boots,
			new String[]{null, "Wear", "Teleport", null, "Drop"}, 1).getRule());
		assertEquals(Rule.TELEPORTS, ItemRules.classify(20, "Some ring", true, boots,
			new String[]{null, "Wear", "Rub", null, "Drop"}, 1).getRule());
		assertEquals(Rule.MISC, ItemRules.classify(21, "Antique lamp", false, null,
			new String[]{"Rub", null, null, null, "Drop"}, 1).getRule());
	}

	@Test
	public void potionsGroupByNameWithHighestDoseFirst()
	{
		ItemInfo p4 = ItemRules.classify(1, "Prayer potion(4)", false, null, new String[]{"Drink"}, 1);
		ItemInfo p1 = ItemRules.classify(2, "Prayer potion(1)", false, null, new String[]{"Drink"}, 1);
		ItemInfo s4 = ItemRules.classify(3, "Super restore(4)", false, null, new String[]{"Drink"}, 1);
		assertEquals("prayer potion", p4.getBaseName());
		assertEquals(4, p4.getSuffixNumber());
		Category c = cat("potions", 4);
		List<BankItem> items = new ArrayList<>();
		items.add(new BankItem(0, 2, 4, p1, c));
		items.add(new BankItem(1, 1, 4, p4, c));
		items.add(new BankItem(2, 3, 4, s4, c));
		items.sort(ItemSorter.comparator(Collections.singletonMap("potions", 0), BankStewardConfig.GearSort.STYLE));
		// super restore is higher in the potion list than prayer potion
		assertEquals(3, items.get(0).getItemId());
		assertEquals(1, items.get(1).getItemId());
		assertEquals(2, items.get(2).getItemId());
	}

	private static List<String> sortResources(String... names)
	{
		Category c = cat("resources", 7);
		List<BankItem> items = new ArrayList<>();
		// shuffled shop prices so tier order can't come from price
		int[] prices = {50, 5, 900, 20, 300, 1, 70, 400, 10, 600, 30, 200};
		for (int i = 0; i < names.length; i++)
		{
			ItemInfo info = ItemRules.classify(i, names[i], false, null, null, prices[i % prices.length]);
			assertEquals(names[i], Rule.RESOURCES, info.getRule());
			items.add(new BankItem(i, i, 7, info, c));
		}
		return sortedNames(items, "resources");
	}

	private static List<String> sortedNames(List<BankItem> items, String categoryId)
	{
		items.sort(ItemSorter.comparator(Collections.singletonMap(categoryId, 0), BankStewardConfig.GearSort.STYLE, new ArrayList<>(items)));
		List<String> out = new ArrayList<>();
		for (BankItem b : items)
		{
			out.add(b.getInfo().getName());
		}
		return out;
	}

	@Test
	public void oresThenBarsByTier()
	{
		assertEquals(Arrays.asList("Copper ore", "Iron ore", "Coal", "Mithril ore", "Runite ore",
			"Bronze bar", "Steel bar", "Gold bar", "Adamantite bar"),
			sortResources("Adamantite bar", "Coal", "Runite ore", "Bronze bar", "Gold bar", "Iron ore",
				"Steel bar", "Copper ore", "Mithril ore"));
	}

	@Test
	public void uncutGemsThenCutByTier()
	{
		assertEquals(Arrays.asList("Uncut sapphire", "Uncut ruby", "Uncut dragonstone",
			"Sapphire", "Ruby", "Dragonstone"),
			sortResources("Ruby", "Uncut dragonstone", "Sapphire", "Uncut sapphire", "Dragonstone", "Uncut ruby"));
	}

	@Test
	public void essenceThenTalismansThenTiaras()
	{
		assertEquals(Arrays.asList("Pure essence", "Air talisman", "Fire talisman", "Nature talisman",
			"Air tiara", "Fire tiara", "Nature tiara"),
			sortResources("Nature tiara", "Fire talisman", "Air tiara", "Pure essence", "Nature talisman",
				"Fire tiara", "Air talisman"));
	}

	@Test
	public void logsThenPlanksByTier()
	{
		assertEquals(Arrays.asList("Logs", "Oak logs", "Maple logs", "Magic logs", "Plank", "Oak plank",
			"Mahogany plank"),
			sortResources("Magic logs", "Oak plank", "Logs", "Mahogany plank", "Maple logs", "Plank", "Oak logs"));
	}

	@Test
	public void outfitSetsStayTogetherHeadToBoots()
	{
		String[][] pieces = {
			{"Angler top", "4"}, {"Graceful boots", "10"}, {"Spirit angler headband", "0"}, {"Graceful hood", "0"},
			{"Angler hat", "0"}, {"Graceful top", "4"}, {"Spirit angler top", "4"}, {"Graceful cape", "1"},
			{"Angler boots", "10"}, {"Graceful legs", "7"}};
		int[] prices = {900, 5, 300, 40, 1, 600, 20, 70, 200, 10};
		Category c = cat("skilling", 6);
		List<BankItem> items = new ArrayList<>();
		for (int i = 0; i < pieces.length; i++)
		{
			ItemEquipmentStats stats = ItemEquipmentStats.builder().slot(Integer.parseInt(pieces[i][1])).build();
			ItemInfo info = ItemRules.classify(i, pieces[i][0], true, stats, null, prices[i]);
			assertEquals(pieces[i][0], Rule.SKILLING, info.getRule());
			items.add(new BankItem(i, i, 6, info, c));
		}
		// fishing outfits, then agility
		assertEquals(Arrays.asList("Angler hat", "Angler top", "Angler boots",
			"Spirit angler headband", "Spirit angler top",
			"Graceful hood", "Graceful cape", "Graceful top", "Graceful legs", "Graceful boots"),
			sortedNames(items, "skilling"));
	}

	private static List<String> sortAll(Object[][] defs)
	{
		// {name, equipable, slot or -1, actions or null}; shop prices are shuffled so price can't decide
		int[] prices = {50, 5, 900, 20, 300, 1, 70, 400, 10, 600, 30, 200, 15, 800};
		Category c = cat("all", 1);
		List<BankItem> items = new ArrayList<>();
		for (int i = 0; i < defs.length; i++)
		{
			String name = (String) defs[i][0];
			boolean equip = (Boolean) defs[i][1];
			int slot = (Integer) defs[i][2];
			ItemEquipmentStats stats = slot < 0 ? null : ItemEquipmentStats.builder().slot(slot).astab(slot == 3 ? 10 : 0).build();
			ItemInfo info = ItemRules.classify(i, name, equip, stats, (String[]) defs[i][3], prices[i % prices.length]);
			items.add(new BankItem(i, i, 1, info, c));
		}
		return sortedNames(items, "all");
	}

	@Test
	public void herbsGrimyThenCleanThenUnfinished()
	{
		assertEquals(Arrays.asList("Grimy guam leaf", "Grimy ranarr weed", "Grimy torstol", "Guam leaf",
			"Ranarr weed", "Torstol", "Guam potion (unf)", "Ranarr potion (unf)", "Torstol potion (unf)"),
			sortResources("Torstol", "Ranarr potion (unf)", "Grimy torstol", "Guam leaf", "Grimy guam leaf",
				"Torstol potion (unf)", "Ranarr weed", "Guam potion (unf)", "Grimy ranarr weed"));
	}

	@Test
	public void ammoByTypeThenMetal()
	{
		assertEquals(Arrays.asList("Iron arrow", "Rune arrow", "Dragon arrow", "Steel bolts", "Runite bolts",
			"Ruby bolts (e)", "Ruby dragon bolts (e)", "Mithril dart", "Rune dart"),
			sortAll(new Object[][]{
				{"Rune dart", true, 3, null}, {"Ruby dragon bolts (e)", true, 13, null}, {"Dragon arrow", true, 13, null},
				{"Steel bolts", true, 13, null}, {"Iron arrow", true, 13, null}, {"Mithril dart", true, 3, null},
				{"Ruby bolts (e)", true, 13, null}, {"Runite bolts", true, 13, null}, {"Rune arrow", true, 13, null}}));
	}

	@Test
	public void toolsByTypeThenMetal()
	{
		// by skill: mining, smithing, woodcutting, farming, crafting
		assertEquals(Arrays.asList("Iron pickaxe", "Dragon pickaxe", "Hammer", "Bronze axe", "Rune axe", "Dragon axe",
			"Dragon felling axe", "Rake", "Spade", "Chisel"),
			sortAll(new Object[][]{
				{"Chisel", false, -1, null}, {"Dragon pickaxe", true, 3, null}, {"Spade", false, -1, null},
				{"Dragon felling axe", true, 3, null}, {"Rune axe", true, 3, null}, {"Hammer", false, -1, null},
				{"Iron pickaxe", true, 3, null}, {"Bronze axe", true, 3, null}, {"Rake", false, -1, null},
				{"Dragon axe", true, 3, null}}));
	}

	@Test
	public void jewelleryKindsStayTogether()
	{
		assertEquals(Arrays.asList("Games necklace(8)", "Games necklace(3)", "Ring of dueling(8)",
			"Ring of dueling(2)", "Amulet of glory(6)", "Amulet of glory(1)"),
			sortAll(new Object[][]{
				{"Ring of dueling(2)", true, 12, null}, {"Amulet of glory(1)", true, 2, null},
				{"Games necklace(3)", true, 2, null}, {"Ring of dueling(8)", true, 12, null},
				{"Amulet of glory(6)", true, 2, null}, {"Games necklace(8)", true, 2, null}}));
	}

	@Test
	public void unlistedPotionsKeepDosesTogether()
	{
		String[] drink = {"Drink"};
		assertEquals(Arrays.asList("Prayer potion(4)", "Prayer potion(2)", "Agility potion(4)", "Agility potion(1)",
			"Hunter potion(3)", "Hunter potion(2)"),
			sortAll(new Object[][]{
				{"Hunter potion(2)", false, -1, drink}, {"Agility potion(1)", false, -1, drink},
				{"Prayer potion(2)", false, -1, drink}, {"Hunter potion(3)", false, -1, drink},
				{"Agility potion(4)", false, -1, drink}, {"Prayer potion(4)", false, -1, drink}}));
	}

	private static Object[] gear(String name, int slot, int stab, int str, int mage, int range, int def)
	{
		return new Object[]{name, slot, ItemEquipmentStats.builder().slot(slot).astab(stab).aslash(stab).acrush(stab)
			.str(str).amagic(mage).arange(range).dstab(def).dslash(def).dcrush(def).build()};
	}

	@Test
	public void gearGroupedByWeaponKindAndArmourSet()
	{
		Object[][] defs = {
			gear("Rune platebody", 4, 0, 0, -30, -10, 80),
			gear("Dragon scimitar", 3, 67, 66, 0, 0, 0),
			gear("Abyssal whip", 3, 82, 82, 0, 0, 0),
			gear("Rune full helm", 0, 0, 0, -6, -2, 30),
			gear("Dharok's platelegs", 7, 0, 0, -21, -7, 85),
			gear("Rune scimitar", 3, 45, 44, 0, 0, 0),
			gear("Dharok's helm", 0, 0, 0, -3, -1, 45),
			gear("Rune platelegs", 7, 0, 0, -21, -7, 50),
			gear("Amulet of strength", 2, 0, 10, 0, 0, 0),
			gear("Black d'hide body", 4, 0, 0, -15, 30, 55),
			gear("Black d'hide chaps", 7, 0, 0, -10, 17, 31),
			gear("Rune kiteshield", 5, 0, 0, -8, -2, 50),
		};
		Category c = cat("gear", 1);
		List<BankItem> items = new ArrayList<>();
		for (int i = 0; i < defs.length; i++)
		{
			ItemInfo info = ItemRules.classify(i, (String) defs[i][0], true, (ItemEquipmentStats) defs[i][2], null, 1);
			assertEquals((String) defs[i][0], Rule.GEAR, info.getRule());
			items.add(new BankItem(i, i, 1, info, c));
		}
		items.sort(ItemSorter.comparator(Collections.singletonMap("gear", 0), BankStewardConfig.GearSort.STYLE, items));
		List<String> names = new ArrayList<>();
		for (BankItem b : items)
		{
			names.add(b.getInfo().getName());
		}
		assertEquals(Arrays.asList(
			// melee weapons: whip, then scimitars strongest first
			"Abyssal whip", "Dragon scimitar", "Rune scimitar",
			// melee armour sets, strongest set first, pieces head to shield
			"Dharok's helm", "Dharok's platelegs", "Rune full helm", "Rune platebody", "Rune platelegs", "Rune kiteshield",
			// melee accessories
			"Amulet of strength",
			// ranged set
			"Black d'hide body", "Black d'hide chaps"), names);
	}

	@Test
	public void similarNamesSitTogether()
	{
		assertEquals("cannonball", ItemRules.headNoun("granite cannonball"));
		assertEquals("page", ItemRules.headNoun("ancient page 12"));
		// grouped by main word (book, cannonball, page), metal ones by tier first
		assertEquals(Arrays.asList("Ghostspeak book", "Holy book", "Mithril cannonball", "Rune cannonball",
			"Cannonball", "Granite cannonball", "Ancient page 1", "Ancient page 2"),
			sortAll(new Object[][]{
				{"Granite cannonball", false, -1, null}, {"Holy book", false, -1, null},
				{"Rune cannonball", false, -1, null}, {"Ancient page 2", false, -1, null},
				{"Cannonball", false, -1, null}, {"Ghostspeak book", false, -1, null},
				{"Mithril cannonball", false, -1, null}, {"Ancient page 1", false, -1, null}}));
	}

	@Test
	public void exactSameNamesStayTogether()
	{
		Category c = cat("res", 7);
		List<BankItem> items = new ArrayList<>();
		// two "Bones" with very different shop prices would otherwise be split by "Big bones"
		items.add(new BankItem(0, 1, 7, ItemRules.classify(1, "Bones", false, null, null, 1), c));
		items.add(new BankItem(1, 2, 7, ItemRules.classify(2, "Big bones", false, null, null, 50), c));
		items.add(new BankItem(2, 3, 7, ItemRules.classify(3, "Bones", false, null, null, 999), c));
		items.sort(ItemSorter.comparator(Collections.singletonMap("res", 0), BankStewardConfig.GearSort.STYLE, items));
		assertEquals(1, items.get(0).getItemId());
		assertEquals(3, items.get(1).getItemId());
		assertEquals(2, items.get(2).getItemId());
	}

	@Test
	public void skillingGoesSkillBySkill()
	{
		ItemEquipmentStats tool = ItemEquipmentStats.builder().slot(3).astab(1).build();
		ItemEquipmentStats body = ItemEquipmentStats.builder().slot(4).build();
		Object[][] defs = {
			{"Fish barrel", false, null}, {"Prospector jacket", true, body}, {"Dragon harpoon", true, tool},
			{"Coal bag", false, null}, {"Angler top", true, body}, {"Rune pickaxe", true, tool}};
		Category c = cat("skilling", 6);
		List<BankItem> items = new ArrayList<>();
		for (int i = 0; i < defs.length; i++)
		{
			ItemInfo info = ItemRules.classify(i, (String) defs[i][0], (Boolean) defs[i][1], (ItemEquipmentStats) defs[i][2], null, 1);
			assertEquals((String) defs[i][0], Rule.SKILLING, info.getRule());
			items.add(new BankItem(i, i, 6, info, c));
		}
		// mining: tool, outfit, storage; then fishing the same way
		assertEquals(Arrays.asList("Rune pickaxe", "Prospector jacket", "Coal bag", "Dragon harpoon", "Angler top",
			"Fish barrel"), sortedNames(items, "skilling"));
	}

	@Test
	public void containersStayTogether()
	{
		assertEquals("bucket", ItemRules.headNoun("bucket of sand"));
		assertEquals("burnt", ItemRules.headNoun("burnt shark"));
		assertEquals("burnt", ItemRules.headNoun("burnt lobster"));
		assertEquals(Arrays.asList("Bucket", "Bucket of milk", "Bucket of sand", "Bucket of water", "Jug",
			"Jug of water"),
			sortAll(new Object[][]{
				{"Jug of water", false, -1, null}, {"Bucket of sand", false, -1, null}, {"Bucket", false, -1, null},
				{"Jug", false, -1, null}, {"Bucket of water", false, -1, null}, {"Bucket of milk", false, -1, null}}));
	}

	@Test
	public void skillRewardsGoWithTheirSkill()
	{
		ItemInfo marks = ItemRules.classify(1, "Mark of grace", false, null, null, 1);
		assertEquals(Rule.SKILLING, marks.getRule());
		ItemEquipmentStats body = ItemEquipmentStats.builder().slot(4).build();
		ItemInfo graceful = ItemRules.classify(2, "Graceful top", true, body, null, 1);
		ItemInfo pickaxe = ItemRules.classify(3, "Rune pickaxe", true, ItemEquipmentStats.builder().slot(3).astab(1).build(), null, 1);
		Category c = cat("skilling", 6);
		List<BankItem> items = new ArrayList<>();
		items.add(new BankItem(0, 1, 6, marks, c));
		items.add(new BankItem(1, 2, 6, graceful, c));
		items.add(new BankItem(2, 3, 6, pickaxe, c));
		// mining first, then agility: graceful, then marks of grace
		assertEquals(Arrays.asList("Rune pickaxe", "Graceful top", "Mark of grace"), sortedNames(items, "skilling"));
	}

	@Test
	public void tabIconsStayPut()
	{
		Category gear = cat("gear", 1);
		Category food = cat("food", 2);
		List<BankItem> items = new ArrayList<>();
		// tab 1: a food item as its icon, then gear out of order
		items.add(item(0, 20, 1, food));
		items.add(item(1, 12, 1, gear));
		items.add(item(2, 11, 1, gear));
		Planner.Plan plan = Planner.plan(items, tabSizes(0, 3), 1, BY_ID, true);
		// the icon isn't moved to tab 2 or sorted; only the gear is reordered, behind it
		assertTrue(plan.getWrongTab().isEmpty());
		assertEquals(Planner.StepType.REORDER, plan.getNext().getType());
		assertTrue(plan.getNext().getItem().getItemId() != 20);
		assertTrue(plan.getNext().getTargetSlot() >= 1);
		assertEquals(1, plan.getOutOfPlace());
		assertTrue(plan.getTabIcons().contains(0));
	}

	@Test
	public void iconsGroupFollowsIt()
	{
		ItemEquipmentStats helm = ItemEquipmentStats.builder().slot(0).amagic(-6).arange(-3).dstab(30).build();
		ItemEquipmentStats body = ItemEquipmentStats.builder().slot(4).amagic(-30).arange(-15).dstab(82).build();
		ItemEquipmentStats sickle = ItemEquipmentStats.builder().slot(3).dslash(1).str(1).build();
		Category c = cat("gear", 1);
		List<BankItem> items = new ArrayList<>();
		// tab 1: rune full helm is the icon, then the sickle, then the platebody
		items.add(new BankItem(0, 1163, 1, ItemRules.classify(1163, "Rune full helm", true, helm, null, 1), c));
		items.add(new BankItem(1, 2961, 1, ItemRules.classify(2961, "Silver sickle", true, sickle, null, 1), c));
		items.add(new BankItem(2, 1127, 1, ItemRules.classify(1127, "Rune platebody", true, body, null, 1), c));
		Comparator<BankItem> order = ItemSorter.comparator(Collections.singletonMap("gear", 0), BankStewardConfig.GearSort.STYLE, items);
		Planner.Plan plan = Planner.plan(items, tabSizes(0, 3), 1, order, true);
		// the platebody belongs next to the helm, so the sickle has to move after it
		assertEquals(Planner.StepType.REORDER, plan.getNext().getType());
		assertEquals(1, plan.getOutOfPlace());
		assertEquals("sickle", ItemSorter.family(ItemRules.classify(2963, "Silver sickle (b)", true, sickle, null, 1)));
	}

	@Test
	public void itemsSharingAWordInTheBankGoTogether()
	{
		// no rules for any of these: they group by the words they share
		assertEquals(Arrays.asList("Goblin mail", "Goblin skull", "Strange fruit", "Strange shard", "Zogre tooth"),
			sortAll(new Object[][]{
				{"Strange shard", false, -1, null}, {"Goblin skull", false, -1, null}, {"Zogre tooth", false, -1, null},
				{"Goblin mail", false, -1, null}, {"Strange fruit", false, -1, null}}));
		// a word shared by more items wins: three "shard"s beat two "blue"s
		List<String> order = sortAll(new Object[][]{
			{"Blue shard", false, -1, null}, {"Ancient shard", false, -1, null}, {"Blue key", false, -1, null},
			{"Dark shard", false, -1, null}});
		assertEquals(Arrays.asList("Blue key", "Ancient shard", "Blue shard", "Dark shard"), order);
	}

	@Test
	public void stavesAreMagicAndFairyRingItemsSitTogether()
	{
		// a staff with more crush than magic attack is still a magic weapon
		ItemEquipmentStats fireStaff = ItemEquipmentStats.builder().slot(3).acrush(7).str(5).amagic(10).build();
		assertEquals(2, ItemSorter.style(ItemRules.classify(1387, "Staff of fire", true, fireStaff, null, 1)));

		List<String> order = sortAll(new Object[][]{
			{"Lunar staff", true, 3, null}, {"Karamja gloves 3", true, 9, null}, {"Ardougne cloak 2", true, 1, null},
			{"Dramen staff", true, 3, null}, {"Desert amulet 2", true, 2, null}});
		int cloak = order.indexOf("Ardougne cloak 2");
		assertEquals("Dramen staff", order.get(cloak + 1));
		assertEquals("Lunar staff", order.get(cloak + 2));
	}

	@Test
	public void itemsUsedTogetherSitTogetherInTheSameTab()
	{
		Category res = cat("resources", 7);
		Category tools = cat("skilling", 6);
		List<BankItem> items = new ArrayList<>();
		items.add(new BankItem(0, 1, 7, ItemRules.classify(1, "Snape grass", false, null, null, 1), res));
		items.add(new BankItem(1, 2, 7, ItemRules.classify(2, "Ranarr potion (unf)", false, null, null, 1), res));
		items.add(new BankItem(2, 3, 7, ItemRules.classify(3, "Toadflax potion (unf)", false, null, null, 1), res));
		items.add(new BankItem(3, 4, 7, ItemRules.classify(4, "Uncut ruby", false, null, null, 1), res));
		// the chisel's category uses another tab, so it doesn't move in beside the gems
		items.add(new BankItem(4, 5, 6, ItemRules.classify(5, "Chisel", false, null, null, 1), tools));
		Map<String, Integer> order = new java.util.HashMap<>();
		order.put("resources", 0);
		order.put("skilling", 1);
		items.sort(ItemSorter.comparator(order, BankStewardConfig.GearSort.STYLE, new ArrayList<>(items)));
		List<String> names = new ArrayList<>();
		for (BankItem b : items)
		{
			names.add(b.getInfo().getName());
		}
		int ranarr = names.indexOf("Ranarr potion (unf)");
		assertEquals("Snape grass", names.get(ranarr + 1));
		assertEquals("Chisel", names.get(names.size() - 1));

		// in the same tab, the chisel goes right before the uncut gems
		List<BankItem> sameTab = new ArrayList<>();
		sameTab.add(new BankItem(0, 4, 7, ItemRules.classify(4, "Uncut ruby", false, null, null, 1), res));
		sameTab.add(new BankItem(1, 6, 7, ItemRules.classify(6, "Coal", false, null, null, 1), res));
		sameTab.add(new BankItem(2, 5, 7, ItemRules.classify(5, "Chisel", false, null, null, 1), res));
		sameTab.sort(ItemSorter.comparator(Collections.singletonMap("resources", 0), BankStewardConfig.GearSort.STYLE, new ArrayList<>(sameTab)));
		List<String> n2 = new ArrayList<>();
		for (BankItem b : sameTab)
		{
			n2.add(b.getInfo().getName());
		}
		assertEquals(n2.indexOf("Uncut ruby") - 1, n2.indexOf("Chisel"));
	}
}
