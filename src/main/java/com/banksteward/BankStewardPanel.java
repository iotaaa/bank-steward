package com.banksteward;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;
import net.runelite.client.util.ImageUtil;

class BankStewardPanel extends PluginPanel
{
	private static final Color GOOD = new Color(110, 210, 120);
	private static final Color WARN = new Color(255, 170, 60);
	private static final String[] TAB_CHOICES = {"None", "Main", "Tab 1", "Tab 2", "Tab 3", "Tab 4", "Tab 5", "Tab 6", "Tab 7", "Tab 8", "Tab 9"};

	/**
	 * A snapshot of plugin state for the panel, made on the client thread.
	 */
	static class Status
	{
		boolean bankKnown;
		boolean bankOpen;
		boolean guideActive;
		boolean previewOpen;
		boolean tagTabOpen;
		boolean insertMode;
		boolean reorderStep;
		int total;
		int outOfPlace;
		int startOutOfPlace;
		String instruction;
		List<String> warnings = Collections.emptyList();
		Map<String, Integer> counts = Collections.emptyMap();
		int unassigned;
	}

	private final BankStewardPlugin plugin;
	private final CategoryStore store;
	private final ColorPickerManager colorPickerManager;

	private final JLabel headline = new JLabel();
	private final JLabel subline = new JLabel();
	private final JProgressBar progress = new JProgressBar(0, 100);
	private final JButton organiseButton = new JButton();
	private final JButton previewButton = new JButton();
	private final JPanel stepCard = new JPanel(new BorderLayout(0, 4));
	private final JLabel stepText = new JLabel();
	private final JLabel stepWarning = new JLabel();
	private final JPanel categoryList = new JPanel();

	private Status status = new Status();

	@Inject
	BankStewardPanel(BankStewardPlugin plugin, CategoryStore store, ColorPickerManager colorPickerManager)
	{
		this.plugin = plugin;
		this.store = store;
		this.colorPickerManager = colorPickerManager;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

		content.add(buildHeader());
		content.add(Box.createVerticalStrut(10));
		content.add(buildStatusCard());
		content.add(Box.createVerticalStrut(8));
		content.add(buildButtons());
		content.add(Box.createVerticalStrut(8));
		content.add(buildStepCard());
		content.add(Box.createVerticalStrut(14));
		content.add(buildCategoriesHeader());
		content.add(Box.createVerticalStrut(6));
		categoryList.setLayout(new BoxLayout(categoryList, BoxLayout.Y_AXIS));
		categoryList.setAlignmentX(LEFT_ALIGNMENT);
		content.add(categoryList);
		content.add(Box.createVerticalStrut(10));
		content.add(buildFooter());

		add(content, BorderLayout.NORTH);
		rebuildCategories();
		refresh();
	}

	private JPanel buildHeader()
	{
		JPanel header = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
		header.setAlignmentX(LEFT_ALIGNMENT);
		JLabel icon = new JLabel(new ImageIcon(ImageUtil.loadImageResource(BankStewardPlugin.class, "nav_icon.png")));
		JLabel title = new JLabel("Bank Steward");
		title.setFont(FontManager.getRunescapeBoldFont().deriveFont(18f));
		title.setForeground(Color.WHITE);
		header.add(icon);
		header.add(title);
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		return header;
	}

