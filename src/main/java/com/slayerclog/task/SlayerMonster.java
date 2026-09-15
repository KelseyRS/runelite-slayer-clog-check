package com.slayerclog.task;

import java.util.List;
import lombok.Value;

/** A slayer task and its per-monster clog sections. */
@Value
public class SlayerMonster
{
	String name;
	List<ItemGroup> groups;
}
