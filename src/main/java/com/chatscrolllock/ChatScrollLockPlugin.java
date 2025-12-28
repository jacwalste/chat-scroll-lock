package com.chatscrolllock;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
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
	private ClientThread clientThread;

	@Inject
	private ChatScrollLockConfig config;

	// Track distance from bottom (stable across scrollHeight changes)
	private int lockedDistanceFromBottom = -1;
	private boolean isLocked = false;

	// Debounce restoration
	private int ticksSinceLastRestore = 0;
	private static final int RESTORE_COOLDOWN = 1;

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
		ticksSinceLastRestore = 0;
	}

	private int getDistanceFromBottom(Widget chatbox)
	{
		int scrollY = chatbox.getScrollY();
		int scrollHeight = chatbox.getScrollHeight();
		int height = chatbox.getHeight();
		return Math.max(0, scrollHeight - height - scrollY);
	}

	private boolean isAtBottom(Widget chatbox)
	{
		return getDistanceFromBottom(chatbox) <= BOTTOM_THRESHOLD;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!config.enabled())
		{
			reset();
			return;
		}

		ticksSinceLastRestore++;

		Widget chatbox = client.getWidget(ComponentID.CHATBOX_MESSAGE_LINES);
		if (chatbox == null)
		{
			return;
		}

		int distanceFromBottom = getDistanceFromBottom(chatbox);
		boolean atBottom = isAtBottom(chatbox);

		// Detect user scroll
		if (isLocked && atBottom)
		{
			// User scrolled to bottom - unlock
			isLocked = false;
			lockedDistanceFromBottom = -1;
			log.debug("Unlocked - user at bottom");
		}
		else if (!isLocked && !atBottom)
		{
			// User scrolled up from bottom - lock
			isLocked = true;
			lockedDistanceFromBottom = distanceFromBottom;
			log.debug("Locked at distance: {}", lockedDistanceFromBottom);
		}
		else if (isLocked && ticksSinceLastRestore > RESTORE_COOLDOWN)
		{
			// While locked, update if user scrolled to different position
			if (Math.abs(distanceFromBottom - lockedDistanceFromBottom) > BOTTOM_THRESHOLD)
			{
				lockedDistanceFromBottom = distanceFromBottom;
				log.debug("Updated lock to distance: {}", lockedDistanceFromBottom);
			}
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.enabled() || !isLocked || lockedDistanceFromBottom < 0)
		{
			return;
		}

		// Delay restoration slightly to let the game finish updating
		clientThread.invokeLater(() ->
		{
			Widget chatbox = client.getWidget(ComponentID.CHATBOX_MESSAGE_LINES);
			if (chatbox == null || !isLocked)
			{
				return;
			}

			int scrollHeight = chatbox.getScrollHeight();
			int height = chatbox.getHeight();
			int targetScrollY = scrollHeight - height - lockedDistanceFromBottom;

			if (targetScrollY >= 0 && targetScrollY != chatbox.getScrollY())
			{
				chatbox.setScrollY(targetScrollY);
				ticksSinceLastRestore = 0;
				log.debug("Restored: distance={}, scrollY={}", lockedDistanceFromBottom, targetScrollY);
			}
		});
	}

	@Provides
	ChatScrollLockConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ChatScrollLockConfig.class);
	}
}
