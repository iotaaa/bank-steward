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
		Comparator<BankItem> gearOrder = gearSort == BankStewardConfig.GearSort.STYLE
			? gear.bySet()
			: Comparator.comparingInt((BankItem b) -> slotOrder(b.getInfo().getStats()))
				.thenComparingInt(b -> style(b.getInfo().getStats()))
				.thenComparingInt(b -> -score(b.getInfo().getStats()));

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
			.thenComparing(b -> b.getInfo().getFamily())
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
		return Comparator
			.comparing((BankItem b) -> byName.getOrDefault(b.getInfo().getName().toLowerCase(), b), order)
			.thenComparingInt(b -> b.getInfo().getId())
			.thenComparingInt(BankItem::getItemId);
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
				ItemEquipmentStats s = b.getInfo().getStats();
				String family = family(b.getInfo());
				int[] totals = styleTotals.computeIfAbsent(family, k -> new int[4]);
				totals[style(s)] += Math.max(1, score(s));
				best.merge(family, score(s), Math::max);
			}
		}

		int familyStyle(ItemInfo info)
		{
			if (section(info) == SECTION_ACCESSORIES)
			{
				return style(info.getStats());
			}
			int[] totals = styleTotals.get(family(info));
			if (totals == null)
			{
				return style(info.getStats());
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
					: -best.getOrDefault(family(b.getInfo()), score(b.getInfo().getStats())))
				.thenComparing(b -> family(b.getInfo()))
				.thenComparingInt(b -> pieceOrder(b.getInfo().getStats()))
				.thenComparingInt(b -> -score(b.getInfo().getStats()));
		}
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
				String w = base;
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

	static int score(ItemEquipmentStats s)
	{
		if (s == null)
		{
			return 0;
		}
		int defence = s.getDstab() + s.getDslash() + s.getDcrush() + s.getDmagic() + s.getDrange();
		int offence;
		switch (style(s))
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
