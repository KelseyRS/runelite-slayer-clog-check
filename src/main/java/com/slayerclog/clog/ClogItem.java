package com.slayerclog.clog;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A collection log item and whether it is obtained. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClogItem
{
	private int id;
	private boolean obtained;
	private int quantity;
}
