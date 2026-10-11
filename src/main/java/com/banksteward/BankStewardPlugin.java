package com.banksteward;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Bank Steward",
	description = "Sorts your bank into categories, shows the finished layout instantly and guides you through every move",
	tags = {"bank", "organise", "organize", "sort", "tidy", "tabs", "layout", "category"}
)
@PluginDependency(BankTagsPlugin.class)
public class BankStewardPlugin extends Plugin
{
	static final String PREVIEW_TAG = "banksteward";

	// Messages shared with the Alch Ledger plugin (RuneLite's PluginMessage events)
	private static final String NAMESPACE = "banksteward";
	private static final String ALCH_LEDGER_NAMESPACE = "alchledger";
	private static final String ALCH_LEDGER_CLASS = "com.alchledger.AlchLedgerPlugin";
	private static final int BANK_WIDTH = 8;
	private static final int[] TAB_VARBITS = {
		VarbitID.BANK_TAB_1, VarbitID.BANK_TAB_2, VarbitID.BANK_TAB_3, VarbitID.BANK_TAB_4, VarbitID.BANK_TAB_5,
		VarbitID.BANK_TAB_6, VarbitID.BANK_TAB_7, VarbitID.BANK_TAB_8, VarbitID.BANK_TAB_9};

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private BankStewardConfig config;

	@Inject
	private CategoryStore store;

	@Inject
	private MarkerOverlay markerOverlay;

	@Inject
	private GuideOverlay guideOverlay;

	@Inject
	private TagManager tagManager;

	@Inject
	private LayoutManager layoutManager;

	@Inject
	private BankTagsService bankTagsService;

	@Inject
	private EventBus eventBus;

	@Inject
	private PluginManager pluginManager;

	@Getter
	private volatile boolean alchLedgerActive;
	private Map<Integer, Integer> alchProfits = Collections.emptyMap();

	private BankStewardPanel panel;
	private NavigationButton navButton;

	private final Map<Integer, ItemInfo> infoCache = new HashMap<>();
	private volatile Set<Integer> previewItems = Collections.emptySet();
	private boolean dirty = true;
	private boolean wasBankOpen;

	// Read by the overlays, written on the client thread
	@Getter
	private List<BankItem> bankItems = Collections.emptyList();
	private Map<Integer, BankItem> itemsById = Collections.emptyMap();
	@Getter
	private Planner.Plan plan;
	@Getter
	private boolean guideActive;
	@Getter
	private int numTabs;
	private int startOutOfPlace;
	private String lastLoggedView;

