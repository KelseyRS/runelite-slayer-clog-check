package com.slayerclog;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(SlayerClogConfig.GROUP)
public interface SlayerClogConfig extends Config
{
	String GROUP = "slayerclog";

	@ConfigItem(
		keyName = "mortimerView",
		name = "Mortimer comparison",
		description = "When Mortimer's task-selection interface is open, compare the collection log potential of each offered task.",
		position = 1
	)
	default boolean mortimerView()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOnlyUnobtained",
		name = "Only show unobtained",
		description = "Hide items you have already collected, so you only see what's left.",
		position = 2
	)
	default boolean showOnlyUnobtained()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showSuperior",
		name = "Show superior drops",
		description = "Include the Superior section (imbued heart, eternal gem, dust and mist battlestaff) in the task and Mortimer tables.",
		position = 3
	)
	default boolean showSuperior()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showDropRates",
		name = "Show drop rates",
		description = "Show each item's drop rate in brackets under its name, for example (1:4000).",
		position = 4
	)
	default boolean showDropRates()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showQuantities",
		name = "Show quantities",
		description = "Show how many of each obtained item you have logged.",
		position = 5
	)
	default boolean showQuantities()
	{
		return true;
	}
}
