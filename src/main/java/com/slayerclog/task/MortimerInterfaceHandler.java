package com.slayerclog.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

/** Reads the task options from Mortimer's "Slayer Task Choice" interface. */
@Singleton
public class MortimerInterfaceHandler
{
	/** "Slayer Task Choice" interface group id. */
	public static final int MORTIMER_GROUP_ID = 236;

	private final Client client;

	@Inject
	private MortimerInterfaceHandler(Client client)
	{
		this.client = client;
	}

	/** Offered monster names, or empty if the interface is not open. Client thread only. */
	public List<String> detect(int loadedGroupId)
	{
		if (loadedGroupId != MORTIMER_GROUP_ID)
		{
			return Collections.emptyList();
		}

		final List<String> names = new ArrayList<>();
		final Set<String> seen = new HashSet<>();

		for (int child = 0; child <= 5; child++)
		{
			collect(client.getWidget(loadedGroupId, child), names, seen);
		}

		return names;
	}

	private void collect(Widget widget, List<String> names, Set<String> seen)
	{
		if (widget == null)
		{
			return;
		}

		final String text = widget.getText();
		// Option names are the underlined links.
		if (text != null && text.contains("<u="))
		{
			final String name = text.replaceAll("<[^>]*>", "").trim();
			if (!name.isEmpty() && seen.add(name.toLowerCase()))
			{
				names.add(name);
			}
		}

		collectAll(widget.getStaticChildren(), names, seen);
		collectAll(widget.getDynamicChildren(), names, seen);
		collectAll(widget.getNestedChildren(), names, seen);
	}

	private void collectAll(Widget[] children, List<String> names, Set<String> seen)
	{
		if (children == null)
		{
			return;
		}
		for (Widget child : children)
		{
			collect(child, names, seen);
		}
	}
}
