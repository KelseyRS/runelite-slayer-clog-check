package com.slayerclog.task;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/** Loads the bundled task -> collection log item mapping. */
@Slf4j
@Singleton
public class MonsterMapping
{
	private static final String RESOURCE = "/com/slayerclog/slayer_monster_items.json";

	/** JSON shape of a section. */
	private static final class RawGroup
	{
		String monster;
		String page;
		List<Integer> items;
	}

	private final Gson gson;

	private final Map<String, SlayerMonster> byName = new HashMap<>();

	@Inject
	MonsterMapping(Gson gson)
	{
		this.gson = gson;
		load();
	}

	private void load()
	{
		final Type outerType = new TypeToken<Map<String, JsonElement>>()
		{
		}.getType();
		final Type groupsType = new TypeToken<List<RawGroup>>()
		{
		}.getType();

		try (InputStream in = MonsterMapping.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				log.error("Could not find monster mapping resource {}", RESOURCE);
				return;
			}
			final Map<String, JsonElement> raw = gson.fromJson(
				new InputStreamReader(in, StandardCharsets.UTF_8), outerType);
			if (raw == null)
			{
				return;
			}
			for (Map.Entry<String, JsonElement> e : raw.entrySet())
			{
				final String name = e.getKey();
				// Skip documentation keys.
				if (name.startsWith("_") || e.getValue() == null || !e.getValue().isJsonArray())
				{
					continue;
				}
				final List<RawGroup> rawGroups = gson.fromJson(e.getValue(), groupsType);
				final List<ItemGroup> groups = new ArrayList<>();
				for (RawGroup g : rawGroups)
				{
					if (g == null || g.items == null || g.items.isEmpty())
					{
						continue;
					}
					final String page = g.page == null ? "Slayer" : g.page;
					final String monster = g.monster == null ? name : g.monster;
					groups.add(new ItemGroup(monster, page,
						Collections.unmodifiableList(new ArrayList<>(g.items))));
				}
				if (!groups.isEmpty())
				{
					byName.put(normalize(name),
						new SlayerMonster(name, Collections.unmodifiableList(groups)));
				}
			}
		}
		catch (Exception ex)
		{
			log.error("Failed to load monster mapping", ex);
		}
	}

	/** All item ids in the mapping. */
	public Set<Integer> allItemIds()
	{
		final Set<Integer> ids = new HashSet<>();
		for (SlayerMonster m : byName.values())
		{
			for (ItemGroup g : m.getGroups())
			{
				ids.addAll(g.getItems());
			}
		}
		return ids;
	}

	/** Lookup by task name. */
	public SlayerMonster get(String name)
	{
		if (name == null)
		{
			return null;
		}
		return byName.get(normalize(name));
	}

	/** Lookup for free text, falling back to a contains match. */
	public SlayerMonster match(String text)
	{
		if (text == null || text.isEmpty())
		{
			return null;
		}
		final String norm = normalize(text);
		final SlayerMonster exact = byName.get(norm);
		if (exact != null)
		{
			return exact;
		}
		for (Map.Entry<String, SlayerMonster> e : byName.entrySet())
		{
			if (norm.contains(e.getKey()))
			{
				return e.getValue();
			}
		}
		return null;
	}

	static String normalize(String s)
	{
		return s.toLowerCase().trim();
	}
}
