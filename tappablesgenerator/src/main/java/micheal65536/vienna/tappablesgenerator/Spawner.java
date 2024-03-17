package micheal65536.vienna.tappablesgenerator;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.eventbus.client.EventBusClient;
import micheal65536.vienna.eventbus.client.Publisher;

import java.util.HashMap;

public class Spawner
{
	private static final long SPAWN_INTERVAL = 15 * 1000;

	private final ActiveTiles activeTiles;
	private final Generator generator;
	private final Publisher publisher;

	private final int maxTappableLifetimeIntervals;

	private long spawnCycleTime;
	private int spawnCycleIndex;
	private final HashMap<Integer, Integer> lastSpawnCycleForTile = new HashMap<>();

	public Spawner(@NotNull EventBusClient eventBusClient, @NotNull ActiveTiles activeTiles, @NotNull Generator generator)
	{
		this.activeTiles = activeTiles;
		this.generator = generator;
		this.publisher = eventBusClient.addPublisher();

		this.maxTappableLifetimeIntervals = (int) (this.generator.getMaxTappableLifetime() / SPAWN_INTERVAL + 1);

		this.spawnCycleTime = System.currentTimeMillis();
		this.spawnCycleIndex = this.maxTappableLifetimeIntervals;
	}

	public void run()
	{
		long nextTime = System.currentTimeMillis() + SPAWN_INTERVAL;
		for (; ; )
		{
			try
			{
				Thread.sleep(nextTime - System.currentTimeMillis());
			}
			catch (InterruptedException exception)
			{
				LogManager.getLogger().info("Spawn thread was interrupted, exiting");
				break;
			}
			nextTime += SPAWN_INTERVAL;

			Spawner.this.doSpawnCycle();
		}
	}

	private void doSpawnCycle()
	{
		this.spawnCycleTime += SPAWN_INTERVAL;
		this.spawnCycleIndex++;

		ActiveTiles.ActiveTile[] activeTiles = this.activeTiles.getActiveTiles(this.spawnCycleTime);
		LogManager.getLogger().info("Spawning tappables for {} tiles", activeTiles.length);
		for (ActiveTiles.ActiveTile activeTile : activeTiles)
		{
			int lastSpawnCycle = this.lastSpawnCycleForTile.getOrDefault((activeTile.tileX() << 16) + activeTile.tileY(), 0);
			int cyclesToSpawn = Math.min(this.spawnCycleIndex - lastSpawnCycle, this.maxTappableLifetimeIntervals);
			for (int index = 0; index < cyclesToSpawn; index++)
			{
				this.doSpawnCycleForTile(activeTile.tileX(), activeTile.tileY(), this.spawnCycleTime - SPAWN_INTERVAL * (cyclesToSpawn - index - 1));
			}
			this.lastSpawnCycleForTile.put((activeTile.tileX() << 16) + activeTile.tileY(), this.spawnCycleIndex);
		}
	}

	private void doSpawnCycleForTile(int tileX, int tileY, long currentTime)
	{
		for (Tappable tappable : this.generator.generateTappables(tileX, tileY, currentTime))
		{
			this.publisher.publish("tappables", "tappableSpawn", new Gson().toJson(tappable)).thenAccept(success ->
			{
				if (!success)
				{
					LogManager.getLogger().error("Event bus server rejected tappable spawn event");
				}
			});
		}
	}
}