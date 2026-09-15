package com.slayerclog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import lombok.Value;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

/** Sidebar panel. All methods must be called on the EDT. */
class SlayerClogPanel extends PluginPanel
{
	/** One item row. */
	@Value
	static class ItemRow
	{
		int itemId;
		String name;
		boolean obtained;
		int quantity;
		boolean synced;
	}

	/** One monster version and its rows. */
	@Value
	static class Section
	{
		String monster;
		List<ItemRow> rows;
	}

	/** A Mortimer option. */
	@Value
	static class Option
	{
		String name;
		List<Section> sections;
	}

	private static final Color OBTAINED = new Color(76, 175, 80);
	private static final Color MISSING = new Color(160, 160, 160);
	private static final Color WARN = new Color(220, 138, 0);

	private final ItemManager itemManager;
	private final SlayerClogConfig config;
	private final JPanel content = new JPanel();

	SlayerClogPanel(ItemManager itemManager, SlayerClogConfig config)
	{
		this.itemManager = itemManager;
		this.config = config;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		add(content, BorderLayout.NORTH);

		showMessage("No active slayer task.", "Get a slayer task to see its collection log progress.");
	}

	void showMessage(String title, String subtitle)
	{
		content.removeAll();
		content.add(titleLabel(title));
		if (subtitle != null)
		{
			content.add(wrappedText(subtitle, MISSING));
		}
		refresh();
	}

	/** Current task, per monster version. */
	void showTask(String taskName, int amount, String location, List<Section> sections)
	{
		content.removeAll();
		content.add(titleLabel(amount > 0 ? taskName + " (" + amount + ")" : taskName));
		if (location != null)
		{
			content.add(wrappedText(location, MISSING));
		}

		if (sections.isEmpty())
		{
			content.add(wrappedText("No collection log items are mapped for this task yet.", MISSING));
			refresh();
			return;
		}

		final List<ItemRow> all = new ArrayList<>();
		for (Section section : sections)
		{
			all.addAll(section.getRows());
			content.add(sectionPanel(section));
		}

		content.add(wrappedText(skipHint(all), MISSING));
		refresh();
	}

	/** Mortimer option comparison. */
	void showMortimer(List<Option> options)
	{
		content.removeAll();
		content.add(titleLabel("Mortimer"));
		content.add(wrappedText("Compare what each option can add to your collection log:", MISSING));

		for (Option option : options)
		{
			final JPanel card = new JPanel();
			card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
			card.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
				BorderFactory.createEmptyBorder(6, 0, 6, 0)));
			card.setAlignmentX(Component.LEFT_ALIGNMENT);

			final JLabel name = new JLabel(plain(option.getName()));
			name.setFont(FontManager.getRunescapeBoldFont());
			name.setForeground(Color.WHITE);
			name.setAlignmentX(Component.LEFT_ALIGNMENT);
			card.add(name);

			if (option.getSections().isEmpty())
			{
				card.add(wrappedText("No collection log items found.", MISSING));
			}
			else
			{
				for (Section section : option.getSections())
				{
					card.add(sectionPanel(section));
				}
			}
			content.add(card);
		}
		refresh();
	}

	/** Monster sub-header plus its item rows. */
	private JPanel sectionPanel(Section section)
	{
		final JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		int obtained = 0;
		boolean anyUnsynced = false;
		for (ItemRow row : section.getRows())
		{
			if (row.isObtained())
			{
				obtained++;
			}
			if (!row.isSynced())
			{
				anyUnsynced = true;
			}
		}

		final JPanel head = new JPanel(new BorderLayout());
		head.setAlignmentX(Component.LEFT_ALIGNMENT);
		final JLabel monster = new JLabel(plain(section.getMonster()));
		monster.setFont(FontManager.getRunescapeSmallFont());
		monster.setForeground(new Color(200, 200, 200));
		head.add(monster, BorderLayout.WEST);
		final JLabel count = new JLabel(obtained + " / " + section.getRows().size());
		count.setFont(FontManager.getRunescapeSmallFont());
		count.setForeground(obtained == section.getRows().size() && !anyUnsynced ? OBTAINED : MISSING);
		head.add(count, BorderLayout.EAST);
		panel.add(head);

		for (ItemRow row : section.getRows())
		{
			if (config.showOnlyUnobtained() && row.isObtained())
			{
				continue;
			}
			panel.add(itemRow(row));
		}
		if (anyUnsynced)
		{
			panel.add(wrappedText("Open this monster's collection log page to sync.", WARN));
		}
		return panel;
	}

	private JPanel itemRow(ItemRow row)
	{
		final JPanel panel = new JPanel(new BorderLayout(6, 0));
		panel.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 0));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		final JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(36, 32));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		final AsyncBufferedImage image = itemManager.getImage(row.getItemId());
		image.addTo(icon);
		panel.add(icon, BorderLayout.WEST);

		final JLabel name = new JLabel(plain(row.getName()));
		name.setForeground(row.isObtained() ? Color.WHITE : MISSING);
		panel.add(name, BorderLayout.CENTER);

		final JLabel status = new JLabel();
		if (!row.isSynced())
		{
			status.setText("?");
			status.setForeground(WARN);
		}
		else if (row.isObtained())
		{
			status.setText(config.showQuantities() && row.getQuantity() > 0 ? "✓ " + row.getQuantity() : "✓");
			status.setForeground(OBTAINED);
		}
		else
		{
			status.setText("✗");
			status.setForeground(MISSING);
		}
		panel.add(status, BorderLayout.EAST);
		return panel;
	}

	private static String skipHint(List<ItemRow> rows)
	{
		boolean anyUnsynced = false;
		boolean anyMissing = false;
		for (ItemRow row : rows)
		{
			if (!row.isSynced())
			{
				anyUnsynced = true;
			}
			else if (!row.isObtained())
			{
				anyMissing = true;
			}
		}
		if (anyUnsynced)
		{
			return "Sync your collection log for a complete picture.";
		}
		return anyMissing
			? "You are still missing log items"
			: "All collection log items collected";
	}

	private static JLabel titleLabel(String text)
	{
		final JLabel label = new JLabel(plain(text));
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(Color.WHITE);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static JLabel wrappedText(String text, Color color)
	{
		final JLabel label = new JLabel("<html><body style='width:150px'>" + escapeHtml(text) + "</body></html>");
		label.setForeground(color);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/** Strips angle brackets so Swing cannot treat the text as HTML. */
	private static String plain(String text)
	{
		return text == null ? "" : text.replace("<", "").replace(">", "");
	}

	/** Escapes text embedded in wrappedText's HTML. */
	private static String escapeHtml(String text)
	{
		return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private void refresh()
	{
		content.revalidate();
		content.repaint();
	}
}
