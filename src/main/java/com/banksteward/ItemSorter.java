package com.banksteward;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemEquipmentStats;

/**
 * Orders items inside a tab: by category (in list order), then by each category's own rules.
 */
final class ItemSorter
{
	private static final int STYLE_MELEE = 0;
	private static final int STYLE_RANGED = 1;
	private static final int STYLE_MAGIC = 2;
	private static final int STYLE_OTHER = 3;

	private ItemSorter()
	{
	}

	/**
	 * @param categoryOrder category id to its position in the category list
	 */
	static Comparator<BankItem> comparator(Map<String, Integer> categoryOrder, BankStewardConfig.GearSort gearSort)
	{
		return comparator(categoryOrder, gearSort, Collections.emptyList());
	}

	/**
	 * @param categoryOrder category id to its position in the category list
	 * @param bank          the whole bank, used to order gear sets by their strongest piece and give each set one style
	 */
	static Comparator<BankItem> comparator(Map<String, Integer> categoryOrder, BankStewardConfig.GearSort gearSort,
		Collection<BankItem> bank)
	{
		return comparator(categoryOrder, gearSort, bank, Collections.emptyMap());
	}

	/**
	 * @param alchProfits profit per cast from Alch Ledger, by item id; items in the High alch category go best first
	 */
	static Comparator<BankItem> comparator(Map<String, Integer> categoryOrder, BankStewardConfig.GearSort gearSort,
		Collection<BankItem> bank, Map<Integer, Integer> alchProfits)
	{
		GearContext gear = new GearContext(bank);
		WordGroups words = new WordGroups(bank);
		Comparator<BankItem> gearOrder = gearSort == BankStewardConfig.GearSort.STYLE
			? gear.bySet()
			: Comparator.comparingInt((BankItem b) -> slotOrder(b.getInfo().getStats()))
				.thenComparingInt(b -> style(b.getInfo()))
				.thenComparingInt(b -> -score(b.getInfo()));

		Comparator<BankItem> order = Comparator
			.comparingInt((BankItem b) -> b.getCategory() == null
				? Integer.MAX_VALUE
				: categoryOrder.getOrDefault(b.getCategory().getId(), Integer.MAX_VALUE))
			.thenComparingInt(b -> b.getCategory() != null && CategoryStore.HIGH_ALCH.equals(b.getCategory().getId())
				? -alchProfits.getOrDefault(b.getInfo().getId(), Integer.MIN_VALUE + 1)
				: 0)
			.thenComparingInt(b -> b.getInfo().getRule().ordinal())
			.thenComparing((x, y) -> x.getInfo().getRule() == Rule.GEAR && y.getInfo().getRule() == Rule.GEAR
				? gearOrder.compare(x, y)
				: 0)
			.thenComparingInt(b -> b.getInfo().getRule() == Rule.GEAR ? 0 : b.getInfo().getGroup())
			.thenComparingInt(b -> words.loose(b) ? words.clusterRank(b) : 0)
			.thenComparing(b -> words.loose(b) ? words.cluster(b) : b.getInfo().getFamily())
			.thenComparingInt(b -> b.getInfo().getRule() == Rule.GEAR ? 0 : b.getInfo().getRank())
			.thenComparing(b -> b.getInfo().getBaseName())
			.thenComparingInt(b -> -b.getInfo().getSuffixNumber());

		// Items with exactly the same name (recolours, clue steps...) sort as one item, so they always sit together
		Map<String, BankItem> byName = new HashMap<>();
		for (BankItem b : bank)
		{
			byName.merge(b.getInfo().getName().toLowerCase(), b,
				(x, y) -> x.getInfo().getId() <= y.getInfo().getId() ? x : y);
		}
		// Items used with each other (a secondary and its unfinished potion, a chisel and uncut gems) sit
		// together when they share a tab
		UsedTogether together = new UsedTogether(bank, order);
		return Comparator
			.comparing((BankItem b) ->
			{
				BankItem a = together.anchor(b);
				return byName.getOrDefault(a.getInfo().getName().toLowerCase(), a);
			}, order)
			.thenComparingInt(together::offset)
			.thenComparingInt(b -> b.getInfo().getId())
			.thenComparingInt(BankItem::getItemId);
	}

