package com.banksteward;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/**
 * Works out how far the bank is from its organised layout, and the single best next move.
 * Pure logic so it can be unit tested. It's re-run after every bank change, so a move the
 * player does differently just leads to a different next step.
 */
final class Planner
{
	enum StepType
	{
		/**
		 * Drag {@code item} onto the header of {@code tab} (0 is the All tab, numTabs+1 the new-tab slot).
		 */
		MOVE_TO_TAB,
		/**
		 * Drag {@code item} onto the item at {@code targetSlot} (insert mode).
		 */
		REORDER,
		/**
		 * Open {@code tab} (0 is the All tab).
		 */
		OPEN_TAB,
		DONE
	}

	@Value
	static class Step
	{
		StepType type;
		BankItem item;
		int tab;
		int targetSlot;
		boolean newTab;
	}

	@Value
	static class Plan
	{
		Step next;
		/**
		 * Items in the wrong tab plus items out of order inside their tab.
		 */
		int outOfPlace;
		/**
		 * Slots of items that belong in a different tab.
		 */
		Set<Integer> wrongTab;
		/**
		 * Slots of the tab icons left alone (the first item of each tab), when keeping them.
		 */
		Set<Integer> tabIcons;
		List<String> warnings;
	}

	private Planner()
	{
	}

	static Plan plan(List<BankItem> items, int[] tabSizes, int viewTab, Comparator<BankItem> order)
	{
		return plan(items, tabSizes, viewTab, order, false);
	}

	/**
	 * @param items        bank items in slot order
	 * @param tabSizes     item counts for tabs 1-9 (index 0 unused)
	 * @param viewTab      the tab being viewed, 0 for All
	 * @param keepTabIcons leave the first item of tabs 1-9 alone, since it's the tab's icon
	 */
	static Plan plan(List<BankItem> items, int[] tabSizes, int viewTab, Comparator<BankItem> order, boolean keepTabIcons)
	{
		// A tab's icon is its first item: with keepTabIcons it never moves and everything else sorts after it
		Set<Integer> icons = new HashSet<>();
		if (keepTabIcons)
		{
			int seenTab = 0;
			for (BankItem b : items)
			{
				if (b.getTab() > 0 && b.getTab() != seenTab)
				{
					icons.add(b.getSlot());
					seenTab = b.getTab();
				}
			}
		}

		int numTabs = 0;
		while (numTabs < 9 && tabSizes[numTabs + 1] > 0)
		{
			numTabs++;
		}

		List<String> warnings = new ArrayList<>();
		Set<Integer> wrongTab = new HashSet<>();
		List<BankItem> feasible = new ArrayList<>();
		Set<Integer> blockedTabs = new HashSet<>();
		for (BankItem b : items)
		{
			int target = b.targetTab();
			if (target == b.getTab() || icons.contains(b.getSlot()))
			{
				continue;
			}
			wrongTab.add(b.getSlot());
			if (target <= numTabs + 1)
			{
				feasible.add(b);
			}
			else
			{
				blockedTabs.add(target);
			}
		}
		for (int t : blockedTabs)
		{
			warnings.add("Tab " + t + " can't be made until tab " + (t - 1) + " exists. Give a category tab "
				+ (numTabs + 1) + ", or move the category to a lower tab.");
		}

		// Tab-by-tab: items that stay in their tab, and how many are out of order
		Set<Integer> leaving = new HashSet<>();
		for (BankItem b : feasible)
		{
			leaving.add(b.getSlot());
		}
		Map<Integer, List<BankItem>> staying = new HashMap<>();
		for (BankItem b : items)
		{
			if (!leaving.contains(b.getSlot()) && !icons.contains(b.getSlot()))
			{
				staying.computeIfAbsent(b.getTab(), k -> new ArrayList<>()).add(b);
			}
		}
		int outOfPlace = feasible.size();
		Map<Integer, int[]> lisByTab = new HashMap<>();
		for (Map.Entry<Integer, List<BankItem>> e : staying.entrySet())
		{
			int[] ranks = ranks(e.getValue(), order);
			int[] lis = lis(ranks);
			lisByTab.put(e.getKey(), lis);
			outOfPlace += ranks.length - lis.length;
		}

		Step next = nextCrossTabMove(feasible, tabSizes, numTabs, viewTab);
		if (next == null)
		{
			next = nextReorder(staying, lisByTab, viewTab, order);
		}
		return new Plan(next, outOfPlace, wrongTab, icons, warnings);
	}

