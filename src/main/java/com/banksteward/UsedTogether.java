package com.banksteward;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Items that are used with each other: a partner (a tool, a secondary) is placed right next to what it's
 * used on, but only when both end up in the same tab. Nothing changes tab to sit by its partner.
 */
final class UsedTogether
{
	private static final class Pair
	{
		final Predicate<ItemInfo> partner;
		final Predicate<ItemInfo> target;
		final boolean before;

		Pair(Predicate<ItemInfo> partner, Predicate<ItemInfo> target, boolean before)
		{
			this.partner = partner;
			this.target = target;
			this.before = before;
		}
	}

	private static final List<Pair> PAIRS = new ArrayList<>();

	static
	{
		// Herblore: each unfinished potion followed by the secondary that finishes it
		String[][] potions = {
			{"guam potion", "eye of newt"}, {"marrentill potion", "unicorn horn dust"},
			{"tarromin potion", "limpwurt root"}, {"harralander potion", "red spiders' eggs"},
			{"ranarr potion", "snape grass"}, {"toadflax potion", "crushed nest"},
			{"avantoe potion", "mort myre fungus"}, {"cadantine potion", "white berries"},
			{"lantadyme potion", "dragon scale dust"}, {"dwarf weed potion", "wine of zamorak"},
			{"torstol potion", "jangerberries"}, {"irit potion", "eye of newt"},
			{"kwuarm potion", "limpwurt root"}, {"snapdragon potion", "red spiders' eggs"}};
		for (String[] p : potions)
		{
			after(base(p[1]), unf(p[0]));
		}
		before(base("vial of water"), i -> i.getName().toLowerCase().endsWith("(unf)"));

		// Tools and materials just before what they're used on
		before(base("chisel"), i -> i.getBaseName().startsWith("uncut "));
		before(base("needle"), i -> i.getBaseName().contains("leather"));
		before(base("thread"), i -> i.getBaseName().contains("leather"));
		before(base("glassblowing pipe"), base("molten glass"));
		before(base("soda ash"), base("molten glass"));
		before(base("seaweed"), base("molten glass"));
		before(i -> i.getBaseName().endsWith(" mould") && !i.getBaseName().startsWith("bolt")
			&& !i.getBaseName().startsWith("ammo"), base("gold bar"));
		before(base("knife"), i -> i.getBaseName().endsWith("logs"));
		before(base("tinderbox"), i -> i.getBaseName().endsWith("logs"));
		before(i -> i.getBaseName().equals("bow string") || i.getBaseName().equals("crossbow string"),
			i -> i.getName().toLowerCase().endsWith("(u)"));
		before(base("hammer"), i -> i.getBaseName().endsWith(" bar"));
		before(base("pestle and mortar"), i -> i.getBaseName().equals("unicorn horn") || i.getBaseName().equals("bird nest"));
	}

	private static Predicate<ItemInfo> base(String name)
	{
		return i -> i.getBaseName().equals(name);
	}

	private static Predicate<ItemInfo> unf(String base)
	{
		return i -> i.getBaseName().equals(base) && i.getName().toLowerCase().endsWith("(unf)");
	}

	private static void before(Predicate<ItemInfo> partner, Predicate<ItemInfo> target)
	{
		PAIRS.add(new Pair(partner, target, true));
	}

	private static void after(Predicate<ItemInfo> partner, Predicate<ItemInfo> target)
	{
		PAIRS.add(new Pair(partner, target, false));
	}

	private final Map<BankItem, BankItem> anchorOf = new HashMap<>();
	private final Map<BankItem, Integer> offset = new HashMap<>();

	/**
	 * @param order the normal order, used to find the first of a partner's targets
	 */
	UsedTogether(Collection<BankItem> bank, Comparator<BankItem> order)
	{
		for (int r = 0; r < PAIRS.size(); r++)
		{
			Pair pair = PAIRS.get(r);
			// filter once per rule: only a handful of items match either side
			List<BankItem> partners = new ArrayList<>();
			List<BankItem> targets = new ArrayList<>();
			for (BankItem b : bank)
			{
				if (pair.partner.test(b.getInfo()))
				{
					partners.add(b);
				}
				if (pair.target.test(b.getInfo()))
				{
					targets.add(b);
				}
			}
			for (BankItem p : partners)
			{
				if (anchorOf.containsKey(p))
				{
					continue;
				}
				BankItem anchor = null;
				for (BankItem t : targets)
				{
					// only within the same tab, and never onto an item that has itself moved next to something
					if (t != p && t.targetTab() == p.targetTab() && !anchorOf.containsKey(t)
						&& (anchor == null || order.compare(t, anchor) < 0))
					{
						anchor = t;
					}
				}
				if (anchor != null)
				{
					anchorOf.put(p, anchor);
					// partners placed before keep their rule order; ones placed after follow it
					offset.put(p, pair.before ? -(PAIRS.size() - r) : r + 1);
				}
			}
		}
	}

	/**
	 * The item this one sorts alongside (itself if it has no partner in its tab).
	 */
	BankItem anchor(BankItem b)
	{
		return anchorOf.getOrDefault(b, b);
	}

	/**
	 * Where it sits around its anchor: negative before, positive after, 0 for the anchor itself.
	 */
	int offset(BankItem b)
	{
		return offset.getOrDefault(b, 0);
	}
}
