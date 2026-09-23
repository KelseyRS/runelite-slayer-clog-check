package com.slayerclog;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/** Settings. Never change a keyName: saved settings are stored under it. */
@ConfigGroup(SlayerClogConfig.GROUP)
public interface SlayerClogConfig extends Config
{
	String GROUP = "slayerclog";

	// Superior options sit under their own heading, below the general options.
	@ConfigSection(
		name = "Superiors",
		description = "Superior slayer monster drops and counts.",
		position = 10
	)
	String SUPERIOR_SECTION = "superiors";

	@ConfigItem(
		keyName = "mortimerView",
		name = "Compare Mortimer options",
		description = "Compare the tasks Mortimer offers.",
		position = 1
	)
	default boolean mortimerView()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOnlyUnobtained",
		name = "Hide obtained items",
		description = "Only list items you still need.",
		position = 2
	)
	default boolean showOnlyUnobtained()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showDropRates",
		name = "Show drop rates",
		description = "Show each item's drop rate, e.g. (1:512).",
		position = 3
	)
	default boolean showDropRates()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showQuantities",
		name = "Show logged quantities",
		description = "Show how many of each item you have logged.",
		position = 4
	)
	default boolean showQuantities()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showKillCounts",
		name = "Show kill counts",
		description = "Show your kill count for each task and boss.",
		position = 5
	)
	default boolean showKillCounts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSuperior",
		name = "Show superior drops",
		description = "Include the Superior section.",
		position = 11,
		section = SUPERIOR_SECTION
	)
	default boolean showSuperior()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSuperiorTracked",
		name = "Superiors per task",
		description = "Show how many superiors you have seen on each task.",
		position = 12,
		section = SUPERIOR_SECTION
	)
	default boolean showSuperiorTracked()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSuperiorTotal",
		name = "Superiors across all tasks",
		description = "Show your total superior kills.",
		position = 13,
		section = SUPERIOR_SECTION
	)
	default boolean showSuperiorTotal()
	{
		return true;
	}
}
