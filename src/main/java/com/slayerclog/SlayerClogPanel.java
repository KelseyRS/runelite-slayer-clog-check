package com.slayerclog;

import com.slayerclog.task.KillCountTracker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
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

/** Sidebar panel. */
class SlayerClogPanel extends PluginPanel
{
	/** A single collection log item row. */
	@Value
	static class ItemRow
	{
		int itemId;
		String name;
		boolean obtained;
		int quantity;
		boolean synced;
		/** Drop rate from the mapping, or null when unknown. */
		String rate;
		/** Collection log page this item is logged on, shown as a tooltip on its status. */
		String page;
	}

	/** One monster version of a task with its item rows. */
	@Value
	static class Section
	{
		String monster;
		List<ItemRow> rows;
		/** Kill count for this monster, or null when unknown or hidden by config. */
		Integer killCount;
		/** Superior section only: superiors seen on this task by the plugin's own tally, or null if hidden. */
		Integer superiorsHere;
		/** Superior section only: superiors killed on all tasks, from the Slayer kill log, or null. */
		Integer superiorsTotal;
		/** The task (or Mortimer option) this section belongs to, named in the per-task superiors line. */
		String task;
	}

	/** A Mortimer option: an offered task name and its per-monster sections. */
	@Value
	static class Option
	{
		String name;
		List<Section> sections;
		/** Kill count for this task from the Slayer kill log, or null when unknown or hidden by config. */
		Integer killCount;
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

	/** Current-task view, broken down per monster version. */
	void showTask(String taskName, int amount, String location, List<Section> sections, Integer killCount)
	{
		content.removeAll();
		content.add(titleLabel(amount > 0 ? taskName + " (" + amount + ")" : taskName));
		if (killCount != null)
		{
			// the title's brackets already hold the kills remaining, so the kill count gets its own line
			content.add(wrappedText("Kill count: " + formatKills(killCount), MISSING));
		}
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

		// only a sync prompt; each section's count already shows what is missing
		if (anyUnsynced(all))
		{
			content.add(wrappedText("Sync your collection log for a complete picture.", MISSING));
		}
		refresh();
	}

	/** Mortimer multi-option comparison view. */
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

			final JLabel name = new JLabel(plain(option.getName()) + killCountText(option.getKillCount()));
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

	// --- rendering helpers ---

	/** A monster-version section: a sub-header ("Monster x/y") plus its item rows. */
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
		final JLabel monster = new JLabel(plain(section.getMonster()) + killCountText(section.getKillCount()));
		monster.setFont(FontManager.getRunescapeSmallFont());
		monster.setForeground(new Color(200, 200, 200));
		head.add(monster, BorderLayout.WEST);
		final JLabel count = new JLabel(obtained + " / " + section.getRows().size());
		count.setFont(FontManager.getRunescapeSmallFont());
		count.setForeground(obtained == section.getRows().size() && !anyUnsynced ? OBTAINED : MISSING);
		head.add(count, BorderLayout.EAST);
		panel.add(head);

		// Superior section: one line per superior count, under the heading
		if (section.getSuperiorsHere() != null)
		{
			panel.add(noteLabel(formatKills(section.getSuperiorsHere()) + " " + plain(section.getTask()) + " "
				+ superiors(section.getSuperiorsHere())));
		}
		if (section.getSuperiorsTotal() != null)
		{
			panel.add(noteLabel(formatKills(section.getSuperiorsTotal()) + " " + superiors(section.getSuperiorsTotal())
				+ " across all tasks"));
		}
		if (section.getSuperiorsHere() != null || section.getSuperiorsTotal() != null)
		{
			// a small gap so the count lines read as their own block, apart from the item rows below
			panel.add(Box.createVerticalStrut(4));
		}

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

		final String rate = row.getRate();
		if (config.showDropRates() && rate != null && !rate.isEmpty())
		{
			// drop rate on a second line under the name
			final JPanel labels = new JPanel();
			labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
			name.setAlignmentX(Component.LEFT_ALIGNMENT);
			labels.add(name);

			final JLabel rateLabel = new JLabel(plain("(" + rate.replace('/', ':') + ")"));
			rateLabel.setFont(FontManager.getRunescapeSmallFont());
			rateLabel.setForeground(MISSING);
			rateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			labels.add(rateLabel);

			panel.add(labels, BorderLayout.CENTER);
		}
		else
		{
			panel.add(name, BorderLayout.CENTER);
		}

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
		// which collection log page this item is on, so the heading does not have to name it
		status.setToolTipText(plain(row.isSynced()
			? "In the " + row.getPage() + " collection log."
			: "In the " + row.getPage() + " collection log. Open that page to sync."));
		panel.add(status, BorderLayout.EAST);
		return panel;
	}

	/** " (1,234 KC)" after a monster, boss or Mortimer option name; empty when unknown. */
	private static String killCountText(Integer kills)
	{
		return kills == null ? "" : " (" + formatKills(kills) + " KC)";
	}

	private static String superiors(int n)
	{
		return n == 1 ? "superior" : "superiors";
	}

	/** A small muted line under a section heading, indented like the item rows. */
	private static JLabel noteLabel(String text)
	{
		final JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(MISSING);
		label.setBorder(BorderFactory.createEmptyBorder(3, 6, 1, 0));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/** 1234 -> "1,234"; the kill log stops counting at "Lots!", shown as "65,535+". */
	private static String formatKills(int kills)
	{
		return kills >= KillCountTracker.LOTS ? "65,535+" : String.format("%,d", kills);
	}

	/** True when any row's status is still unknown because its collection log page has not been opened. */
	private static boolean anyUnsynced(List<ItemRow> rows)
	{
		for (ItemRow row : rows)
		{
			if (!row.isSynced())
			{
				return true;
			}
		}
		return false;
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

	/** Strips angle brackets so Swing cannot interpret the string as HTML. */
	private static String plain(String text)
	{
		return text == null ? "" : text.replace("<", "").replace(">", "");
	}

	/** Escapes text placed in wrappedText's HTML so it renders literally. */
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
