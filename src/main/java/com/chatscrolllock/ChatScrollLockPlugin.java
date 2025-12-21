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
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ChatScrollLockConfig config;

	private int savedScrollY = -1;
	private boolean isLocked = false;
	private int lastScrollY = -1;

	@Override
	protected void startUp() throws Exception
	{
		log.debug("Chat Scroll Lock started");
		savedScrollY = -1;
		isLocked = false;
		lastScrollY = -1;
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.debug("Chat Scroll Lock stopped");
		savedScrollY = -1;
		isLocked = false;
		lastScrollY = -1;
	}

	@Subscribe
	public void onGameTick(GameTick event)
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

		// Calculate if we're at the bottom (with small tolerance)
		boolean isAtBottom = scrollY >= scrollHeight - height - 5;

		// If user manually scrolled (scroll position changed and we didn't cause it)
		if (lastScrollY >= 0 && scrollY != lastScrollY)
		{
			if (isAtBottom)
			{
				// User scrolled to bottom, unlock
				if (isLocked)
				{
					isLocked = false;
					savedScrollY = -1;
					log.debug("Chat unlocked - user scrolled to bottom");
				}
			}
			else if (!isLocked)
			{
				// User scrolled up, lock at this position
				isLocked = true;
				savedScrollY = scrollY;
				log.debug("Chat locked at scrollY: {}", savedScrollY);
			}
		}

		lastScrollY = scrollY;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.enabled() || !isLocked || savedScrollY < 0)
		{
			return;
		}

		// Use invokeLater to restore scroll after the game processes the message
		clientThread.invokeLater(() ->
		{
			Widget chatbox = client.getWidget(ComponentID.CHATBOX_MESSAGE_LINES);
			if (chatbox == null)
			{
				return;
			}

			chatbox.setScrollY(savedScrollY);
			chatbox.revalidateScroll();
			lastScrollY = savedScrollY;
			log.debug("Restored scroll position to: {}", savedScrollY);
		});
	}

	@Provides
	ChatScrollLockConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ChatScrollLockConfig.class);
	}
}
