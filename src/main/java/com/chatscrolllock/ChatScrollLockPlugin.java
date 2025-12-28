package com.chatscrolllock;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.ScriptID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Chat Scroll Lock",
	description = "Freezes chat scroll position when scrolled up, preventing new messages from pushing you down",
	tags = {"chat", "scroll", "freeze", "lock", "qol"}
)
public class ChatScrollLockPlugin extends Plugin
{
	private static final int BOTTOM_THRESHOLD = 15;

	@Inject
	private Client client;

	@Inject
	private ChatScrollLockConfig config;

	// Track distance from bottom when locked (more stable than absolute scrollY)
	private int lockedDistanceFromBottom = -1;
	private boolean isLocked = false;

	@Override
	protected void startUp() throws Exception
	{
		log.debug("Chat Scroll Lock started");
		reset();
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.debug("Chat Scroll Lock stopped");
		reset();
	}

	private void reset()
	{
		lockedDistanceFromBottom = -1;
		isLocked = false;
	}

	private int getDistanceFromBottom(Widget chatbox)
	{
		int scrollY = chatbox.getScrollY();
		int scrollHeight = chatbox.getScrollHeight();
		int height = chatbox.getHeight();
		// Distance from bottom = how far up from the bottom we are
		return scrollHeight - height - scrollY;
	}

	private boolean isAtBottom(Widget chatbox)
	{
		return getDistanceFromBottom(chatbox) <= BOTTOM_THRESHOLD;
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (!config.enabled())
		{
			return;
		}

		// BUILD_CHATBOX is the script that runs when chat is updated
		if (event.getScriptId() != ScriptID.BUILD_CHATBOX)
		{
			return;
		}

		Widget chatbox = client.getWidget(ComponentID.CHATBOX_MESSAGE_LINES);
		if (chatbox == null)
		{
			return;
		}

		int distanceFromBottom = getDistanceFromBottom(chatbox);
		boolean atBottom = distanceFromBottom <= BOTTOM_THRESHOLD;

		if (isLocked)
		{
			if (atBottom)
			{
				// User scrolled to bottom, unlock
				isLocked = false;
				lockedDistanceFromBottom = -1;
				log.debug("Unlocked - at bottom");
			}
			else
			{
				// Restore position by setting scrollY based on locked distance from bottom
				int scrollHeight = chatbox.getScrollHeight();
				int height = chatbox.getHeight();
				int targetScrollY = scrollHeight - height - lockedDistanceFromBottom;

				if (targetScrollY >= 0)
				{
					chatbox.setScrollY(targetScrollY);
					log.debug("Restored to distance {} (scrollY: {})", lockedDistanceFromBottom, targetScrollY);
				}
			}
		}
		else
		{
			if (!atBottom)
			{
				// User scrolled up, lock at current position
				isLocked = true;
				lockedDistanceFromBottom = distanceFromBottom;
				log.debug("Locked at distance from bottom: {}", lockedDistanceFromBottom);
			}
		}
	}

	@Provides
	ChatScrollLockConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ChatScrollLockConfig.class);
	}
}
