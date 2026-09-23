package com.slayerclog;

import com.google.inject.Provides;
import com.slayerclog.clog.CollectionLogManager;
import com.slayerclog.task.ItemGroup;
import com.slayerclog.task.KillCountTracker;
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
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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

	@Inject
	private KillCountTracker killCounts;

	private SlayerClogPanel panel;
	private NavigationButton navButton;

	// Konar is the only master that assigns an area, and Konar tasks are what yield Brimstone keys.
	private static final String SUPERIOR_LABEL = "Superior";
	private static final String BRIMSTONE_CHEST = "Brimstone chest";
	private static final String LARRANS_CHEST = "Larran's chest";
	private static final int KRYSTILIA_SLAYER_MASTER = 7;

	// Mortimer selection state
	private boolean mortimerActive;
	private List<String> mortimerOptions;

	// Current-task polling state
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
				killCounts.load(client.getAccountHash());
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
			killCounts.load(client.getAccountHash());
			refreshView();
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.COLLECTION_DRAW_LIST)
		{
			clogManager.captureOpenPage();
			// boss pages also show "X kills: N" in their header
			killCounts.captureCollectionLogHeader();
			refreshView();
		}
	}

	/** Ticks a newly obtained item from the "New item added to your collection log" message. */
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		final String message = event.getMessage();
		if (message == null)
		{
			return;
		}
		// "Your X kill count is: N" keeps boss kill counts current between visits to the kill logs
		if (killCounts.recordKillMessage(message))
		{
			refreshView();
			return;
		}
		// "A superior foe has appeared..." is counted against the current task: the game keeps no per-task tally
		if (killCounts.recordSuperior(message, taskTracker.getCurrentTaskName()))
		{
			refreshView();
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

	/** Lazily build a name -> item id index over every item in the mapping. */
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
				// skip ids that have no composition
			}
		}
		nameIndexBuilt = true;
	}

	/** Polls the Slayer plugin's task config each tick; ConfigChanged is unreliable for it. */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		// The Slayer kill log has no varps, so, like the collection log, it is read whenever it is open.
		if (killCounts.captureKillLog())
		{
			refreshView();
		}

		// Mortimer selection interface: poll it (modal interfaces don't always emit WidgetLoaded).
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
		// our own display options changed
		if (SlayerClogConfig.GROUP.equals(event.getGroup()))
		{
			refreshView();
		}
	}

	/** Rebuild the panel from current state. */
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
					final Integer kills = config.showKillCounts() ? killCounts.get(optionName) : null;
					options.add(new SlayerClogPanel.Option(optionName, sections, kills));
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
			final Integer kills = config.showKillCounts() ? killCounts.get(taskName) : null;
			updatePanel(() -> panel.showTask(taskName, amount, location, sections, kills));
		});
	}

	/** Build one display Section per monster version of the task (base monster, Superior, boss). */
	private List<SlayerClogPanel.Section> buildSections(SlayerMonster monster)
	{
		// one section per monster: groups from several pages merge, each row keeps its page
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
				final boolean synced = status != null; // null = status still unknown (page not synced)
				final boolean obtained = Boolean.TRUE.equals(status);
				final int quantity = clogManager.getQuantity(group.getPage(), itemId);
				final String rate = group.getRates().get(itemId);
				rows.add(new SlayerClogPanel.ItemRow(itemId, name, obtained, quantity, synced, rate, group.getPage()));
			}
		}
		final List<SlayerClogPanel.Section> sections = new ArrayList<>();
		for (Map.Entry<String, List<SlayerClogPanel.ItemRow>> e : byMonster.entrySet())
		{
			// the task's own kill log count is shown by the task/option name, so sections do not repeat it
			final Integer kills = config.showKillCounts() ? killCounts.getForSection(e.getKey(), monster.getName()) : null;
			// Superior section: superiors seen on this task, and the all-task total
			final boolean superior = SUPERIOR_LABEL.equals(e.getKey());
			Integer here = null;
			if (superior && config.showSuperiorTracked())
			{
				final Integer seen = killCounts.superiorsOnTask(monster.getName());
				here = seen == null ? 0 : seen;
			}
			final Integer total = superior && config.showSuperiorTotal() ? killCounts.superiorsKilled() : null;
			sections.add(new SlayerClogPanel.Section(e.getKey(), e.getValue(), kills, here, total, monster.getName()));
		}
		return sections;
	}

	/** Adds a chest entry from the mapping as extra sections, when its slayer master condition applies. */
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
