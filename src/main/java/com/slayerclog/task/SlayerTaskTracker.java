package com.slayerclog.task;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/** Reads the current slayer task from the Slayer plugin's per-profile config. */
@Singleton
public class SlayerTaskTracker
{
	public static final String SLAYER_CONFIG_GROUP = "slayer";
	public static final String TASK_NAME_KEY = "taskName";
	public static final String AMOUNT_KEY = "amount";
	public static final String TASK_LOC_KEY = "taskLocation";

	private final ConfigManager configManager;

	@Inject
	private SlayerTaskTracker(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/** Current task name, or null if none. */
	public String getCurrentTaskName()
	{
		final String name = configManager.getRSProfileConfiguration(SLAYER_CONFIG_GROUP, TASK_NAME_KEY);
		if (name == null || name.isEmpty() || "None".equalsIgnoreCase(name))
		{
			return null;
		}
		return name;
	}

	/** Remaining kill count. */
	public int getAmount()
	{
		final String amount = configManager.getRSProfileConfiguration(SLAYER_CONFIG_GROUP, AMOUNT_KEY);
		if (amount == null)
		{
			return 0;
		}
		try
		{
			return Integer.parseInt(amount);
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	/** Assigned area, or null. Only Konar sets one. */
	public String getTaskLocation()
	{
		final String location = configManager.getRSProfileConfiguration(SLAYER_CONFIG_GROUP, TASK_LOC_KEY);
		return location == null || location.isEmpty() ? null : location;
	}
}