	private static Step nextCrossTabMove(List<BankItem> feasible, int[] tabSizes, int numTabs, int viewTab)
	{
		if (feasible.isEmpty())
		{
			return null;
		}
		// Fill low tabs first so new tabs get made in order; main tab moves last
		List<BankItem> sorted = new ArrayList<>(feasible);
		sorted.sort(Comparator
			.comparingInt((BankItem b) -> b.targetTab() == 0 ? 10 : b.targetTab())
			.thenComparingInt(BankItem::getSlot));

		// Moving the last item out of a tab deletes it and renumbers the tabs after it, so avoid that while we can
		BankItem best = null;
		BankItem fallback = null;
		boolean anyHidden = false;
		for (BankItem b : sorted)
		{
			boolean visible = viewTab == 0 || b.getTab() == viewTab;
			if (!visible)
			{
				anyHidden = true;
				continue;
			}
			boolean emptiesTab = b.getTab() > 0 && tabSizes[b.getTab()] == 1;
			if (!emptiesTab)
			{
				best = b;
				break;
			}
			if (fallback == null)
			{
				fallback = b;
			}
		}
		if (best == null && anyHidden)
		{
			// the All tab shows everything, so moves without the tab problem may be there
			return new Step(StepType.OPEN_TAB, null, 0, -1, false);
		}
		if (best == null)
		{
			best = fallback;
		}
		int target = best.targetTab();
		return new Step(StepType.MOVE_TO_TAB, best, target, -1, target == numTabs + 1);
	}

	private static Step nextReorder(Map<Integer, List<BankItem>> staying,
		Map<Integer, int[]> lisByTab, int viewTab, Comparator<BankItem> order)
	{
		// the viewed tab first, then tabs 1-9, then the main section
		List<Integer> tabOrder = new ArrayList<>();
		tabOrder.add(viewTab);
		for (int t = 1; t <= 9; t++)
		{
			tabOrder.add(t);
		}
		tabOrder.add(0);

		for (int t : tabOrder)
		{
			List<BankItem> current = staying.get(t);
			if (current == null || lisByTab.get(t).length == current.size())
			{
				continue;
			}
			if (viewTab != t)
			{
				return new Step(StepType.OPEN_TAB, null, t, -1, false);
			}
			return reorderStep(current, lisByTab.get(t), order);
		}
		return new Step(StepType.DONE, null, -1, -1, false);
	}

	private static Step reorderStep(List<BankItem> current, int[] lis, Comparator<BankItem> order)
	{
		int[] ranks = ranks(current, order);
		Set<Integer> inLis = new HashSet<>();
		for (int i : lis)
		{
			inLis.add(i);
		}

		// The lowest-ranked item that's out of order goes straight after its sorted predecessor
		int move = -1;
		for (int i = 0; i < ranks.length; i++)
		{
			if (!inLis.contains(i) && (move < 0 || ranks[i] < ranks[move]))
			{
				move = i;
			}
		}
		int pred = -1;
		for (int i : lis)
		{
			if (ranks[i] < ranks[move] && (pred < 0 || ranks[i] > ranks[pred]))
			{
				pred = i;
			}
		}

		// In insert mode, dropping onto slot j leaves the dragged item at index j
		int j = pred < 0 ? 0 : (pred < move ? pred + 1 : pred);
		BankItem item = current.get(move);
		BankItem target = current.get(j);
		return new Step(StepType.REORDER, item, item.getTab(), target.getSlot(), false);
	}

	/**
	 * Each item's position in the sorted order, listed in current order.
	 */
	static int[] ranks(List<BankItem> current, Comparator<BankItem> order)
	{
		List<BankItem> sorted = new ArrayList<>(current);
		sorted.sort(order);
		Map<Integer, Integer> rankBySlot = new HashMap<>();
		for (int i = 0; i < sorted.size(); i++)
		{
			rankBySlot.put(sorted.get(i).getSlot(), i);
		}
		int[] ranks = new int[current.size()];
		for (int i = 0; i < ranks.length; i++)
		{
			ranks[i] = rankBySlot.get(current.get(i).getSlot());
		}
		return ranks;
	}

	/**
	 * Indices of a longest increasing subsequence, in order. These items can stay put.
	 */
	static int[] lis(int[] a)
	{
		int n = a.length;
		int[] tails = new int[n];
		int[] prev = new int[n];
		int len = 0;
		for (int i = 0; i < n; i++)
		{
			int lo = 0, hi = len;
			while (lo < hi)
			{
				int mid = (lo + hi) >>> 1;
				if (a[tails[mid]] < a[i])
				{
					lo = mid + 1;
				}
				else
				{
					hi = mid;
				}
			}
			prev[i] = lo > 0 ? tails[lo - 1] : -1;
			tails[lo] = i;
			if (lo == len)
			{
				len++;
			}
		}
		int[] out = new int[len];
		int k = len > 0 ? tails[len - 1] : -1;
		for (int i = len - 1; i >= 0; i--)
		{
			out[i] = k;
			k = prev[k];
		}
		return out;
	}

	static int[] tabSizes(int... sizes)
	{
		return Arrays.copyOf(sizes, 10);
	}
}
