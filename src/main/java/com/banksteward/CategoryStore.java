package com.banksteward;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Categories, per-item overrides, and working out which category an item is in.
 * Methods are synchronized because the panel edits from the Swing thread while the client thread reads.
 */
@Slf4j
@Singleton
class CategoryStore
{
	/**
	 * Override value for "leave this item where it is".
	 */
	static final String KEEP = "keep";

	/**
	 * The High alch category. It has no tab by default, so nothing moves until the player gives it one.
	 */
	static final String HIGH_ALCH = "highalch";
	private static final String HIGH_ALCH_ADDED_KEY = "highAlchAdded";

	private final ConfigManager configManager;
	private final Gson gson;

	private List<Category> categories = new ArrayList<>();
	private final Map<Integer, String> overrides = new HashMap<>();
	private final Map<String, List<Pattern>> compiledPatterns = new HashMap<>();
	private int version;

	@Inject
	CategoryStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	synchronized void load()
	{
		categories = new ArrayList<>();
		String json = configManager.getConfiguration(BankStewardConfig.GROUP, BankStewardConfig.CATEGORIES_KEY);
		if (json != null && !json.isEmpty())
		{
			try
			{
				List<Category> loaded = gson.fromJson(json, new TypeToken<List<Category>>()
				{
				}.getType());
				if (loaded != null)
				{
					categories.addAll(loaded);
				}
			}
			catch (JsonSyntaxException e)
			{
				log.warn("Couldn't read saved categories, using defaults", e);
			}
		}
		if (categories.isEmpty())
		{
			categories = defaults();
		}
		else if (configManager.getConfiguration(BankStewardConfig.GROUP, HIGH_ALCH_ADDED_KEY) == null
			&& categories.stream().noneMatch(c -> HIGH_ALCH.equals(c.getId())))
		{
			// saved before High alch existed: add it once, at the bottom (a player can still delete it)
			categories.add(highAlch());
		}
		configManager.setConfiguration(BankStewardConfig.GROUP, HIGH_ALCH_ADDED_KEY, true);

		overrides.clear();
		String saved = configManager.getConfiguration(BankStewardConfig.GROUP, BankStewardConfig.OVERRIDES_KEY);
		if (saved != null && !saved.isEmpty())
		{
			for (String pair : saved.split(","))
			{
				int colon = pair.indexOf(':');
				if (colon <= 0)
				{
					continue;
				}
				try
				{
					overrides.put(Integer.parseInt(pair.substring(0, colon)), pair.substring(colon + 1));
				}
				catch (NumberFormatException ignored)
				{
				}
			}
		}
		compilePatterns();
		version++;
	}

	private static List<Category> defaults()
	{
		List<Category> list = new ArrayList<>();
		for (Rule rule : Rule.values())
		{
			list.add(rule.toCategory());
		}
		list.add(highAlch());
		return list;
	}

	private static Category highAlch()
	{
		return new Category(HIGH_ALCH, "High alch", 0xFF8C42, Category.NO_TAB, "");
	}

