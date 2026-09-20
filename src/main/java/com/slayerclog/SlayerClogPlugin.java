package com.slayerclog;

import com.google.inject.Provides;
import com.slayerclog.clog.CollectionLogManager;
import com.slayerclog.task.ItemGroup;
import com.slayerclog.task.MonsterMapping;
import com.slayerclog.task.MortimerInterfaceHandler;
import com.slayerclog.task.SlayerMonster;
import com.slayerclog.task.SlayerTaskTracker;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
	name = "Slayer Clog Check",
	description = "Shows collection log progress for your current slayer task, and compares each option at Mortimer.",
	tags = {"slayer", "collection", "log", "clog", "task", "mortimer", "skip"}
)
public class SlayerClogPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private SlayerClogConfig config;

	@Inject
	private CollectionLogManager clogManager;

	@Inject
	private MonsterMapping mapping;

	@Inject
	private SlayerTaskTracker taskTracker;

	@Inject
	private MortimerInterfaceHandler mortimer;

	private SlayerClogPanel panel;
	private NavigationButton navButton;

	// Chest rewards: Konar tasks give Brimstone keys, Krystilia's wilderness tasks give Larran's keys.
	// mapping label for a task's shared superior drops
	private static final String SUPERIOR_LABEL = "Superior";
	private static final String BRIMSTONE_CHEST = "Brimstone chest";
	private static final String LARRANS_CHEST = "Larran's chest";
	private static final int KRYSTILIA_SLAYER_MASTER = 7;

	// Mortimer state
	private boolean mortimerActive;
	private List<String> mortimerOptions;

	// Task polling state
	private String lastTaskName;
	private int lastAmount = -1;

	// "New item added to your collection log: <name>."
	private static final Pattern CLOG_MESSAGE = Pattern.compile(
		"added to your collection log:\\s*(.+)", Pattern.CASE_INSENSITIVE);
	private final Map<String, Integer> itemNameToId = new HashMap<>();
	private boolean nameIndexBuilt;

	@Provides
	SlayerClogConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SlayerClogConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = new SlayerClogPanel(itemManager, config);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/com/slayerclog/icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Slayer Clog Check")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(() ->
			{
				clogManager.load(client.getAccountHash());
				refreshView();
			});
		}
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		panel = null;
		navButton = null;
		mortimerActive = false;
		mortimerOptions = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			clogManager.load(client.getAccountHash());
			refreshView();
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.COLLECTION_DRAW_LIST)
		{
			clogManager.captureOpenPage();
			refreshView();
		}
	}

	/** Ticks an item as soon as the new collection log item message fires. */
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		final String message = event.getMessage();
		if (message == null)
		{
			return;
		}
		final Matcher m = CLOG_MESSAGE.matcher(message);
		if (!m.find())
		{
			return;
		}
		final String itemName = m.group(1).replaceAll("<[^>]*>", "").replaceAll("[.\\s]+$", "").trim();
		ensureNameIndex();
		final Integer id = itemNameToId.get(itemName.toLowerCase());
		if (id != null)
		{
			clogManager.markObtained(id);
			refreshView();
		}
	}

	/** Builds a name to item id index. Client thread only. */
	private void ensureNameIndex()
	{
		if (nameIndexBuilt)
		{
			return;
		}
		for (int id : mapping.allItemIds())
		{
			try
			{
				final String name = itemManager.getItemComposition(id).getName();
				if (name != null && !name.isEmpty())
				{
					itemNameToId.put(name.toLowerCase(), id);
				}
			}
			catch (Exception ignored)
			{
				// no composition
			}
		}
		nameIndexBuilt = true;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Poll for the Mortimer interface; it does not reliably emit WidgetLoaded.
		if (config.mortimerView())
		{
			final List<String> opts = mortimer.detect(MortimerInterfaceHandler.MORTIMER_GROUP_ID);
			if (!opts.isEmpty())
			{
				if (!mortimerActive || !opts.equals(mortimerOptions))
				{
					mortimerActive = true;
					mortimerOptions = opts;
					refreshView();
				}
				return;
			}
			if (mortimerActive)
			{
				mortimerActive = false;
				mortimerOptions = null;
				refreshView();
			}
		}

		// Poll the task, stored as per-profile config.
		final String name = taskTracker.getCurrentTaskName();
		final int amount = taskTracker.getAmount();
		if (!Objects.equals(name, lastTaskName) || amount != lastAmount)
		{
			lastTaskName = name;
			lastAmount = amount;
			refreshView();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (SlayerClogConfig.GROUP.equals(event.getGroup()))
		{
			refreshView();
		}
	}

	/** Rebuilds the panel: data on the client thread, UI on the EDT. */
	private void refreshView()
	{
		if (panel == null)
		{
			return;
		}

		clientThread.invokeLater(() ->
		{
			if (mortimerActive && mortimerOptions != null && config.mortimerView())
			{
				final List<SlayerClogPanel.Option> options = new ArrayList<>();
				for (String optionName : mortimerOptions)
				{
					final SlayerMonster monster = mapping.get(optionName);
					final List<SlayerClogPanel.Section> sections =
						monster == null ? new ArrayList<>() : buildSections(monster);
					options.add(new SlayerClogPanel.Option(optionName, sections));
				}
				updatePanel(() -> panel.showMortimer(options));
				return;
			}

			final String taskName = taskTracker.getCurrentTaskName();
			if (taskName == null)
			{
				updatePanel(() -> panel.showMessage(
					"No active slayer task.", "Get a slayer task to see its collection log progress."));
				return;
			}

			final int amount = taskTracker.getAmount();
			final String location = taskTracker.getTaskLocation();
			final SlayerMonster monster = mapping.get(taskName);
			final List<SlayerClogPanel.Section> sections =
				monster == null ? new ArrayList<>() : buildSections(monster);
			if (location != null)
			{
				addChest(sections, BRIMSTONE_CHEST);
			}
			if (client.getVarbitValue(VarbitID.SLAYER_MASTER) == KRYSTILIA_SLAYER_MASTER)
			{
				addChest(sections, LARRANS_CHEST);
			}
			updatePanel(() -> panel.showTask(taskName, amount, location, sections));
		});
	}

	/** One section per monster version. Client thread only. */
	private List<SlayerClogPanel.Section> buildSections(SlayerMonster monster)
	{
		// one section per monster, merging its groups; each row keeps its own page
		final Map<String, List<SlayerClogPanel.ItemRow>> byMonster = new LinkedHashMap<>();
		for (ItemGroup group : monster.getGroups())
		{
			if (!config.showSuperior() && SUPERIOR_LABEL.equals(group.getMonster()))
			{
				continue;
			}
			final List<SlayerClogPanel.ItemRow> rows =
				byMonster.computeIfAbsent(group.getMonster(), k -> new ArrayList<>());
			for (int itemId : group.getItems())
			{
				final String name = itemManager.getItemComposition(itemId).getName();
				final Boolean status = clogManager.isObtained(group.getPage(), itemId);
				final boolean synced = status != null;
				final boolean obtained = Boolean.TRUE.equals(status);
				final int quantity = clogManager.getQuantity(group.getPage(), itemId);
				final String rate = group.getRates().get(itemId);
				rows.add(new SlayerClogPanel.ItemRow(itemId, name, obtained, quantity, synced, rate, group.getPage()));
			}
		}
		final List<SlayerClogPanel.Section> sections = new ArrayList<>();
		for (Map.Entry<String, List<SlayerClogPanel.ItemRow>> e : byMonster.entrySet())
		{
			sections.add(new SlayerClogPanel.Section(e.getKey(), e.getValue()));
		}
		return sections;
	}

	/** Adds a chest entry from the mapping as extra sections. */
	private void addChest(List<SlayerClogPanel.Section> sections, String key)
	{
		final SlayerMonster chest = mapping.get(key);
		if (chest != null)
		{
			sections.addAll(buildSections(chest));
		}
	}

	private void updatePanel(Runnable update)
	{
		final SlayerClogPanel current = panel;
		if (current == null)
		{
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			if (panel == current)
			{
				update.run();
			}
		});
	}
}
