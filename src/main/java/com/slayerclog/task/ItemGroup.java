package com.slayerclog.task;

import java.util.List;
import lombok.Value;

/** One monster version and its clog items on a single page. */
@Value
public class ItemGroup
{
	String monster;
	String page;
	List<Integer> items;
}