	@Provides
	BankStewardConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BankStewardConfig.class);
	}

	@Override
	protected void startUp()
	{
		store.load();
		tagManager.registerTag(PREVIEW_TAG, id -> previewItems.contains(id));
		overlayManager.add(markerOverlay);
		overlayManager.add(guideOverlay);

		panel = injector.getInstance(BankStewardPanel.class);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "nav_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Bank Steward")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		updateAlchLedgerActive();
		dirty = true;
		clientThread.invokeLater(this::rebuild);
	}

	@Override
	protected void shutDown()
	{
		clientThread.invokeLater(() ->
		{
			if (PREVIEW_TAG.equals(bankTagsService.getActiveTag()))
			{
				bankTagsService.closeBankTag();
			}
			layoutManager.removeLayout(PREVIEW_TAG);
		});
		tagManager.unregisterTag(PREVIEW_TAG);
		overlayManager.remove(markerOverlay);
		overlayManager.remove(guideOverlay);
		clientToolbar.removeNavigation(navButton);
		setGuideActive(false);
		alchProfits = Collections.emptyMap();
		plan = null;
		bankItems = Collections.emptyList();
		itemsById = Collections.emptyMap();
		infoCache.clear();
		panel = null;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.BANK)
		{
			dirty = true;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int id = event.getVarbitId();
		if (id == VarbitID.BANK_CURRENTTAB || id == VarbitID.BANK_INSERTMODE
			|| id >= VarbitID.BANK_TAB_1 && id <= VarbitID.BANK_TAB_9)
		{
			dirty = true;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (BankStewardConfig.GROUP.equals(event.getGroup())
			|| ALCH_LEDGER_NAMESPACE.equals(event.getGroup()) && config.alchLedgerProfits())
		{
			markDirty();
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		boolean open = isBankOpen();
		if (open != wasBankOpen)
		{
			wasBankOpen = open;
			dirty = true;
			if (open && log.isDebugEnabled())
			{
				logTabBar();
			}
		}
		if (dirty)
		{
			rebuild();
		}
	}

	/**
	 * Call from any thread after categories or settings change.
	 */
	void markDirty()
	{
		clientThread.invokeLater(() ->
		{
			dirty = true;
			rebuild();
		});
	}

	boolean isBankOpen()
	{
		Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
		return items != null && !items.isHidden();
	}

	boolean isTagTabOpen()
	{
		return bankTagsService.getActiveTag() != null;
	}

	boolean isPreviewOpen()
	{
		return PREVIEW_TAG.equals(bankTagsService.getActiveTag());
	}

	boolean isInsertMode()
	{
		return client.getVarbitValue(VarbitID.BANK_INSERTMODE) == 1;
	}

	int getViewTab()
	{
		return client.getVarbitValue(VarbitID.BANK_CURRENTTAB);
	}

	private void rebuild()
	{
		dirty = false;
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (bank == null)
		{
			bankItems = Collections.emptyList();
			plan = null;
			pushToPanel();
			return;
		}

		int[] tabSizes = new int[10];
		for (int i = 0; i < 9; i++)
		{
			tabSizes[i + 1] = client.getVarbitValue(TAB_VARBITS[i]);
		}
		int tabs = 0;
		while (tabs < 9 && tabSizes[tabs + 1] > 0)
		{
			tabs++;
		}
		numTabs = tabs;

		List<BankItem> list = new ArrayList<>();
		Item[] items = bank.getItems();
		int tab = 1;
		int tabEnd = tabSizes[1];
		for (int slot = 0; slot < items.length; slot++)
		{
			int id = items[slot].getId();
			if (id < 0)
			{
				break;
			}
			while (tab <= 9 && slot >= tabEnd)
			{
				tab++;
				tabEnd += tab <= 9 ? tabSizes[tab] : 0;
			}
			int itemTab = tab <= 9 ? tab : 0;
			ItemInfo info = info(itemManager.canonicalize(id));
			list.add(new BankItem(slot, id, itemTab, info, store.categorize(info)));
		}
		list = fileProfitableAlchs(list);
		list = sameNameSameCategory(list);
		Map<Integer, BankItem> byId = new HashMap<>();
		for (BankItem b : list)
		{
			byId.put(b.getItemId(), b);
		}
		bankItems = list;
		itemsById = byId;

		plan = Planner.plan(list, tabSizes, getViewTab(), comparator(), config.keepTabIcons());
		logTabOrder(list, getViewTab());
		if (guideActive)
		{
			if (plan.getNext().getType() == Planner.StepType.DONE)
			{
				setGuideActive(false);
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					ColorUtil.wrapWithColorTag("Bank Steward: your bank is in order.", java.awt.Color.GREEN.darker()), null);
			}
			startOutOfPlace = Math.max(startOutOfPlace, plan.getOutOfPlace());
		}
		pushToPanel();
	}

	// ---- Debug logging, to find out why something sorted where it did ----

	/**
	 * Logs the viewed tab's current and wanted order once per bank open or tab change.
	 */
	private void logTabOrder(List<BankItem> items, int viewTab)
	{
		if (!log.isDebugEnabled() || !isBankOpen())
		{
			lastLoggedView = null;
			return;
		}
		String view = viewTab + "/" + items.size();
		if (view.equals(lastLoggedView))
		{
			return;
		}
		lastLoggedView = view;
		List<BankItem> tab = new ArrayList<>();
		for (BankItem b : items)
		{
			if (b.getTab() == viewTab)
			{
				tab.add(b);
			}
		}
		logOrder("Tab " + viewTab + " current order", tab);
		List<BankItem> wanted = new ArrayList<>(tab);
		wanted.sort(comparator());
		logOrder("Tab " + viewTab + " wanted order (before keeping the icon)", wanted);
	}

	private void logOrder(String title, List<BankItem> items)
	{
		if (!log.isDebugEnabled())
		{
			return;
		}
		StringBuilder sb = new StringBuilder(title).append(':');
		for (BankItem b : items)
		{
			ItemInfo i = b.getInfo();
			sb.append("\n  ").append(i.getName())
				.append(" [id ").append(i.getId())
				.append(", ").append(b.getCategory() == null ? "no category" : b.getCategory().getName())
				.append(", ").append(i.getRule())
				.append(i.getRule() == Rule.GEAR
					? ", " + ItemSorter.blockKey(b) + ", style " + ItemSorter.style(i)
					: ", group " + i.getGroup() + ", family " + i.getFamily() + ", rank " + i.getRank())
				.append(']');
		}
		log.debug(sb.toString());
	}

	// ---- Alch Ledger ----

	/**
	 * Starts or stops the guide, and tells Alch Ledger so it can hide its bank outlines while the guide runs.
	 */
	private void setGuideActive(boolean active)
	{
		if (guideActive == active)
		{
			return;
		}
		guideActive = active;
		sendGuideState();
	}

	private void sendGuideState()
	{
		Map<String, Object> data = new HashMap<>();
		data.put("active", guideActive);
		eventBus.post(new PluginMessage(NAMESPACE, "guide", data));
	}

	private void updateAlchLedgerActive()
	{
		boolean active = false;
		for (Plugin p : pluginManager.getPlugins())
		{
			if (ALCH_LEDGER_CLASS.equals(p.getClass().getName()) && pluginManager.isPluginActive(p))
			{
				active = true;
				break;
			}
		}
		if (active != alchLedgerActive)
		{
			alchLedgerActive = active;
			markDirty();
		}
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		updateAlchLedgerActive();
	}

	@Subscribe
	public void onPluginMessage(PluginMessage message)
	{
		if (!ALCH_LEDGER_NAMESPACE.equals(message.getNamespace()))
		{
			return;
		}
		if ("hello".equals(message.getName()))
		{
			// Alch Ledger just started: tell it whether the guide is running
			sendGuideState();
		}
		else if ("profits".equals(message.getName()))
		{
			Object raw = message.getData().get("profits");
			Map<Integer, Integer> profits = new HashMap<>();
			if (raw instanceof Map)
			{
				for (Map.Entry<?, ?> e : ((Map<?, ?>) raw).entrySet())
				{
					if (e.getKey() instanceof Integer && e.getValue() instanceof Integer)
					{
						profits.put((Integer) e.getKey(), (Integer) e.getValue());
					}
				}
			}
			alchProfits = profits;
		}
	}

	/**
	 * With the setting on and Alch Ledger running, items it shows as profitable go in the High alch category,
	 * unless the player has filed them by hand. Alch Ledger answers the request straight away, on this thread.
	 */
	private List<BankItem> fileProfitableAlchs(List<BankItem> items)
	{
		alchProfits = Collections.emptyMap();
		Category highAlch = store.get(CategoryStore.HIGH_ALCH);
		if (!config.alchLedgerProfits() || !alchLedgerActive || highAlch == null || items.isEmpty())
		{
			return items;
		}

		List<Integer> ids = new ArrayList<>();
		for (BankItem b : items)
		{
			ids.add(b.getInfo().getId());
		}
		Map<String, Object> data = new HashMap<>();
		data.put("items", ids);
		eventBus.post(new PluginMessage(NAMESPACE, "profitRequest", data));

		Map<Integer, Integer> profits = alchProfits;
		if (profits.isEmpty())
		{
			return items;
		}
		List<BankItem> out = new ArrayList<>(items.size());
		for (BankItem b : items)
		{
			int id = b.getInfo().getId();
			boolean file = profits.containsKey(id) && !store.hasOverride(id);
			out.add(file ? new BankItem(b.getSlot(), b.getItemId(), b.getTab(), b.getInfo(), highAlch) : b);
		}
		return out;
	}

	/**
	 * Items with exactly the same name share one category: a hand-picked one if any of them has it,
	 * otherwise the one with the lowest id.
	 */
	private List<BankItem> sameNameSameCategory(List<BankItem> items)
	{
		Map<String, BankItem> chosen = new HashMap<>();
		for (BankItem b : items)
		{
			String name = b.getInfo().getName().toLowerCase();
			BankItem current = chosen.get(name);
			boolean override = store.hasOverride(b.getInfo().getId());
			if (current == null
				|| override && !store.hasOverride(current.getInfo().getId())
				|| override == store.hasOverride(current.getInfo().getId()) && b.getInfo().getId() < current.getInfo().getId())
			{
				chosen.put(name, b);
			}
		}
		List<BankItem> out = new ArrayList<>(items.size());
		for (BankItem b : items)
		{
			Category c = chosen.get(b.getInfo().getName().toLowerCase()).getCategory();
			out.add(c == b.getCategory() ? b : new BankItem(b.getSlot(), b.getItemId(), b.getTab(), b.getInfo(), c));
		}
		return out;
	}

	private Comparator<BankItem> comparator()
	{
		Map<String, Integer> order = new HashMap<>();
		List<Category> categories = store.getCategories();
		for (int i = 0; i < categories.size(); i++)
		{
			order.put(categories.get(i).getId(), i);
		}
		return ItemSorter.comparator(order, config.gearSort(), bankItems, alchProfits);
	}

	private ItemInfo info(int id)
	{
		ItemInfo cached = infoCache.get(id);
		if (cached != null)
		{
			return cached;
		}
		ItemComposition comp = itemManager.getItemComposition(id);
		ItemStats stats = itemManager.getItemStats(id);
		boolean equipable = stats != null && stats.isEquipable();
		ItemInfo info = ItemRules.classify(id, comp.getName(), equipable,
			stats != null ? stats.getEquipment() : null, comp.getInventoryActions(), comp.getPrice());
		infoCache.put(id, info);
		return info;
	}

	private void pushToPanel()
	{
		if (panel == null)
		{
			return;
		}
		BankStewardPanel.Status status = new BankStewardPanel.Status();
		status.bankKnown = client.getItemContainer(InventoryID.BANK) != null;
		status.bankOpen = isBankOpen();
		status.guideActive = guideActive;
		status.previewOpen = isPreviewOpen();
		status.tagTabOpen = isTagTabOpen() && !status.previewOpen;
		status.insertMode = isInsertMode();
		status.total = bankItems.size();
		status.outOfPlace = plan != null ? plan.getOutOfPlace() : 0;
		status.startOutOfPlace = startOutOfPlace;
		status.instruction = plan != null ? describe(plan.getNext()) : null;
		status.reorderStep = plan != null && plan.getNext().getType() == Planner.StepType.REORDER;
		status.warnings = plan != null ? plan.getWarnings() : Collections.emptyList();
		Map<String, Integer> counts = new HashMap<>();
		int unassigned = 0;
		for (BankItem b : bankItems)
		{
			if (b.getCategory() == null)
			{
				unassigned++;
			}
			else
			{
				counts.merge(b.getCategory().getId(), 1, Integer::sum);
			}
		}
		status.counts = counts;
		status.unassigned = unassigned;
		BankStewardPanel p = panel;
		SwingUtilities.invokeLater(() -> p.update(status));
	}

	String describe(Planner.Step step)
	{
		switch (step.getType())
		{
			case MOVE_TO_TAB:
				String name = step.getItem().getInfo().getName();
				if (step.getTab() == 0)
				{
					return "Drag " + name + " onto the All tab (the first tab) to take it out of its tab.";
				}
				if (step.isNewTab())
				{
					return "Drag " + name + " onto the empty tab slot (+) to make tab " + step.getTab() + ".";
				}
				return "Drag " + name + " onto tab " + step.getTab() + ".";
			case REORDER:
				BankItem target = itemAtSlot(step.getTargetSlot());
				return "Drag " + step.getItem().getInfo().getName() + " onto "
					+ (target != null ? target.getInfo().getName() : "the highlighted slot") + ".";
			case OPEN_TAB:
				if (plan != null && !plan.getWrongTab().isEmpty())
				{
					return "Open the All tab so you can move items between tabs.";
				}
				return step.getTab() == 0
					? "Open the All tab and scroll to the untabbed items at the bottom."
					: "Open tab " + step.getTab() + " to put it in order.";
			default:
				return "Your bank is in order.";
		}
	}

	BankItem itemById(int itemId)
	{
		return itemsById.get(itemId);
	}

	BankItem itemAtSlot(int slot)
	{
		List<BankItem> items = bankItems;
		return slot >= 0 && slot < items.size() ? items.get(slot) : null;
	}

	// ---- Panel actions (Swing thread) ----

	void toggleGuide()
	{
		clientThread.invokeLater(() ->
		{
			setGuideActive(!guideActive);
			startOutOfPlace = plan != null ? plan.getOutOfPlace() : 0;
			if (guideActive && isPreviewOpen())
			{
				bankTagsService.closeBankTag();
			}
			rebuild();
		});
	}

	void togglePreview()
	{
		clientThread.invokeLater(() ->
		{
			if (isPreviewOpen())
			{
				bankTagsService.closeBankTag();
			}
			else if (isBankOpen())
			{
				openPreview();
			}
			pushToPanel();
		});
	}

	/**
	 * Shows the whole bank already organised, as a Bank Tags layout: one block per category, each starting on a new row.
	 */
	private void openPreview()
	{
		List<BankItem> sorted = new ArrayList<>(bankItems);
		sorted.sort(comparator());
		logOrder("Preview order", sorted);
		List<Integer> layout = new ArrayList<>();
		Set<Integer> ids = new HashSet<>();
		String lastCategory = null;
		for (BankItem b : sorted)
		{
			String cat = b.getCategory() == null ? "" : b.getCategory().getId();
			if (lastCategory != null && !cat.equals(lastCategory))
			{
				while (layout.size() % BANK_WIDTH != 0)
				{
					layout.add(-1);
				}
			}
			lastCategory = cat;
			layout.add(b.getInfo().getId());
			ids.add(b.getInfo().getId());
			ids.add(b.getItemId());
		}
		previewItems = ids;
		int[] arr = layout.stream().mapToInt(Integer::intValue).toArray();
		layoutManager.saveLayout(new Layout(PREVIEW_TAG, arr));
		setGuideActive(false);
		bankTagsService.openBankTag(PREVIEW_TAG, 0);
	}

	// ---- Right-click menus ----

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.categoryMenu())
		{
			return;
		}
		String option = Text.removeTags(event.getOption());
		if (event.getActionParam1() == InterfaceID.Bankmain.ITEMS && "Examine".equals(option))
		{
			addItemMenu(event);
		}
		else if (event.getActionParam1() == InterfaceID.Bankmain.TABS
			&& (option.startsWith("View tab") || option.equals("View all items")))
		{
			int tab = tabForHeader(event.getActionParam0());
			if (tab >= 0)
			{
				addTabMenu(event, tab);
			}
		}
	}

	private void addItemMenu(MenuEntryAdded event)
	{
		int rawId = event.getItemId();
		if (rawId <= 0)
		{
			return;
		}
		int id = itemManager.canonicalize(rawId);
		Category current = store.categorize(info(id));
		boolean overridden = store.hasOverride(id);

		MenuEntry parent = client.getMenu().createMenuEntry(-1)
			.setOption(ColorUtil.wrapWithColorTag("Steward", java.awt.Color.ORANGE))
			.setTarget(event.getTarget())
			.setType(MenuAction.RUNELITE);
		Menu sub = parent.createSubMenu();

		List<Category> categories = store.getCategories();
		Collections.reverse(categories);
		sub.createMenuEntry(-1)
			.setOption("Leave where it is")
			.setType(MenuAction.RUNELITE)
			.onClick(e -> setCategory(id, CategoryStore.KEEP));
		if (overridden)
		{
			sub.createMenuEntry(-1)
				.setOption("Automatic")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> setCategory(id, null));
		}
		for (Category c : categories)
		{
			boolean selected = current != null && current.getId().equals(c.getId());
			sub.createMenuEntry(-1)
				.setOption(ColorUtil.wrapWithColorTag(c.getName(), c.getColor()) + (selected ? " (current)" : ""))
				.setType(MenuAction.RUNELITE)
				.onClick(e -> setCategory(id, c.getId()));
		}
	}

	private void setCategory(int itemId, String categoryId)
	{
		// every item with the same name goes with it
		Set<Integer> ids = new HashSet<>();
		ids.add(itemId);
		String name = info(itemId).getName();
		for (BankItem b : bankItems)
		{
			if (b.getInfo().getName().equalsIgnoreCase(name))
			{
				ids.add(b.getInfo().getId());
			}
		}
		store.setOverrides(ids, categoryId);
		markDirty();
		refreshPanelCategories();
	}

	private void addTabMenu(MenuEntryAdded event, int tab)
	{
		List<Category> categories = store.getCategories();
		Collections.reverse(categories);
		String tabName = Category.tabLabel(tab);

		MenuEntry fileAs = client.getMenu().createMenuEntry(-1)
			.setOption(ColorUtil.wrapWithColorTag("Steward: file all items as", java.awt.Color.ORANGE))
			.setTarget(event.getTarget())
			.setType(MenuAction.RUNELITE);
		Menu fileSub = fileAs.createSubMenu();
		for (Category c : categories)
		{
			fileSub.createMenuEntry(-1)
				.setOption(ColorUtil.wrapWithColorTag(c.getName(), c.getColor()))
				.setType(MenuAction.RUNELITE)
				.onClick(e -> fileTabAs(tab, c.getId()));
		}

		MenuEntry assign = client.getMenu().createMenuEntry(-1)
			.setOption(ColorUtil.wrapWithColorTag("Steward: use " + tabName + " for", java.awt.Color.ORANGE))
			.setTarget(event.getTarget())
			.setType(MenuAction.RUNELITE);
		Menu assignSub = assign.createSubMenu();
		for (Category c : categories)
		{
			assignSub.createMenuEntry(-1)
				.setOption(ColorUtil.wrapWithColorTag(c.getName(), c.getColor()) + (c.getTab() == tab ? " (current)" : ""))
				.setType(MenuAction.RUNELITE)
				.onClick(e ->
				{
					c.setTab(tab);
					store.update(c);
					markDirty();
					refreshPanelCategories();
				});
		}
	}

	private void fileTabAs(int tab, String categoryId)
	{
		List<Integer> ids = new ArrayList<>();
		for (BankItem b : bankItems)
		{
			if (b.getTab() == tab)
			{
				ids.add(b.getInfo().getId());
			}
		}
		store.setOverrides(ids, categoryId);
		markDirty();
		refreshPanelCategories();
	}

	private void refreshPanelCategories()
	{
		BankStewardPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(p::rebuildCategories);
		}
	}

	// ---- Tab headers ----

	/**
	 * Bank tab header widgets: index 0 is the All tab, 1-9 the tabs, and 10 the empty "New tab" slot.
	 * Found by their menu options; hidden ones are kept so callers can check. The tab bar children are
	 * normally 10 (All), 11-19 (tabs) and the New tab slot after the last tab, which is the fallback.
	 */
	Widget[] tabHeaders()
	{
		Widget[] headers = new Widget[11];
		Widget bar = client.getWidget(InterfaceID.Bankmain.TABS);
		Widget[] children = bar == null ? null : bar.getDynamicChildren();
		if (children == null)
		{
			return headers;
		}

		List<Widget> tabs = new ArrayList<>();
		for (Widget w : children)
		{
			String[] actions = w.getActions();
			if (actions == null)
			{
				continue;
			}
			for (String a : actions)
			{
				if ("View all items".equals(a))
				{
					headers[0] = w;
					break;
				}
				if ("View tab".equals(a))
				{
					tabs.add(w);
					break;
				}
				if ("New tab".equals(a))
				{
					headers[10] = w;
					break;
				}
			}
		}

		if (headers[0] == null && tabs.isEmpty())
		{
			for (int t = 0; t <= numTabs && 10 + t < children.length; t++)
			{
				headers[t] = children[10 + t];
			}
			if (numTabs < 9 && 11 + numTabs < children.length)
			{
				headers[10] = children[11 + numTabs];
			}
			return headers;
		}

		// tabs are listed in order, so child index gives tab number
		tabs.sort(Comparator.comparingInt(Widget::getIndex));
		for (int i = 0; i < tabs.size() && i < 9; i++)
		{
			headers[i + 1] = tabs.get(i);
		}
		return headers;
	}

	/**
	 * Screen bounds of a visible tab header, or null. numTabs+1 is the New tab slot.
	 */
	java.awt.Rectangle tabBounds(int tab)
	{
		Widget[] headers = tabHeaders();
		Widget w = null;
		if (tab >= 0 && tab <= numTabs)
		{
			w = headers[tab];
		}
		else if (tab == numTabs + 1)
		{
			w = headers[10];
		}
		if (w == null || w.isHidden() || w.getBounds().x < 0)
		{
			return null;
		}
		return w.getBounds();
	}

	private void logTabBar()
	{
		Widget bar = client.getWidget(InterfaceID.Bankmain.TABS);
		Widget[] children = bar == null ? null : bar.getDynamicChildren();
		if (children == null)
		{
			return;
		}
		for (Widget w : children)
		{
			if (w.getActions() != null)
			{
				log.debug("Tab bar child {} at {}: {}", w.getIndex(), w.getBounds(), java.util.Arrays.toString(w.getActions()));
			}
		}
	}

	private int tabForHeader(int childIndex)
	{
		Widget[] headers = tabHeaders();
		for (int t = 0; t <= numTabs; t++)
		{
			if (headers[t] != null && headers[t].getIndex() == childIndex)
			{
				return t;
			}
		}
		return -1;
	}

	Widget itemWidget(int slot)
	{
		Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
		return items == null ? null : items.getChild(slot);
	}
}
