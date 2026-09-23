package com.slayerclog.task;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.RuneLite;

/** Kill counts for tasks, options, monsters and bosses, read from the game and saved per account. */
@Slf4j
@Singleton
public class KillCountTracker
{
	/** The kill log shows "Lots!" instead of a number from this count upwards. */
	public static final int LOTS = 65535;

	/** "Your Thermonuclear smoke devil kill count is: 1,230." (tags already stripped). */
	private static final Pattern KILL_MESSAGE = Pattern.compile("Your (.+?) kill count is: *([0-9,]+)",
		Pattern.CASE_INSENSITIVE);

	/** Names counted under other kill log rows, summed (e.g. Dagannoth Kings, The Cave Kraken Boss). */
	private static final Map<String, String[]> COMBINED = new HashMap<>();

	static
	{
		COMBINED.put("barrowsbrothers", new String[]{"barrowschests"});
		COMBINED.put("cavekrakenboss", new String[]{"kraken"});
		COMBINED.put("dagannothkings", new String[]{"dagannothrex", "dagannothprime", "dagannothsupreme"});
		COMBINED.put("fightcaves", new String[]{"tztokjad"});
		COMBINED.put("inferno", new String[]{"tzkalzuk"});
	}

	private static final String KILLS_LABEL = "kills:";

	/** Sent when a superior spawns; RuneLite's own Slayer plugin watches for the same message. */
	private static final String SUPERIOR_MESSAGE = "A superior foe has appeared";

	/** The Slayer kill log's single row for superior kills across all tasks. */
	private static final String SUPERIORS_ROW = "superiorcreatures";
	private static final File BASE_DIR = new File(RuneLite.RUNELITE_DIR, "slayer-clog-check");
	private static final Type SAVE_TYPE = new TypeToken<Map<String, Integer>>()
	{
	}.getType();

	private final Client client;
	private final Gson gson;

	// key(name) -> kills, from the kill logs, collection log headers and kill count messages
	private final Map<String, Integer> counts = new HashMap<>();
	// key(task name) -> superiors that appeared while on that task.
	private final Map<String, Integer> superiors = new HashMap<>();
	private long loadedAccountHash = -1L;

	@Inject
	KillCountTracker(Client client, Gson gson)
	{
		this.client = client;
		this.gson = gson;
	}

	/** Reads the open kill log, keeping the higher count per row. */
	public boolean captureKillLog()
	{
		final Widget names = client.getWidget(InterfaceID.KillLog.NAME);
		final Widget kills = client.getWidget(InterfaceID.KillLog.KILL);
		if (names == null || kills == null || names.isHidden())
		{
			return false;
		}

		final Map<String, Integer> captured = readRows(names, kills);
		boolean changed = false;
		for (Map.Entry<String, Integer> e : captured.entrySet())
		{
			changed |= record(e.getKey(), e.getValue());
		}
		if (changed)
		{
			save();
		}
		return changed;
	}

	/** Records the "X kills: N" counts in the open collection log page's header. */
	public boolean captureCollectionLogHeader()
	{
		final Widget header = client.getWidget(ComponentID.COLLECTION_LOG_ENTRY_HEADER);
		final Widget[] lines = header == null ? null : header.getDynamicChildren();
		if (lines == null || lines.length == 0)
		{
			return false;
		}

		boolean changed = false;
		int counters = 0;
		int total = 0;
		for (Widget line : lines)
		{
			final String text = line.getText() == null ? "" : line.getText().replaceAll("<[^>]*>", "");
			final int at = text.toLowerCase().indexOf(KILLS_LABEL);
			final Integer count = at < 0 ? null : parseCount(text.substring(at + KILLS_LABEL.length()));
			if (count == null)
			{
				continue;
			}
			counters++;
			total += count;
			if (record(key(text.substring(0, at)), count))
			{
				changed = true;
			}
		}
		if (counters > 0)
		{
			changed |= record(key(lines[0].getText()), total);
		}
		if (changed)
		{
			save();
		}
		return changed;
	}

