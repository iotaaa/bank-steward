package com.banksteward;

import java.awt.Color;

/**
 * Softens overlay colours so markers and highlights don't glare over the bank.
 */
final class Fade
{
	private static final float AMOUNT = 0.7f;

	private Fade()
	{
	}

	/**
	 * A category colour at the player's chosen opacity (percent).
	 */
	static Color category(Color c, int percent)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(255 * percent / 100f));
	}

	static Color of(Color c)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(c.getAlpha() * AMOUNT));
	}
}