	/**
	 * Groups items by the words they share with other items in the bank, so things go together even when
	 * there's no rule for them: "Silver sickle" and "Silver sickle (b)", every "Bucket of ...", all "Burnt" food.
	 * Only used where a category has no fixed order of its own. Each item joins the group of its word that the
	 * most other items share. Food and resource groups are ordered by their best member, others A-Z.
	 */
	private static final class WordGroups
	{
		private final Map<String, String> clusterByName = new HashMap<>();
		private final Map<String, Integer> clusterRank = new HashMap<>();

		WordGroups(Collection<BankItem> bank)
		{
			// how many differently named items in each block share each word
			Map<String, Map<String, Integer>> counts = new HashMap<>();
			Map<String, BankItem> byName = new HashMap<>();
			for (BankItem b : bank)
			{
				if (loose(b))
				{
					byName.putIfAbsent(b.getInfo().getName().toLowerCase(), b);
				}
			}
			for (BankItem b : byName.values())
			{
				Map<String, Integer> c = counts.computeIfAbsent(block(b), k -> new HashMap<>());
				for (String w : new java.util.LinkedHashSet<>(ItemRules.words(b.getInfo().getBaseName())))
				{
					c.merge(w, 1, Integer::sum);
				}
			}
			for (Map.Entry<String, BankItem> e : byName.entrySet())
			{
				BankItem b = e.getValue();
				Map<String, Integer> c = counts.get(block(b));
				String head = ItemRules.headNoun(b.getInfo().getBaseName());
				String best = null;
				int bestCount = 1;
				int bestPos = -1;
				java.util.List<String> ws = ItemRules.words(b.getInfo().getBaseName());
				for (int i = 0; i < ws.size(); i++)
				{
					String w = ws.get(i);
					int n = c.getOrDefault(w, 0);
					// most shared word wins; on a tie the main word, then the later word
					boolean better = n > bestCount
						|| n == bestCount && best != null && (w.equals(head) && !best.equals(head)
							|| !best.equals(head) && i > bestPos);
					if (n >= 2 && better)
					{
						best = w;
						bestCount = n;
						bestPos = i;
					}
				}
				String cluster = block(b) + "|" + (best != null ? best : head);
				clusterByName.put(e.getKey(), cluster);
			}
			for (BankItem b : bank)
			{
				if (loose(b))
				{
					clusterRank.merge(cluster(b), b.getInfo().getRank(), Math::min);
				}
			}
		}

		private static String block(BankItem b)
		{
			String category = b.getCategory() == null ? "" : b.getCategory().getId();
			return category + "|" + b.getInfo().getRule() + "|" + b.getInfo().getGroup();
		}

		boolean loose(BankItem b)
		{
			return ItemRules.isLoose(b.getInfo().getRule(), b.getInfo().getGroup());
		}

		String cluster(BankItem b)
		{
			return clusterByName.getOrDefault(b.getInfo().getName().toLowerCase(),
				block(b) + "|" + ItemRules.headNoun(b.getInfo().getBaseName()));
		}

		/**
		 * Food groups go best healing first and resource groups by tier; elsewhere groups go A-Z.
		 */
		int clusterRank(BankItem b)
		{
			Rule rule = b.getInfo().getRule();
			if (rule != Rule.FOOD && rule != Rule.RESOURCES)
			{
				return 0;
			}
			return clusterRank.getOrDefault(cluster(b), b.getInfo().getRank());
		}
	}

	private static final int SECTION_WEAPONS = 0;
	private static final int SECTION_ARMOUR = 1;
	private static final int SECTION_ACCESSORIES = 2;

	/**
	 * Gear is grouped into weapons (by kind, e.g. all scimitars), armour (by set, e.g. all rune pieces)
	 * and accessories (amulets, rings, capes, ammo-slot items). Each family gets one combat style, worked out
	 * from all its pieces, so a set never gets split, and families are ordered strongest first.
	 */
	private static final class GearContext
	{
		private final Map<String, int[]> styleTotals = new HashMap<>();
		private final Map<String, Integer> best = new HashMap<>();

		GearContext(Collection<BankItem> bank)
		{
			for (BankItem b : bank)
			{
				if (b.getInfo().getRule() != Rule.GEAR)
				{
					continue;
				}
				ItemInfo info = b.getInfo();
				String family = family(info);
				int[] totals = styleTotals.computeIfAbsent(family, k -> new int[4]);
				totals[style(info)] += Math.max(1, score(info));
				best.merge(family, score(info), Math::max);
			}
		}

