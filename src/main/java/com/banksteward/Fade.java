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

	static Color of(Color c)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(c.getAlpha() * AMOUNT));
	}
}