	private void save()
	{
		configManager.setConfiguration(BankStewardConfig.GROUP, BankStewardConfig.CATEGORIES_KEY, gson.toJson(categories));
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<Integer, String> e : overrides.entrySet())
		{
			if (sb.length() > 0)
			{
				sb.append(',');
			}
			sb.append(e.getKey()).append(':').append(e.getValue());
		}
		configManager.setConfiguration(BankStewardConfig.GROUP, BankStewardConfig.OVERRIDES_KEY, sb.toString());
		compilePatterns();
		version++;
	}

	private void compilePatterns()
	{
		compiledPatterns.clear();
		for (Category c : categories)
		{
			if (c.getPatterns() == null || c.getPatterns().trim().isEmpty())
			{
				continue;
			}
			List<Pattern> list = new ArrayList<>();
			for (String p : c.getPatterns().split(","))
			{
				String t = p.trim().toLowerCase();
				if (t.isEmpty())
				{
					continue;
				}
				StringBuilder regex = new StringBuilder();
				for (String part : t.split("\\*", -1))
				{
					if (regex.length() > 0 || t.startsWith("*"))
					{
						regex.append(".*");
					}
					regex.append(Pattern.quote(part));
				}
				list.add(Pattern.compile(regex.toString()));
			}
			compiledPatterns.put(c.getId(), list);
		}
	}

	/**
	 * Bumped on every change so callers can tell when cached results are stale.
	 */
	synchronized int getVersion()
	{
		return version;
	}

	synchronized List<Category> getCategories()
	{
		List<Category> copy = new ArrayList<>();
		for (Category c : categories)
		{
			copy.add(new Category(c.getId(), c.getName(), c.getRgb(), c.getTab(), c.getPatterns()));
		}
		return copy;
	}

	synchronized Category get(String id)
	{
		for (Category c : categories)
		{
			if (c.getId().equals(id))
			{
				return c;
			}
		}
		return null;
	}

	/**
	 * Category index in the list, which is also the order categories appear in a shared tab.
	 */
	synchronized int indexOf(String id)
	{
		for (int i = 0; i < categories.size(); i++)
		{
			if (categories.get(i).getId().equals(id))
			{
				return i;
			}
		}
		return Integer.MAX_VALUE;
	}

	/**
	 * The category an item is in: a manual choice first, then name patterns, then the built-in rule.
	 * Returns null if the item fits no category.
	 */
	synchronized Category categorize(ItemInfo info)
	{
		String override = overrides.get(info.getId());
		if (KEEP.equals(override))
		{
			return null;
		}
		if (override != null)
		{
			Category c = get(override);
			if (c != null)
			{
				return c;
			}
		}

		String lower = info.getName().toLowerCase();
		for (Category c : categories)
		{
			List<Pattern> patterns = compiledPatterns.get(c.getId());
			if (patterns == null)
			{
				continue;
			}
			for (Pattern p : patterns)
			{
				if (p.matcher(lower).matches())
				{
					return c;
				}
			}
		}

		Category c = get(info.getRule().getId());
		return c != null ? c : get(Rule.MISC.getId());
	}

	synchronized boolean hasOverride(int itemId)
	{
		return overrides.containsKey(itemId);
	}

	synchronized void setOverride(int itemId, String categoryId)
	{
		if (categoryId == null)
		{
			overrides.remove(itemId);
		}
		else
		{
			overrides.put(itemId, categoryId);
		}
		save();
	}

	synchronized void setOverrides(Iterable<Integer> itemIds, String categoryId)
	{
		for (int id : itemIds)
		{
			if (categoryId == null)
			{
				overrides.remove(id);
			}
			else
			{
				overrides.put(id, categoryId);
			}
		}
		save();
	}

	synchronized void update(Category changed)
	{
		for (int i = 0; i < categories.size(); i++)
		{
			if (categories.get(i).getId().equals(changed.getId()))
			{
				categories.set(i, changed);
				save();
				return;
			}
		}
	}

	synchronized Category add(String name)
	{
		// spread new colours round the hue wheel
		float hue = (categories.size() * 0.13f) % 1f;
		Category c = new Category("c" + System.currentTimeMillis(), name,
			java.awt.Color.HSBtoRGB(hue, 0.6f, 0.95f) & 0xFFFFFF, Category.NO_TAB, "");
		categories.add(c);
		save();
		return c;
	}

	synchronized void remove(String id)
	{
		categories.removeIf(c -> c.getId().equals(id));
		overrides.values().removeIf(id::equals);
		save();
	}

	synchronized void move(String id, int delta)
	{
		int i = indexOf(id);
		int j = i + delta;
		if (i == Integer.MAX_VALUE || j < 0 || j >= categories.size())
		{
			return;
		}
		Collections.swap(categories, i, j);
		save();
	}

	synchronized void resetToDefaults()
	{
		categories = defaults();
		overrides.clear();
		save();
	}
}