		int familyStyle(ItemInfo info)
		{
			if (section(info) == SECTION_ACCESSORIES)
			{
				return style(info);
			}
			int[] totals = styleTotals.get(family(info));
			if (totals == null)
			{
				return style(info);
			}
			// the style most of the set's strength is in; "other" only if nothing else
			int pick = STYLE_OTHER;
			for (int st = STYLE_MELEE; st <= STYLE_MAGIC; st++)
			{
				if (totals[st] > 0 && (pick == STYLE_OTHER || totals[st] > totals[pick]))
				{
					pick = st;
				}
			}
			return pick;
		}

		Comparator<BankItem> bySet()
		{
			return Comparator
				.comparingInt((BankItem b) -> familyStyle(b.getInfo()))
				.thenComparingInt(b -> section(b.getInfo()))
				.thenComparingInt(b -> section(b.getInfo()) == SECTION_ACCESSORIES
					? slotOrder(b.getInfo().getStats())
					: -best.getOrDefault(family(b.getInfo()), score(b.getInfo())))
				.thenComparing(b -> family(b.getInfo()))
				.thenComparingInt(b -> pieceOrder(b.getInfo().getStats()))
				.thenComparingInt(b -> -score(b.getInfo()));
		}
	}

	/**
	 * Which block of a category an item belongs to: its armour set or weapon kind for gear,
	 * its type and similar-name group for everything else. Items with the same key sit together.
	 */
	static String blockKey(BankItem b)
	{
		String category = b.getCategory() == null ? "" : b.getCategory().getId();
		ItemInfo info = b.getInfo();
		if (info.getRule() == Rule.GEAR)
		{
			return category + "|gear|" + section(info) + "|" + family(info);
		}
		return category + "|" + info.getRule() + "|" + info.getGroup() + "|" + info.getFamily();
	}

	static int section(ItemInfo info)
	{
		ItemEquipmentStats s = info.getStats();
		int slot = s == null ? -1 : s.getSlot();
		if (slot == EquipmentInventorySlot.WEAPON.getSlotIdx())
		{
			return SECTION_WEAPONS;
		}
		if (slot == EquipmentInventorySlot.AMULET.getSlotIdx() || slot == EquipmentInventorySlot.RING.getSlotIdx()
			|| slot == EquipmentInventorySlot.CAPE.getSlotIdx() || slot == EquipmentInventorySlot.AMMO.getSlotIdx())
		{
			return SECTION_ACCESSORIES;
		}
		return SECTION_ARMOUR;
	}

	/**
	 * Weapons: their kind ("scimitar", "godsword", "2h sword"). Armour: its set ("rune", "dharok's",
	 * "black d'hide"). Accessories: their slot.
	 */
	static String family(ItemInfo info)
	{
		String base = info.getBaseName();
		switch (section(info))
		{
			case SECTION_WEAPONS:
			{
				// "silver sickle (b)" is a sickle
				String w = base.replaceAll("\\(.*?\\)", " ").trim();
				int of = w.indexOf(" of ");
				if (of > 0)
				{
					w = w.substring(0, of);
				}
				if (w.endsWith("2h sword"))
				{
					return "2h sword";
				}
				int space = w.lastIndexOf(' ');
				return space < 0 ? w : w.substring(space + 1);
			}
			case SECTION_ACCESSORIES:
				return "slot " + slotOrder(info.getStats());
			default:
			{
				int hide = base.indexOf("d'hide");
				if (hide >= 0)
				{
					return base.substring(0, hide + 6);
				}
				String metal = ItemRules.metalOf(base);
				if (metal != null)
				{
					return metal;
				}
				int space = base.indexOf(' ');
				return space < 0 ? base : base.substring(0, space);
			}
		}
	}

