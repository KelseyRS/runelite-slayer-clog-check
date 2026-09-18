package com.slayerclog.task;

import java.util.List;
import java.util.Map;
import lombok.Value;

/** One monster version and its clog items on a single page, with drop rates where known. */
@Value
public class ItemGroup
{
	String monster;
	String page;
	List<Integer> items;
	// item id -> drop rate
	Map<Integer, String> rates;
}
