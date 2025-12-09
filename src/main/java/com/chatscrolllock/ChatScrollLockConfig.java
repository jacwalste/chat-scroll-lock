package com.chatscrolllock;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("chatscrolllock")
public interface ChatScrollLockConfig extends Config
{
	@ConfigItem(
		keyName = "enabled",
		name = "Enable Scroll Lock",
		description = "Freeze chat position when scrolled up"
	)
	default boolean enabled()
	{
		return true;
	}
}
