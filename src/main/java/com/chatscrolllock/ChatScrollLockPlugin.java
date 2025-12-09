package com.chatscrolllock;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.events.ChatMessage;
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
	@Inject
	private Client client;

	@Inject
	private ChatScrollLockConfig config;

	private int savedScrollY = -1;
	private boolean isLocked = false;

	@Override
	protected void startUp() throws Exception
	{
		log.debug("Chat Scroll Lock started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.debug("Chat Scroll Lock stopped");
		savedScrollY = -1;
		isLocked = false;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.enabled())
		{
			return;
		}

		Widget chatbox = client.getWidget(ComponentID.CHATBOX_MESSAGE_LINES);
		if (chatbox == null)
		{
			return;
		}

		int scrollY = chatbox.getScrollY();
		int scrollHeight = chatbox.getScrollHeight();
		int height = chatbox.getHeight();

		// Check if user is scrolled up (not at the bottom)
		boolean isAtBottom = scrollY >= scrollHeight - height;

		if (!isAtBottom && !isLocked)
		{
			// User just scrolled up, lock the position
			savedScrollY = scrollY;
			isLocked = true;
			log.debug("Chat locked at scrollY: {}", savedScrollY);
		}
		else if (isLocked && savedScrollY >= 0)
		{
			// Restore the scroll position after new message
			chatbox.setScrollY(savedScrollY);
			chatbox.revalidateScroll();
			log.debug("Restored scroll position to: {}", savedScrollY);
		}

		// If user scrolls back to bottom, unlock
		if (isAtBottom && isLocked)
		{
			isLocked = false;
			savedScrollY = -1;
			log.debug("Chat unlocked - returned to bottom");
		}
	}

	@Provides
	ChatScrollLockConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ChatScrollLockConfig.class);
	}
}
