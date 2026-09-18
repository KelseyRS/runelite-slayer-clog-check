package com.slayerclog.clog;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.RuneLite;

/**
 * Tracks which collection log items are obtained, from opened log pages and new-item messages.
 * State is persisted per account.
 */
@Slf4j
@Singleton
public class CollectionLogManager
{
	private static final File BASE_DIR = new File(RuneLite.RUNELITE_DIR, "slayer-clog-check");

	private final Client client;
	private final Gson gson;

	// page title -> (item id -> item)
	private final Map<String, Map<Integer, ClogItem>> pages = new HashMap<>();
	// every item id known to be obtained
	private final Set<Integer> obtainedItems = new HashSet<>();

	private long loadedAccountHash = -1L;

	/** On-disk shape. */
	private static final class SaveData
	{
		Map<String, List<ClogItem>> pages;
		List<Integer> obtained;
	}

	@Inject
	private CollectionLogManager(Client client, Gson gson)
	{
		this.client = client;
		this.gson = gson;
	}

	/** Read the open collection log page. Client thread only. */
	public void captureOpenPage()
	{
		final Widget header = client.getWidget(ComponentID.COLLECTION_LOG_ENTRY_HEADER);
		final Widget itemsContainer = client.getWidget(ComponentID.COLLECTION_LOG_ENTRY_ITEMS);
		if (header == null || itemsContainer == null)
		{
			return;
		}

		final Widget[] headerChildren = header.getDynamicChildren();
		if (headerChildren == null || headerChildren.length == 0)
		{
			return;
		}

		final String pageTitle = headerChildren[0].getText();
		if (pageTitle == null || pageTitle.isEmpty())
		{
			return;
		}

		final Map<Integer, ClogItem> pageItems = new HashMap<>();
		final Widget[] itemWidgets = itemsContainer.getDynamicChildren();
		if (itemWidgets != null)
		{
			for (Widget w : itemWidgets)
			{
				final int itemId = w.getItemId();
				if (itemId <= 0)
				{
					continue;
				}
				// Opacity 0 means obtained.
				final boolean obtained = w.getOpacity() == 0;
				final int quantity = obtained ? w.getItemQuantity() : 0;
				pageItems.put(itemId, new ClogItem(itemId, obtained, quantity));
				if (obtained)
				{
					obtainedItems.add(itemId);
				}
			}
		}

		if (pageItems.isEmpty())
		{
			return;
		}

		pages.put(pageKey(pageTitle), pageItems);
		save();
	}

	/** Record an item as obtained. */
	public void markObtained(int itemId)
	{
		if (itemId > 0 && obtainedItems.add(itemId))
		{
			save();
		}
	}

	/** TRUE obtained, FALSE not obtained, null if unknown. */
	public Boolean isObtained(String pageTitle, int itemId)
	{
		if (obtainedItems.contains(itemId))
		{
			return Boolean.TRUE;
		}
		final Map<Integer, ClogItem> page = pages.get(pageKey(pageTitle));
		if (page == null)
		{
			return null;
		}
		final ClogItem item = page.get(itemId);
		return item != null && item.isObtained();
	}

	/** Logged quantity, or 0. */
	public int getQuantity(String pageTitle, int itemId)
	{
		final Map<Integer, ClogItem> page = pages.get(pageKey(pageTitle));
		if (page == null)
		{
			return 0;
		}
		final ClogItem item = page.get(itemId);
		return item == null ? 0 : item.getQuantity();
	}

	/** Load saved state for an account. */
	public void load(long accountHash)
	{
		pages.clear();
		obtainedItems.clear();
		loadedAccountHash = accountHash;
		if (accountHash == -1L)
		{
			return;
		}

		final File file = saveFile(accountHash);
		if (!file.exists())
		{
			return;
		}

		try
		{
			final String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
			final JsonObject root = new JsonParser().parse(json).getAsJsonObject();
			final SaveData data;
			if (root.has("pages") || root.has("obtained"))
			{
				data = gson.fromJson(root, SaveData.class);
			}
			else
			{
				// Legacy format: the object was the pages map.
				data = new SaveData();
				data.pages = gson.fromJson(root,
					new com.google.gson.reflect.TypeToken<Map<String, List<ClogItem>>>()
					{
					}.getType());
			}
			if (data != null && data.pages != null)
			{
				for (Map.Entry<String, List<ClogItem>> entry : data.pages.entrySet())
				{
					final Map<Integer, ClogItem> page = new HashMap<>();
					for (ClogItem item : entry.getValue())
					{
						page.put(item.getId(), item);
						if (item.isObtained())
						{
							obtainedItems.add(item.getId());
						}
					}
					pages.put(pageKey(entry.getKey()), page);
				}
			}
			if (data != null && data.obtained != null)
			{
				obtainedItems.addAll(data.obtained);
			}
		}
		catch (Exception e)
		{
			log.warn("Failed to load collection log save file", e);
		}
	}

	private void save()
	{
		if (loadedAccountHash == -1L)
		{
			return;
		}

		final SaveData data = new SaveData();
		data.pages = new HashMap<>();
		for (Map.Entry<String, Map<Integer, ClogItem>> entry : pages.entrySet())
		{
			data.pages.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
		}
		data.obtained = new ArrayList<>(obtainedItems);

		try
		{
			//noinspection ResultOfMethodCallIgnored
			BASE_DIR.mkdirs();
			Files.write(saveFile(loadedAccountHash).toPath(),
				gson.toJson(data).getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			log.warn("Failed to save collection log data", e);
		}
	}

	/** Page lookup key: ignores case, spacing and punctuation. */
	static String pageKey(String title)
	{
		return title == null ? "" : title.replaceAll("<[^>]*>", "").toLowerCase().replaceAll("[^a-z0-9]", "");
	}

	private static File saveFile(long accountHash)
	{
		return new File(BASE_DIR, accountHash + ".json");
	}
}