	/**
	 * Armour pieces in the order a set is usually laid out.
	 */
	static int pieceOrder(ItemEquipmentStats s)
	{
		int slot = s == null ? -1 : s.getSlot();
		if (slot == EquipmentInventorySlot.HEAD.getSlotIdx())
		{
			return 0;
		}
		if (slot == EquipmentInventorySlot.BODY.getSlotIdx())
		{
			return 1;
		}
		if (slot == EquipmentInventorySlot.LEGS.getSlotIdx())
		{
			return 2;
		}
		if (slot == EquipmentInventorySlot.SHIELD.getSlotIdx())
		{
			return 3;
		}
		if (slot == EquipmentInventorySlot.GLOVES.getSlotIdx())
		{
			return 4;
		}
		if (slot == EquipmentInventorySlot.BOOTS.getSlotIdx())
		{
			return 5;
		}
		return 6;
	}

	static int style(ItemEquipmentStats s)
	{
		if (s == null)
		{
			return STYLE_OTHER;
		}
		int melee = Math.max(s.getAstab(), Math.max(s.getAslash(), s.getAcrush())) + s.getStr();
		int ranged = s.getArange() + s.getRstr();
		int magic = s.getAmagic() + Math.round(s.getMdmg() * 3);
		if (magic > 0 && magic >= melee && magic >= ranged)
		{
			return STYLE_MAGIC;
		}
		if (ranged > 0 && ranged >= melee)
		{
			return STYLE_RANGED;
		}
		if (melee > 0)
		{
			return STYLE_MELEE;
		}
		// pure defence: plate armour has negative magic and ranged attack
		if (s.getAmagic() < 0 || s.getArange() < 0)
		{
			return STYLE_MELEE;
		}
		return STYLE_OTHER;
	}

	static int slotOrder(ItemEquipmentStats s)
	{
		if (s == null)
		{
			return 11;
		}
		int slot = s.getSlot();
		if (slot == EquipmentInventorySlot.WEAPON.getSlotIdx())
		{
			return 0;
		}
		if (slot == EquipmentInventorySlot.SHIELD.getSlotIdx())
		{
			return 1;
		}
		if (slot == EquipmentInventorySlot.HEAD.getSlotIdx())
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
		if (slot == EquipmentInventorySlot.AMULET.getSlotIdx())
		{
			return 7;
		}
		if (slot == EquipmentInventorySlot.RING.getSlotIdx())
		{
			return 8;
		}
		if (slot == EquipmentInventorySlot.CAPE.getSlotIdx())
		{
			return 9;
		}
		if (slot == EquipmentInventorySlot.AMMO.getSlotIdx())
		{
			return 10;
		}
		return 11;
	}

	private static final java.util.regex.Pattern MAGIC_WEAPON = java.util.regex.Pattern.compile(
		"(staff|wand|sceptre|trident|sanguinesti|tumeken's shadow|kodai|nightmare staff|harmonised|volatile|eldritch)");
	private static final java.util.regex.Pattern RANGED_WEAPON = java.util.regex.Pattern.compile(
		"(bow|blowpipe|ballista|dart|knife|thrownaxe|javelin|chinchompa|atlatl)");

	/**
	 * Combat style of an item. Weapons are judged by what they are first, because stats can mislead:
	 * many staves have more crush and strength than magic attack, but they're still magic weapons.
	 */
	static int style(ItemInfo info)
	{
		ItemEquipmentStats s = info.getStats();
		if (s != null && s.getSlot() == EquipmentInventorySlot.WEAPON.getSlotIdx())
		{
			String n = info.getBaseName();
			if (MAGIC_WEAPON.matcher(n).find())
			{
				return STYLE_MAGIC;
			}
			if (RANGED_WEAPON.matcher(n).find())
			{
				return STYLE_RANGED;
			}
		}
		return style(s);
	}

	static int score(ItemInfo info)
	{
		return score(info.getStats(), style(info));
	}

	static int score(ItemEquipmentStats s)
	{
		return score(s, style(s));
	}

	private static int score(ItemEquipmentStats s, int style)
	{
		if (s == null)
		{
			return 0;
		}
		int defence = s.getDstab() + s.getDslash() + s.getDcrush() + s.getDmagic() + s.getDrange();
		int offence;
		switch (style)
		{
			case STYLE_MAGIC:
				offence = s.getAmagic() + Math.round(s.getMdmg() * 3);
				break;
			case STYLE_RANGED:
				offence = s.getArange() + s.getRstr();
				break;
			default:
				offence = Math.max(s.getAstab(), Math.max(s.getAslash(), s.getAcrush())) + s.getStr();
		}
		return offence * 5 + defence + s.getPrayer() * 3;
	}
}
