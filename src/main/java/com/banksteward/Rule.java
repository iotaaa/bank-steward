package com.banksteward;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Built-in categories. The id doubles as the default category id, so deleting a
 * default category just sends its items to Misc (or leaves them unassigned).
 */
@Getter
@RequiredArgsConstructor
enum Rule
{
	MONEY("money", "Money & clues", 0xF2C14E, Category.MAIN_TAB),
	GEAR("gear", "Gear", 0xE05555, 1),
	TELEPORTS("teleports", "Teleports", 0x4FA3F7, 2),
	RUNES("runes", "Runes & ammo", 0xB57BFF, 3),
	POTIONS("potions", "Potions", 0x3FD1C4, 4),
	FOOD("food", "Food", 0xF08A3C, 5),
	SKILLING("skilling", "Skilling", 0x7BC96F, 6),
	RESOURCES("resources", "Resources", 0xA67C52, 7),
	COSMETIC("cosmetic", "Cosmetic", 0xF27BC1, 8),
	MISC("misc", "Quest & misc", 0x9AA0A6, 9);

	private final String id;
	private final String displayName;
	private final int rgb;
	private final int defaultTab;

	Category toCategory()
	{
		return new Category(id, displayName, rgb, defaultTab, "");
	}
}
