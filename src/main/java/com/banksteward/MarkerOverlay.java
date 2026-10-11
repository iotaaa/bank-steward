package com.banksteward;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Stroke;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Category colour markers on bank items, and a dashed box on items in the wrong tab.
 */
class MarkerOverlay extends WidgetItemOverlay
{
	private static final Color WRONG_TAB = new Color(255, 70, 70, 220);
	private static final Color TAB_ICON = new Color(255, 200, 60, 230);
	private static final Stroke DASHED = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 2f}, 0f);

	private final BankStewardPlugin plugin;
	private final BankStewardConfig config;

	@Inject
	MarkerOverlay(BankStewardPlugin plugin, BankStewardConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		showOnBank();
	}

	@Override
	public void renderItemOverlay(Graphics2D g, int itemId, WidgetItem widgetItem)
	{
		BankItem item = plugin.itemAtSlot(widgetItem.getWidget().getIndex());
		if (item == null || item.getItemId() != itemId)
		{
			// tag tab layouts put items in other widgets, so look it up by id
			item = plugin.itemById(itemId);
			if (item == null)
			{
				return;
			}
		}

		Rectangle r = widgetItem.getCanvasBounds();
		Category category = item.getCategory();
		BankStewardConfig.MarkerStyle style = config.markerStyle();
		if (config.showCategoryMarkers() && category != null && style != BankStewardConfig.MarkerStyle.NONE)
		{
			g.setColor(Fade.category(category.getColor(), config.categoryOpacity()));
			switch (style)
			{
				case CORNER:
					// bottom-right, clear of the quantity number in the top-left
					Polygon corner = new Polygon();
					corner.addPoint(r.x + r.width, r.y + r.height);
					corner.addPoint(r.x + r.width - 9, r.y + r.height);
					corner.addPoint(r.x + r.width, r.y + r.height - 9);
					g.fillPolygon(corner);
					break;
				case BAR:
					g.fillRect(r.x + 1, r.y + r.height - 2, r.width - 2, 2);
					break;
				case OUTLINE:
					g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
					break;
				default:
					break;
			}
		}

		Planner.Plan plan = plugin.getPlan();
		if (config.highlightTabIcons() && plan != null && !plugin.isTagTabOpen()
			&& plan.getTabIcons().contains(item.getSlot()))
		{
			// reserved as the tab's icon: gold frame and a gold corner in the top-right
			g.setColor(Fade.of(TAB_ICON));
			g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
			Polygon corner = new Polygon();
			corner.addPoint(r.x + r.width, r.y);
			corner.addPoint(r.x + r.width - 9, r.y);
			corner.addPoint(r.x + r.width, r.y + 9);
			g.fillPolygon(corner);
		}

		if (config.markMisplaced() && plan != null && !plugin.isTagTabOpen()
			&& plan.getWrongTab().contains(item.getSlot()))
		{
			Stroke old = g.getStroke();
			g.setStroke(DASHED);
			g.setColor(Fade.of(WRONG_TAB));
			g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
			g.setStroke(old);
		}
	}
}
