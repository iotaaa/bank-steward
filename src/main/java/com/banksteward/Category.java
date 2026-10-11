package com.banksteward;

import java.awt.Color;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A user-editable item category. Saved as JSON in the plugin config.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Category
{
	static final int NO_TAB = -1;
	static final int MAIN_TAB = 0;

	/**
	 * Stable id. Built-in categories use their rule name (e.g. "gear"), user ones start with "c".
	 */
	private String id;
	private String name;
	private int rgb;
	/**
	 * -1 for none (items stay where they are), 0 for the main tab, 1-9 for bank tabs.
	 */
	private int tab;
	/**
	 * Comma-separated name patterns, * is a wildcard. Checked before the built-in rules.
	 */
	private String patterns;

	Color getColor()
	{
		return new Color(rgb);
	}

	void setColor(Color color)
	{
		rgb = color.getRGB() & 0xFFFFFF;
	}

	static String tabLabel(int tab)
	{
		if (tab == NO_TAB)
		{
			return "None";
		}
		return tab == MAIN_TAB ? "Main" : "Tab " + tab;
	}
}
