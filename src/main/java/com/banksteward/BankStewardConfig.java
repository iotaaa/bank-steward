package com.banksteward;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(BankStewardConfig.GROUP)
public interface BankStewardConfig extends Config
{
	String GROUP = "banksteward";
	String CATEGORIES_KEY = "categories";
	String OVERRIDES_KEY = "overrides";

	enum MarkerStyle
	{
		CORNER,
		BAR,
		OUTLINE,
		NONE;

		@Override
		public String toString()
		{
			switch (this)
			{
				case CORNER:
					return "Corner";
				case BAR:
					return "Bar";
				case OUTLINE:
					return "Outline";
				default:
					return "None";
			}
		}
	}

	enum GearSort
	{
		STYLE,
		SLOT;

		@Override
		public String toString()
		{
			return this == STYLE ? "Sets and weapon kinds" : "All of one slot together";
		}
	}

	@ConfigSection(
		name = "Bank display",
		description = "How categories show in the bank",
		position = 0
	)
	String displaySection = "display";

	@ConfigSection(
		name = "Guide",
		description = "The step-by-step organise guide",
		position = 1
	)
	String guideSection = "guide";

	@ConfigSection(
		name = "Sorting",
		description = "How items are ordered inside a category",
		position = 2
	)
	String sortingSection = "sorting";

	@ConfigItem(
		keyName = "showCategoryMarkers",
		name = "Show category markers",
		description = "Mark each bank item with its category's colour",
		section = displaySection,
		position = -1
	)
	default boolean showCategoryMarkers()
	{
		return false;
	}

	@ConfigItem(
		keyName = "markerStyle",
		name = "Category marker",
		description = "How each item's category colour is drawn in the bank, when category markers are on",
		section = displaySection,
		position = 0
	)
	default MarkerStyle markerStyle()
	{
		return MarkerStyle.CORNER;
	}

	@Range(min = 10, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "categoryOpacity",
		name = "Category colour opacity",
		description = "How strong category colours are, on the item markers and the bars under the tab headers",
		section = displaySection,
		position = 1
	)
	default int categoryOpacity()
	{
		return 60;
	}

	@ConfigItem(
		keyName = "markMisplaced",
		name = "Mark items in the wrong tab",
		description = "Draw a dashed red box around items that belong in a different tab",
		section = displaySection,
		position = 2
	)
	default boolean markMisplaced()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightTabIcons",
		name = "Highlight tab icons",
		description = "Mark the first item of each tab in gold when it's kept as the tab's icon (see Keep tab icons)",
		section = displaySection,
		position = 3
	)
	default boolean highlightTabIcons()
	{
		return true;
	}

	@ConfigItem(
		keyName = "tabColours",
		name = "Colour tab headers",
		description = "Draw each category's colour under the bank tab it's assigned to",
		section = displaySection,
		position = 3
	)
	default boolean tabColours()
	{
		return true;
	}

	@ConfigItem(
		keyName = "categoryMenu",
		name = "Right-click category menu",
		description = "Add a Steward option to bank items and tabs for changing categories",
		section = displaySection,
		position = 4
	)
	default boolean categoryMenu()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "moveColour",
		name = "Item to move",
		description = "Highlight colour for the item to move",
		section = guideSection,
		position = 0
	)
	default Color moveColour()
	{
		return new Color(80, 230, 100, 230);
	}

	@Alpha
	@ConfigItem(
		keyName = "targetColour",
		name = "Drop target",
		description = "Highlight colour for where to drop it",
		section = guideSection,
		position = 1
	)
	default Color targetColour()
	{
		return new Color(255, 210, 60, 230);
	}

	@ConfigItem(
		keyName = "keepTabIcons",
		name = "Keep tab icons",
		description = "Leave the first item in each bank tab where it is, since it's the tab's icon. "
			+ "Everything else is organised after it.",
		section = guideSection,
		position = 3
	)
	default boolean keepTabIcons()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showArrow",
		name = "Show arrow",
		description = "Draw an arrow from the item to where it goes",
		section = guideSection,
		position = 2
	)
	default boolean showArrow()
	{
		return true;
	}

	@ConfigSection(
		name = "Alch Ledger",
		description = "Working with the Alch Ledger plugin, when it's installed and on",
		position = 3
	)
	String alchLedgerSection = "alchLedger";

	@ConfigItem(
		keyName = "alchLedgerProfits",
		name = "File profitable alchs",
		description = "When Alch Ledger is on, put items it shows as profitable (at or above its Low tier) in the "
			+ "High alch category, best profit first. Items you've filed by hand stay where you put them.",
		section = alchLedgerSection,
		position = 0
	)
	default boolean alchLedgerProfits()
	{
		return false;
	}

	@ConfigItem(
		keyName = "gearSort",
		name = "Gear order",
		description = "How gear is ordered inside its category",
		section = sortingSection,
		position = 0
	)
	default GearSort gearSort()
	{
		return GearSort.STYLE;
	}
}