	/** Records a "Your X kill count is: N" game message. */
	public boolean recordKillMessage(String message)
	{
		if (message == null)
		{
			return false;
		}
		final Matcher m = KILL_MESSAGE.matcher(message.replaceAll("<[^>]*>", ""));
		if (!m.find())
		{
			return false;
		}
		final Integer count = parseCount(m.group(2));
		if (count == null || !record(key(m.group(1)), count))
		{
			return false;
		}
		save();
		return true;
	}

	/** If this is the superior spawn message, counts one superior against the given (current) task. */
	public boolean recordSuperior(String message, String task)
	{
		if (message == null || task == null || !message.replaceAll("<[^>]*>", "").contains(SUPERIOR_MESSAGE))
		{
			return false;
		}
		superiors.merge(key(task), 1, Integer::sum);
		save();
		return true;
	}

	/** Superiors seen on this task since tracking began (singular/plural tolerant), or null if none yet. */
	public Integer superiorsOnTask(String task)
	{
		return task == null ? null : findIn(superiors, key(task));
	}

	/** Superior slayer monsters killed on all tasks, from the Slayer kill log, or null if never opened. */
	public Integer superiorsKilled()
	{
		return counts.get(SUPERIORS_ROW);
	}

	/** Kill count for a task, Mortimer option, monster or boss name, or null when not seen yet. */
	public Integer get(String name)
	{
		for (String form : forms(name))
		{
			final Integer direct = findIn(counts, key(form));
			if (direct != null)
			{
				return direct;
			}
			final String[] rows = COMBINED.get(key(form));
			final Integer combined = rows != null ? sum(rows) : sumOfParts(form);
			if (combined != null)
			{
				return combined;
			}
		}
		return null;
	}

	/** Kill count for a monster section within a task. */
	public Integer getForSection(String monster, String task)
	{
		final String own = rowOf(monster);
		if (own != null && own.equals(rowOf(task)))
		{
			return null;
		}
		return get(monster);
	}

	/** The kill log row a name resolves to directly (so "The Kalphite Queen" and "Kalphite Queen" agree). */
	private String rowOf(String name)
	{
		for (String form : forms(name))
		{
			final String row = findKey(counts, key(form));
			if (row != null)
			{
				return row;
			}
		}
		return null;
	}

	private static List<String> forms(String name)
	{
		final List<String> forms = new ArrayList<>();
		if (name != null)
		{
			forms.add(name);
			if (name.regionMatches(true, 0, "The ", 0, 4))
			{
				forms.add(name.substring(4));
			}
		}
		return forms;
	}

	/** "Callisto and Artio" -> Callisto + Artio, when every part has a count. */
	private Integer sumOfParts(String name)
	{
		final String[] parts = name.split(" and ");
		if (parts.length < 2)
		{
			return null;
		}
		final String[] rows = new String[parts.length];
		for (int i = 0; i < parts.length; i++)
		{
			rows[i] = key(parts[i]);
		}
		return sum(rows);
	}

	/** Total of the given rows, or null unless every one is known (a partial total would under-count). */
	private Integer sum(String[] rows)
	{
		int total = 0;
		for (String row : rows)
		{
			final Integer n = findIn(counts, row);
			if (n == null)
			{
				return null;
			}
			total += n;
		}
		return total;
	}

	/** Load the saved counts for this account, replacing what is in memory. */
	public void load(long accountHash)
	{
		counts.clear();
		superiors.clear();
		loadedAccountHash = accountHash;
		if (accountHash == -1L)
		{
			return;
		}
		readInto(saveFile(accountHash, "killlog"), counts);
		readInto(saveFile(accountHash, "superiors"), superiors);
	}

	private void readInto(File file, Map<String, Integer> into)
	{
		if (!file.exists())
		{
			return;
		}
		try
		{
			final String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
			final Map<String, Integer> saved = gson.fromJson(json, SAVE_TYPE);
			if (saved != null)
			{
				into.putAll(saved);
			}
		}
		catch (Exception e)
		{
			log.warn("Failed to load {}", file.getName(), e);
		}
	}

	/** Keeps the higher of the stored and new count; returns true if the stored count went up. */
	boolean record(String key, int count)
	{
		final Integer old = counts.get(key);
		if (key.isEmpty() || (old != null && old >= count))
		{
			return false;
		}
		counts.put(key, count);
		return true;
	}

