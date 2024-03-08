package micheal65536.minecraftearth.tappablesgenerator;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import micheal65536.minecraftearth.eventbus.client.EventBusClient;
import micheal65536.minecraftearth.eventbus.client.Subscriber;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ActiveTiles
{
	private static final int ACTIVE_TILE_RADIUS = 3;
	private static final long ACTIVE_TILE_EXPIRY_TIME = 2 * 60 * 1000;

	private final HashMap<Integer, ActiveTile> activeTiles = new HashMap<>();

	public ActiveTiles(@NotNull EventBusClient eventBusClient)
	{
		eventBusClient.addSubscriber("tappables", new Subscriber.SubscriberListener()
		{
			@Override
			public void event(@NotNull Subscriber.Event event)
			{
				if (event.type.equals("activeTile"))
				{
					record ActiveTileNotification(
							int x,
							int y,
							@NotNull String playerId
					)
					{
					}

					ActiveTileNotification activeTileNotification;
					try
					{
						activeTileNotification = new Gson().fromJson(event.data, ActiveTileNotification.class);
					}
					catch (Exception exception)
					{
						LogManager.getLogger().error("Could not deserialise active tile notification event", exception);
						return;
					}

					long currentTime = System.currentTimeMillis();
					ActiveTiles.this.pruneActiveTiles(currentTime);
					for (int tileX = activeTileNotification.x - ACTIVE_TILE_RADIUS; tileX < activeTileNotification.x + ACTIVE_TILE_RADIUS + 1; tileX++)
					{
						for (int tileY = activeTileNotification.y - ACTIVE_TILE_RADIUS; tileY < activeTileNotification.y + ACTIVE_TILE_RADIUS + 1; tileY++)
						{
							ActiveTiles.this.markTileActive(tileX, tileY, currentTime);
						}
					}
				}
			}

			@Override
			public void error()
			{
				LogManager.getLogger().error("Event bus subscriber error");
				System.exit(1);
			}
		});
	}

	@NotNull
	public ActiveTile[] getActiveTiles(long currentTime)
	{
		return this.activeTiles.values().stream().filter(activeTile -> currentTime < activeTile.latestActiveTime + ACTIVE_TILE_EXPIRY_TIME).toArray(ActiveTile[]::new);
	}

	private void markTileActive(int tileX, int tileY, long currentTime)
	{
		ActiveTile activeTile = this.activeTiles.getOrDefault((tileX << 16) + tileY, null);
		if (activeTile == null)
		{
			LogManager.getLogger().info("Tile {},{} is becoming active", tileX, tileY);
			activeTile = new ActiveTile(tileX, tileY, currentTime, currentTime);
		}
		else
		{
			activeTile = new ActiveTile(tileX, tileY, activeTile.firstActiveTime, currentTime);
		}
		this.activeTiles.put((tileX << 16) + tileY, activeTile);
	}

	private void pruneActiveTiles(long currentTime)
	{
		for (Iterator<Map.Entry<Integer, ActiveTile>> iterator = this.activeTiles.entrySet().iterator(); iterator.hasNext(); )
		{
			Map.Entry<Integer, ActiveTile> entry = iterator.next();
			if (entry.getValue().latestActiveTime + ACTIVE_TILE_EXPIRY_TIME <= currentTime)
			{
				LogManager.getLogger().info("Tile {},{} is inactive", entry.getValue().tileX, entry.getValue().tileY);
				iterator.remove();
			}
		}
	}

	public record ActiveTile(
			int tileX,
			int tileY,
			long firstActiveTime,
			long latestActiveTime
	)
	{
	}
}