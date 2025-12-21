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
	private static final int BOTTOM_THRESHOLD = 10;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ChatScrollLockConfig config;

	// The scroll position to maintain when locked
	private int lockedScrollY = -1;

	// Track if we're currently locked (user scrolled up)
	private boolean isLocked = false;

	// Flag to ignore scroll changes we caused
	private boolean ignoringScrollChange = false;

	// Track scroll position from before any message arrives
	private int preMessageScrollY = -1;
	private boolean wasAtBottom = true;

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
		lockedScrollY = -1;
		isLocked = false;
		ignoringScrollChange = false;
		preMessageScrollY = -1;
		wasAtBottom = true;
	}

	private boolean isAtBottom(Widget chatbox)
	{
		int scrollY = chatbox.getScrollY();
		int scrollHeight = chatbox.getScrollHeight();
		int height = chatbox.getHeight();
		return scrollY >= scrollHeight - height - BOTTOM_THRESHOLD;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!config.enabled())
		{
			reset();
			return;
		}

		Widget chatbox = client.getWidget(ComponentID.CHATBOX_MESSAGE_LINES);
		if (chatbox == null)
		{
			return;
		}

		int currentScrollY = chatbox.getScrollY();
		boolean currentlyAtBottom = isAtBottom(chatbox);

		// If we're ignoring this tick (because we just set scroll), skip logic
		if (ignoringScrollChange)
		{
			ignoringScrollChange = false;
			preMessageScrollY = currentScrollY;
			wasAtBottom = currentlyAtBottom;
			return;
		}

		// Check if user scrolled to bottom - unlock
		if (currentlyAtBottom && isLocked)
		{
			isLocked = false;
			lockedScrollY = -1;
			log.debug("Unlocked - user at bottom");
		}
		// Check if user scrolled up from bottom - lock
		else if (!currentlyAtBottom && wasAtBottom && !isLocked)
		{
			isLocked = true;
			lockedScrollY = currentScrollY;
			log.debug("Locked at scrollY: {}", lockedScrollY);
		}
		// User scrolled while locked - update lock position
		else if (isLocked && !currentlyAtBottom && currentScrollY != preMessageScrollY)
		{
			lockedScrollY = currentScrollY;
			log.debug("Updated lock to scrollY: {}", lockedScrollY);
		}

		// Save state for next tick
		preMessageScrollY = currentScrollY;
		wasAtBottom = currentlyAtBottom;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.enabled() || !isLocked || lockedScrollY < 0)
		{
			return;
		}

		// Restore scroll position after game processes the message
		clientThread.invokeLater(() ->
		{
			Widget chatbox = client.getWidget(ComponentID.CHATBOX_MESSAGE_LINES);
			if (chatbox == null)
			{
				return;
			}

			// Set flag so GameTick knows to ignore this change
			ignoringScrollChange = true;

			chatbox.setScrollY(lockedScrollY);
			chatbox.revalidateScroll();
			log.debug("Restored to scrollY: {}", lockedScrollY);
		});
	}

	@Provides
	ChatScrollLockConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ChatScrollLockConfig.class);
	}
}