	private void save()
	{
		if (loadedAccountHash == -1L)
		{
			return;
		}
		try
		{
			//noinspection ResultOfMethodCallIgnored
			BASE_DIR.mkdirs();
			Files.write(saveFile(loadedAccountHash, "killlog").toPath(),
				gson.toJson(counts).getBytes(StandardCharsets.UTF_8));
			Files.write(saveFile(loadedAccountHash, "superiors").toPath(),
				gson.toJson(superiors).getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			log.warn("Failed to save kill counts", e);
		}
	}

	private static File saveFile(long accountHash, String kind)
	{
		return new File(BASE_DIR, accountHash + "-" + kind + ".json");
	}

	/** Pairs the name and kill columns row by row. */
	private static Map<String, Integer> readRows(Widget names, Widget kills)
	{
		final Map<String, Integer> rows = new HashMap<>();
		final Widget[] nameCells = names.getDynamicChildren();
		final Widget[] killCells = kills.getDynamicChildren();

		if (nameCells != null && nameCells.length > 0 && killCells != null && killCells.length > 0)
		{
			if (nameCells.length == killCells.length)
			{
				for (int i = 0; i < nameCells.length; i++)
				{
					put(rows, nameCells[i].getText(), killCells[i].getText());
				}
			}
			else
			{
				final Map<Integer, String> killByY = new HashMap<>();
				for (Widget cell : killCells)
				{
					killByY.put(cell.getRelativeY(), cell.getText());
				}
				for (Widget cell : nameCells)
				{
					put(rows, cell.getText(), killByY.get(cell.getRelativeY()));
				}
			}
		}
		else
		{
			final List<String> nameLines = lines(names.getText());
			final List<String> killLines = lines(kills.getText());
			for (int i = 0; i < Math.min(nameLines.size(), killLines.size()); i++)
			{
				put(rows, nameLines.get(i), killLines.get(i));
			}
		}
		return rows;
	}

	private static List<String> lines(String text)
	{
		final List<String> out = new ArrayList<>();
		if (text != null)
		{
			for (String line : text.split("<br>"))
			{
				out.add(line);
			}
		}
		return out;
	}

	private static void put(Map<String, Integer> rows, String name, String kills)
	{
		final String key = key(name);
		final Integer count = parseCount(kills);
		if (!key.isEmpty() && count != null)
		{
			rows.put(key, count);
		}
	}

	/** "1,234" -> 1234, "Lots!" -> LOTS, colour tags ignored; null for anything else. */
	static Integer parseCount(String text)
	{
		if (text == null)
		{
			return null;
		}
		final String t = text.replaceAll("<[^>]*>", "").replace(",", "").trim();
		if (t.toLowerCase().startsWith("lots"))
		{
			return LOTS;
		}
		try
		{
			return Integer.parseInt(t);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/** Lookup key: case, spacing, punctuation and tags ignored, as for collection log page titles. */
	static String key(String name)
	{
		return name == null ? "" : name.replaceAll("<[^>]*>", "").toLowerCase().replaceAll("[^a-z0-9]", "");
	}

	/** The count for a key, allowing for singular/plural differences. */
	static Integer findIn(Map<String, Integer> counts, String key)
	{
		final String found = findKey(counts, key);
		return found == null ? null : counts.get(found);
	}

	/** The stored key a name matches, allowing singular/plural differences, or null. */
	static String findKey(Map<String, Integer> counts, String key)
	{
		if (key.isEmpty())
		{
			return null;
		}
		final List<String> forms = new ArrayList<>();
		forms.add(key);
		forms.add(key + "s");
		forms.add(key + "es");
		if (key.endsWith("y"))
		{
			forms.add(key.substring(0, key.length() - 1) + "ies");
		}
		if (key.endsWith("ies"))
		{
			forms.add(key.substring(0, key.length() - 3) + "y");
		}
		if (key.endsWith("s"))
		{
			forms.add(key.substring(0, key.length() - 1));
		}
		for (String form : forms)
		{
			if (counts.containsKey(form))
			{
				return form;
			}
		}
		return null;
	}
}