	private JPanel buildStatusCard()
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(10, 10, 10, 10));
		card.setAlignmentX(LEFT_ALIGNMENT);

		headline.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
		headline.setAlignmentX(CENTER_ALIGNMENT);
		subline.setFont(FontManager.getRunescapeSmallFont());
		subline.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		subline.setAlignmentX(CENTER_ALIGNMENT);
		progress.setForeground(GOOD);
		progress.setBackground(ColorScheme.DARK_GRAY_COLOR);
		progress.setStringPainted(true);
		progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
		progress.setAlignmentX(CENTER_ALIGNMENT);

		card.add(headline);
		card.add(Box.createVerticalStrut(2));
		card.add(subline);
		card.add(Box.createVerticalStrut(8));
		card.add(progress);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
		return card;
	}

	private JPanel buildButtons()
	{
		JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		organiseButton.setToolTipText("Highlights one move at a time until the bank is in order");
		organiseButton.addActionListener(e -> plugin.toggleGuide());
		previewButton.setToolTipText("Shows your whole bank already organised, as a tag tab you can use straight away");
		previewButton.addActionListener(e -> plugin.togglePreview());
		buttons.add(organiseButton);
		buttons.add(previewButton);
		buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		return buttons;
	}

	private JPanel buildStepCard()
	{
		stepCard.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		stepCard.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, GOOD),
			new EmptyBorder(8, 8, 8, 8)));
		stepCard.setAlignmentX(LEFT_ALIGNMENT);
		stepText.setForeground(Color.WHITE);
		stepWarning.setForeground(WARN);
		stepWarning.setFont(FontManager.getRunescapeSmallFont());
		stepCard.add(stepText, BorderLayout.CENTER);
		stepCard.add(stepWarning, BorderLayout.SOUTH);
		return stepCard;
	}

	private JPanel buildCategoriesHeader()
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setAlignmentX(LEFT_ALIGNMENT);
		JLabel title = new JLabel("Categories");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		JButton add = new JButton("+ Add");
		add.setToolTipText("Add a category");
		add.addActionListener(e ->
		{
			String name = JOptionPane.showInputDialog(this, "Name for the new category:", "Add category", JOptionPane.PLAIN_MESSAGE);
			if (name != null && !name.trim().isEmpty())
			{
				store.add(name.trim());
				categoriesChanged();
			}
		});
		header.add(title, BorderLayout.WEST);
		header.add(add, BorderLayout.EAST);
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		return header;
	}

	private JPanel buildFooter()
	{
		JPanel footer = new JPanel();
		footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
		footer.setAlignmentX(LEFT_ALIGNMENT);
		JLabel help = new JLabel("<html>Right-click a bank item to change its category, or a bank tab to file every item in it.</html>");
		help.setFont(FontManager.getRunescapeSmallFont());
		help.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		help.setAlignmentX(LEFT_ALIGNMENT);
		JLabel reset = new JLabel("Reset categories");
		reset.setFont(FontManager.getRunescapeSmallFont());
		reset.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		reset.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		reset.setAlignmentX(LEFT_ALIGNMENT);
		reset.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				int answer = JOptionPane.showConfirmDialog(BankStewardPanel.this,
					"Put the categories back to the defaults and forget every item you've filed by hand?",
					"Reset categories", JOptionPane.YES_NO_OPTION);
				if (answer == JOptionPane.YES_OPTION)
				{
					store.resetToDefaults();
					categoriesChanged();
				}
			}
		});
		footer.add(help);
		footer.add(Box.createVerticalStrut(8));
		footer.add(reset);
		return footer;
	}

	void update(Status status)
	{
		boolean countsChanged = !status.counts.equals(this.status.counts) || status.unassigned != this.status.unassigned;
		this.status = status;
		refresh();
		if (countsChanged)
		{
			rebuildCategories();
		}
	}

	private void refresh()
	{
		Status s = status;
		if (!s.bankKnown)
		{
			headline.setText("Open your bank");
			headline.setForeground(Color.WHITE);
			subline.setText("Bank Steward reads it when you open it");
		}
		else if (s.outOfPlace == 0)
		{
			headline.setText("Bank in order");
			headline.setForeground(GOOD);
			subline.setText(s.total + " items, all in place");
		}
		else
		{
			headline.setText(s.outOfPlace + " to move");
			headline.setForeground(WARN);
			subline.setText(s.total + " items in your bank");
		}

		progress.setVisible(s.guideActive && s.startOutOfPlace > 0);
		if (s.startOutOfPlace > 0)
		{
			int done = s.startOutOfPlace - s.outOfPlace;
			progress.setValue(Math.max(0, done * 100 / s.startOutOfPlace));
			progress.setString(done + " / " + s.startOutOfPlace + " moves");
		}

		organiseButton.setText(s.guideActive ? "Stop" : "Organise");
		organiseButton.setEnabled(s.bankKnown && (s.guideActive || s.outOfPlace > 0));
		previewButton.setText(s.previewOpen ? "Close preview" : "Preview");
		previewButton.setEnabled(s.bankOpen);

		String warning = null;
		if (s.guideActive && !s.bankOpen)
		{
			warning = "Open your bank to carry on.";
		}
		else if (s.guideActive && s.tagTabOpen)
		{
			warning = "Close the tag tab to carry on.";
		}
		else if (s.guideActive && s.reorderStep && !s.insertMode)
		{
			warning = "Switch the bank to Insert mode (button at the bottom of the bank).";
		}
		else if (!s.warnings.isEmpty())
		{
			warning = s.warnings.get(0);
		}

		stepCard.setVisible(s.guideActive || s.previewOpen || warning != null);
		if (s.previewOpen && !s.guideActive)
		{
			stepText.setText(html("This is your bank organised. You can withdraw from it as normal, "
				+ "or press Organise to make your real tabs match."));
		}
		else
		{
			stepText.setText(html(s.instruction != null ? s.instruction : ""));
		}
		stepWarning.setText(warning == null ? "" : html(warning));
		revalidate();
		repaint();
	}

	private static String html(String text)
	{
		return "<html><body style='width:170px'>" + text + "</body></html>";
	}

	void rebuildCategories()
	{
		categoryList.removeAll();
		for (Category c : store.getCategories())
		{
			categoryList.add(categoryRow(c));
			categoryList.add(Box.createVerticalStrut(4));
		}
		if (status.unassigned > 0)
		{
			JLabel left = new JLabel(status.unassigned + " items left where they are");
			left.setFont(FontManager.getRunescapeSmallFont());
			left.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			categoryList.add(left);
		}
		categoryList.revalidate();
		categoryList.repaint();
	}

	private JPanel categoryRow(Category c)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 4, 4, 4));
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

		JButton swatch = new JButton();
		swatch.setPreferredSize(new Dimension(16, 16));
		swatch.setBackground(c.getColor());
		swatch.setBorder(BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR));
		swatch.setFocusPainted(false);
		swatch.setToolTipText("Change colour");
		swatch.addActionListener(e ->
		{
			RuneliteColorPicker picker = colorPickerManager.create(this, c.getColor(), c.getName() + " colour", true);
			picker.setOnClose(color ->
			{
				c.setColor(color);
				store.update(c);
				categoriesChanged();
			});
			picker.setVisible(true);
		});
		JPanel swatchHolder = new JPanel(new GridLayout(0, 1));
		swatchHolder.setOpaque(false);
		swatchHolder.setBorder(new EmptyBorder(6, 0, 6, 0));
		swatchHolder.add(swatch);

		JPanel text = new JPanel(new GridLayout(2, 1));
		text.setOpaque(false);
		JLabel name = new JLabel(c.getName());
		name.setForeground(Color.WHITE);
		int count = status.counts.getOrDefault(c.getId(), 0);
		JLabel detail = new JLabel(count + (count == 1 ? " item" : " items"));
		detail.setFont(FontManager.getRunescapeSmallFont());
		detail.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		text.add(name);
		text.add(detail);

		JComboBox<String> tab = new JComboBox<>(TAB_CHOICES);
		tab.setSelectedIndex(c.getTab() + 1);
		tab.setPreferredSize(new Dimension(66, 22));
		tab.setToolTipText("Which bank tab this category goes in");
		tab.addActionListener(e ->
		{
			int chosen = tab.getSelectedIndex() - 1;
			if (chosen != c.getTab())
			{
				c.setTab(chosen);
				store.update(c);
				categoriesChanged();
			}
		});

		JButton more = new JButton("⋮");
		more.setPreferredSize(new Dimension(20, 22));
		more.setBorder(new EmptyBorder(0, 0, 0, 0));
		more.setToolTipText("More");
		more.addActionListener(e -> moreMenu(c).show(more, 0, more.getHeight()));

		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 3));
		right.setOpaque(false);
		right.add(tab);
		right.add(more);

		row.add(swatchHolder, BorderLayout.WEST);
		row.add(text, BorderLayout.CENTER);
		row.add(right, BorderLayout.EAST);
		return row;
	}

	private JPopupMenu moreMenu(Category c)
	{
		JPopupMenu menu = new JPopupMenu();
		JMenuItem rename = new JMenuItem("Rename");
		rename.addActionListener(e ->
		{
			String name = (String) JOptionPane.showInputDialog(this, "New name:", "Rename category",
				JOptionPane.PLAIN_MESSAGE, null, null, c.getName());
			if (name != null && !name.trim().isEmpty())
			{
				c.setName(name.trim());
				store.update(c);
				categoriesChanged();
			}
		});
		JMenuItem patterns = new JMenuItem("Name patterns...");
		patterns.addActionListener(e ->
		{
			String value = (String) JOptionPane.showInputDialog(this,
				"<html>Items whose name matches go in this category.<br>Separate with commas, * matches anything.<br>"
					+ "Example: <i>*bones, dragon *, coal</i></html>",
				"Name patterns", JOptionPane.PLAIN_MESSAGE, null, null, c.getPatterns());
			if (value != null)
			{
				c.setPatterns(value.trim());
				store.update(c);
				categoriesChanged();
			}
		});
		JMenuItem up = new JMenuItem("Move up");
		up.addActionListener(e ->
		{
			store.move(c.getId(), -1);
			categoriesChanged();
		});
		JMenuItem down = new JMenuItem("Move down");
		down.addActionListener(e ->
		{
			store.move(c.getId(), 1);
			categoriesChanged();
		});
		JMenuItem delete = new JMenuItem("Delete");
		delete.addActionListener(e ->
		{
			int answer = JOptionPane.showConfirmDialog(this, "Delete " + c.getName() + "?", "Delete category",
				JOptionPane.YES_NO_OPTION);
			if (answer == JOptionPane.YES_OPTION)
			{
				store.remove(c.getId());
				categoriesChanged();
			}
		});
		menu.add(rename);
		menu.add(patterns);
		menu.addSeparator();
		menu.add(up);
		menu.add(down);
		menu.addSeparator();
		menu.add(delete);
		return menu;
	}

	private void categoriesChanged()
	{
		rebuildCategories();
		plugin.markDirty();
	}
}
