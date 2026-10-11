package com.banksteward;

import lombok.Value;
import net.runelite.client.game.ItemEquipmentStats;

/**
 * Everything the categoriser and sorter need about one item, worked out once on the client thread.
 */
@Value
class ItemInfo
{
	int id;
	String name;
	/**
	 * Lower-case name without a trailing "(4)", "(p++)" and so on, so doses and charges group together.
	 */
	String baseName;
	/**
	 * The number in a trailing "(n)" (dose, charges), or 0.
	 */
	int suffixNumber;
	Rule rule;
	/**
	 * Sort group inside the rule (e.g. runes before ammo).
	 */
	int group;
	/**
	 * Sort rank inside the group, lowest first.
	 */
	int rank;
	int storePrice;
	ItemEquipmentStats stats;
	/**
	 * Main word of the name ("cannonball", "hat") for categories without their own order, so items
	 * with similar names sit together. Empty when the category has its own order.
	 */
	String family;
}
