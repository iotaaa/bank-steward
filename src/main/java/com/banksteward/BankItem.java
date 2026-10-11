package com.banksteward;

import lombok.Value;

/**
 * One occupied bank slot.
 */
@Value
class BankItem
{
	int slot;
	/**
	 * The id in the container, which is the placeholder id for placeholders.
	 */
	int itemId;
	/**
	 * 0 for the main section, 1-9 for tabs.
	 */
	int tab;
	ItemInfo info;
	/**
	 * Null if the item fits no category.
	 */
	Category category;

	/**
	 * Where the item should end up: its category's tab, or where it is now if the category has none.
	 */
	int targetTab()
	{
		return category == null || category.getTab() == Category.NO_TAB ? tab : category.getTab();
	}
}
