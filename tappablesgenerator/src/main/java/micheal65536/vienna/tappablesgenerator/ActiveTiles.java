package micheal65536.vienna.tappablesgenerator;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.eventbus.client.EventBusClient;
import micheal65536.vienna.eventbus.client.RequestHandler;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

public class ActiveTiles
{
	private static final int ACTIVE_TILE_RADIUS = 3;
	private static final long ACTIVE_TILE_EXPIRY_TIME = 2 * 60 * 1000;

	private final HashMap<Integer, ActiveTile> activeTiles = new HashMap<>();
	private final ActiveTileListener activeTileListener;

	public ActiveTiles(@NotNull EventBusClient eventBusClient, @NotNull ActiveTileListener activeTileListener)
	{
		this.activeTileListener = activeTileListener;

		eventBusClient.addRequestHandler("tappables", new RequestHandler.Handler()
		{
			@Override
			@Nullable
			public String request(@NotNull RequestHandler.Request request)
			{
				if (request.type.equals("activeTile"))
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
						activeTileNotification = new Gson().fromJson(request.data, ActiveTileNotification.class);
					}
					catch (Exception exception)
					{
						LogManager.getLogger().error("Could not deserialise active tile notification event", exception);
						return null;
					}

					long currentTime = System.currentTimeMillis();
					ActiveTiles.this.pruneActiveTiles(currentTime);
					LinkedList<ActiveTile> newActiveTiles = new LinkedList<>();
					for (int tileX = activeTileNotification.x - ACTIVE_TILE_RADIUS; tileX < activeTileNotification.x + ACTIVE_TILE_RADIUS + 1; tileX++)
					{
						for (int tileY = activeTileNotification.y - ACTIVE_TILE_RADIUS; tileY < activeTileNotification.y + ACTIVE_TILE_RADIUS + 1; tileY++)
						{
							ActiveTile activeTile = ActiveTiles.this.markTileActive(tileX, tileY, currentTime);
							if (activeTile.latestActiveTime == activeTile.firstActiveTime) // indicating that the tile is newly-active
							{
								newActiveTiles.add(activeTile);
							}
						}
					}
					if (!newActiveTiles.isEmpty())
					{
						ActiveTiles.this.activeTileListener.active(newActiveTiles.toArray(ActiveTile[]::new));
					}

					return "";
				}
				else
				{
					return null;
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

	@NotNull
	private ActiveTile markTileActive(int tileX, int tileY, long currentTime)
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
		return activeTile;
	}

	private void pruneActiveTiles(long currentTime)
	{
		LinkedList<ActiveTile> inactiveTiles = new LinkedList<>();
		for (Iterator<Map.Entry<Integer, ActiveTile>> iterator = this.activeTiles.entrySet().iterator(); iterator.hasNext(); )
		{
			Map.Entry<Integer, ActiveTile> entry = iterator.next();
			ActiveTile activeTile = entry.getValue();
			if (activeTile.latestActiveTime + ACTIVE_TILE_EXPIRY_TIME <= currentTime)
			{
				LogManager.getLogger().info("Tile {},{} is inactive", activeTile.tileX, activeTile.tileY);
				iterator.remove();
				inactiveTiles.add(activeTile);
			}
		}
		if (!inactiveTiles.isEmpty())
		{
			this.activeTileListener.inactive(inactiveTiles.toArray(ActiveTile[]::new));
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

	public interface ActiveTileListener
	{
		void active(@NotNull ActiveTile[] activeTiles);

		void inactive(@NotNull ActiveTile[] activeTiles);
	}
}