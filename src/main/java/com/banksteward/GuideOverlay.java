package com.banksteward;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Tab header colours, and the guide's "move this here" highlights with an arrow between them.
 */
class GuideOverlay extends Overlay
{
	private static final Stroke THICK = new BasicStroke(2f);
	private static final Stroke ARROW = new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	private static final Color WARNING = new Color(255, 70, 70);

	// Alch Ledger outlines items in yellow, green, blue, purple and red, so with it on the default guide
	// colours could pass for a tier outline. These stand apart from all of them.
	private static final Color DEFAULT_MOVE = new Color(80, 230, 100, 230);
	private static final Color DEFAULT_TARGET = new Color(255, 210, 60, 230);
	private static final Color ALCH_SAFE_MOVE = new Color(245, 245, 245, 235);
	private static final Color ALCH_SAFE_TARGET = new Color(255, 120, 230, 235);

	private final Client client;
	private final BankStewardPlugin plugin;
	private final BankStewardConfig config;
	private final CategoryStore store;

	@Inject
	GuideOverlay(Client client, BankStewardPlugin plugin, BankStewardConfig config, CategoryStore store)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.store = store;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!plugin.isBankOpen() || plugin.isTagTabOpen())
		{
			return null;
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		if (config.tabColours())
		{
			drawTabColours(g);
		}

		Planner.Plan plan = plugin.getPlan();
		if (!plugin.isGuideActive() || plan == null)
		{
			return null;
		}

		Planner.Step step = plan.getNext();
		switch (step.getType())
		{
			case MOVE_TO_TAB:
				drawMove(g, step.getItem(), plugin.tabBounds(step.getTab()), step.isNewTab() ? "NEW TAB" : "DROP");
				break;
			case REORDER:
				if (!plugin.isInsertMode())
				{
					drawInsertWarning(g);
					break;
				}
				Widget target = visibleItem(step.getTargetSlot());
				if (target != null)
				{
					drawMove(g, step.getItem(), target.getBounds(), "HERE");
				}
				else
				{
					drawMoveOffscreen(g, step.getItem(), step.getTargetSlot());
				}
				break;
			case OPEN_TAB:
				Rectangle tab = plugin.tabBounds(step.getTab());
				if (tab != null)
				{
					drawBox(g, tab, pulse(targetColour()), "OPEN");
				}
				break;
			default:
				break;
		}
		return null;
	}

	private void drawTabColours(Graphics2D g)
	{
		Widget[] headers = plugin.tabHeaders();
		List<Category> categories = store.getCategories();
		for (int t = 0; t <= plugin.getNumTabs(); t++)
		{
			if (headers[t] == null || headers[t].isHidden() || headers[t].getBounds().x < 0)
			{
				continue;
			}
			Rectangle r = headers[t].getBounds();
			int x = r.x + 3;
			int width = r.width - 6;
			// several categories can share a tab: split the bar between them
			int n = 0;
			for (Category c : categories)
			{
				if (c.getTab() == t)
				{
					n++;
				}
			}
			int i = 0;
			for (Category c : categories)
			{
				if (c.getTab() != t)
				{
					continue;
				}
				int x0 = x + width * i / n;
				int x1 = x + width * (i + 1) / n;
				g.setColor(Fade.category(c.getColor(), config.categoryOpacity()));
				g.fillRect(x0, r.y + r.height - 4, x1 - x0, 3);
				i++;
			}
		}
	}

	private void drawMove(Graphics2D g, BankItem item, Rectangle target, String targetLabel)
	{
		Color move = moveColour();
		Color drop = pulse(targetColour());

		// While the item is being dragged, the arrow starts from the mouse. Dragging can scroll the
		// item itself out of view, so the target has to show either way.
		Rectangle from = null;
		boolean dragging = isDragging(item);
		if (dragging)
		{
			net.runelite.api.Point mouse = client.getMouseCanvasPosition();
			from = new Rectangle(mouse.getX() - 8, mouse.getY() - 8, 16, 16);
		}
		else
		{
			Widget source = visibleItem(item.getSlot());
			if (source != null)
			{
				from = source.getBounds();
			}
		}

		if (from != null && target != null && config.showArrow())
		{
			drawArrow(g, from, target, move);
		}
		if (from != null && !dragging)
		{
			drawBox(g, from, move, "MOVE");
		}
		if (target != null)
		{
			drawBox(g, target, drop, targetLabel);
		}
		if (from == null)
		{
			drawHint(g, "Scroll to find " + item.getInfo().getName());
		}
	}

	private boolean isDragging(BankItem item)
	{
		if (!client.isDraggingWidget())
		{
			return false;
		}
		Widget dragged = client.getDraggedWidget();
		return dragged != null && dragged.getId() == InterfaceID.Bankmain.ITEMS && dragged.getIndex() == item.getSlot();
	}

	/**
	 * The drop target is scrolled out of view. Holding a dragged item at the edge of the bank scrolls it,
	 * so point at that edge, in the target's column, until the target comes into view.
	 */
	private void drawMoveOffscreen(Graphics2D g, BankItem item, int targetSlot)
	{
		Widget w = plugin.itemWidget(targetSlot);
		Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (w == null || w.isHidden() || container == null)
		{
			drawMove(g, item, null, "HERE");
			return;
		}
		Rectangle view = container.getBounds();
		Rectangle r = w.getBounds();
		boolean above = r.y < view.y;
		Rectangle edge = new Rectangle(r.x, above ? view.y : view.y + view.height - r.height, r.width, r.height);
		drawMove(g, item, edge, above ? "DRAG UP" : "DRAG DOWN");

		BankItem target = plugin.itemAtSlot(targetSlot);
		String name = target != null ? target.getInfo().getName() : "the target";
		drawHint(g, "Hold it at the " + (above ? "top" : "bottom") + " edge to scroll to " + name, !above);
	}

	private void drawInsertWarning(Graphics2D g)
	{
		Widget button = client.getWidget(InterfaceID.Bankmain.SWAP_INSERT);
		if (button != null && !button.isHidden())
		{
			drawBox(g, button.getBounds(), pulse(WARNING), "INSERT");
		}
		drawHint(g, "Switch the bank to Insert mode");
	}

	/**
	 * The item's widget, if it's on screen inside the scrolling item area.
	 */
	private Widget visibleItem(int slot)
	{
		Widget w = plugin.itemWidget(slot);
		Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (w == null || w.isHidden() || container == null)
		{
			return null;
		}
		Rectangle view = container.getBounds();
		Rectangle r = w.getBounds();
		if (r.y < view.y - r.height / 2 || r.y + r.height > view.y + view.height + r.height / 2)
		{
			return null;
		}
		return w;
	}

	private void drawBox(Graphics2D g, Rectangle r, Color c, String label)
	{
		g.setStroke(THICK);
		g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.min(40, c.getAlpha())));
		g.fillRect(r.x, r.y, r.width, r.height);
		g.setColor(Fade.of(c));
		g.drawRect(r.x, r.y, r.width, r.height);

		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();
		int w = fm.stringWidth(label) + 4;
		int x = r.x + (r.width - w) / 2;
		int y = r.y - fm.getHeight() - 1;
		g.setColor(new Color(0, 0, 0, 190));
		g.fillRect(x, y, w, fm.getHeight());
		g.setColor(c);
		g.drawString(label, x + 2, y + fm.getAscent());
	}

	private void drawHint(Graphics2D g, String text)
	{
		drawHint(g, text, true);
	}

	private void drawHint(Graphics2D g, String text, boolean top)
	{
		Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (container == null)
		{
			return;
		}
		Rectangle view = container.getBounds();
		g.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics fm = g.getFontMetrics();
		int w = fm.stringWidth(text) + 12;
		int x = view.x + (view.width - w) / 2;
		int y = top ? view.y + 6 : view.y + view.height - fm.getHeight() - 12;
		g.setColor(new Color(0, 0, 0, 200));
		g.fillRoundRect(x, y, w, fm.getHeight() + 6, 6, 6);
		g.setColor(targetColour());
		g.drawString(text, x + 6, y + 3 + fm.getAscent());
	}

	private void drawArrow(Graphics2D g, Rectangle from, Rectangle to, Color c)
	{
		double x1 = from.getCenterX(), y1 = from.getCenterY();
		double x2 = to.getCenterX(), y2 = to.getCenterY();
		double len = Math.hypot(x2 - x1, y2 - y1);
		if (len < 20)
		{
			return;
		}
		// stop short of both boxes
		double ux = (x2 - x1) / len, uy = (y2 - y1) / len;
		double start = 14, end = 16;
		int sx = (int) (x1 + ux * start), sy = (int) (y1 + uy * start);
		int ex = (int) (x2 - ux * end), ey = (int) (y2 - uy * end);

		g.setStroke(ARROW);
		g.setColor(new Color(0, 0, 0, 100));
		g.drawLine(sx + 1, sy + 1, ex + 1, ey + 1);
		g.setColor(Fade.of(c));
		g.drawLine(sx, sy, ex, ey);

		AffineTransform old = g.getTransform();
		g.translate(ex, ey);
		g.rotate(Math.atan2(uy, ux));
		Polygon head = new Polygon(new int[]{4, -7, -7}, new int[]{0, -6, 6}, 3);
		g.fillPolygon(head);
		g.setTransform(old);
	}

	/**
	 * The guide colours, swapped for ones Alch Ledger doesn't use when it's on and the player kept the defaults.
	 */
	private Color moveColour()
	{
		Color c = config.moveColour();
		return plugin.isAlchLedgerActive() && c.equals(DEFAULT_MOVE) ? ALCH_SAFE_MOVE : c;
	}

	private Color targetColour()
	{
		Color c = config.targetColour();
		return plugin.isAlchLedgerActive() && c.equals(DEFAULT_TARGET) ? ALCH_SAFE_TARGET : c;
	}

	private static Color pulse(Color c)
	{
		double t = (System.currentTimeMillis() % 1200) / 1200.0;
		double f = 0.55 + 0.45 * (0.5 + 0.5 * Math.sin(t * 2 * Math.PI));
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (c.getAlpha() * f));
	}
}
