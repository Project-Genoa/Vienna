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
	private final TappableGenerator tappableGenerator;
	private final EncounterGenerator encounterGenerator;
	private final Publisher publisher;

	private final int maxTappableLifetimeIntervals;

	private long spawnCycleTime;
	private int spawnCycleIndex;
	private final HashMap<Integer, Integer> lastSpawnCycleForTile = new HashMap<>();

	public Spawner(@NotNull EventBusClient eventBusClient, @NotNull ActiveTiles activeTiles, @NotNull TappableGenerator tappableGenerator, @NotNull EncounterGenerator encounterGenerator)
	{
		this.activeTiles = activeTiles;
		this.tappableGenerator = tappableGenerator;
		this.encounterGenerator = encounterGenerator;
		this.publisher = eventBusClient.addPublisher();

		this.maxTappableLifetimeIntervals = (int) (Math.max(this.tappableGenerator.getMaxTappableLifetime(), this.encounterGenerator.getMaxEncounterLifetime()) / SPAWN_INTERVAL + 1);

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

	public void spawnTile(int tileX, int tileY)
	{
		LogManager.getLogger().info("Spawning tappables for tile {},{}", tileX, tileY);

		long spawnCycleTime = this.spawnCycleTime;
		int spawnCycleIndex = this.spawnCycleIndex;

		while (spawnCycleTime < System.currentTimeMillis())
		{
			spawnCycleTime += SPAWN_INTERVAL;
			spawnCycleIndex++;
		}

		this.doSpawnCyclesForTile(tileX, tileY, spawnCycleTime, spawnCycleIndex);
	}

	private void doSpawnCycle()
	{
		ActiveTiles.ActiveTile[] activeTiles = this.activeTiles.getActiveTiles(this.spawnCycleTime);

		LogManager.getLogger().info("Spawning tappables for {} tiles", activeTiles.length);

		while (this.spawnCycleTime < System.currentTimeMillis())
		{
			this.spawnCycleTime += SPAWN_INTERVAL;
			this.spawnCycleIndex++;
		}

		for (ActiveTiles.ActiveTile activeTile : activeTiles)
		{
			this.doSpawnCyclesForTile(activeTile.tileX(), activeTile.tileY(), this.spawnCycleTime, this.spawnCycleIndex);
		}
	}

	private void doSpawnCyclesForTile(int tileX, int tileY, long spawnCycleTime, int spawnCycleIndex)
	{
		int lastSpawnCycle = this.lastSpawnCycleForTile.getOrDefault((tileX << 16) + tileY, 0);
		int cyclesToSpawn = Math.min(spawnCycleIndex - lastSpawnCycle, this.maxTappableLifetimeIntervals);
		for (int index = 0; index < cyclesToSpawn; index++)
		{
			this.spawnTappablesForTile(tileX, tileY, spawnCycleTime - SPAWN_INTERVAL * (cyclesToSpawn - index - 1));
		}
		this.lastSpawnCycleForTile.put((tileX << 16) + tileY, spawnCycleIndex);
	}

	private void spawnTappablesForTile(int tileX, int tileY, long currentTime)
	{
		for (Tappable tappable : this.tappableGenerator.generateTappables(tileX, tileY, currentTime))
		{
			if (!this.publisher.publish("tappables", "tappableSpawn", new Gson().toJson(tappable)).join())
			{
				LogManager.getLogger().error("Event bus server rejected tappable spawn event");
			}
		}
		for (Encounter encounter : this.encounterGenerator.generateEncounters(tileX, tileY, currentTime))
		{
			if (!this.publisher.publish("tappables", "encounterSpawn", new Gson().toJson(encounter)).join())
			{
				LogManager.getLogger().error("Event bus server rejected encounter spawn event");
			}
		}
	}
}