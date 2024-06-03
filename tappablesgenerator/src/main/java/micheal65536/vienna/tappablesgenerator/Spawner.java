package micheal65536.vienna.tappablesgenerator;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.eventbus.client.EventBusClient;
import micheal65536.vienna.eventbus.client.Publisher;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

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

	@Deprecated
	public void spawnTile(int tileX, int tileY)
	{
		long spawnCycleTime = this.spawnCycleTime;
		int spawnCycleIndex = this.spawnCycleIndex;

		while (spawnCycleTime < System.currentTimeMillis())
		{
			spawnCycleTime += SPAWN_INTERVAL;
			spawnCycleIndex++;
		}

		LinkedList<Tappable> tappables = new LinkedList<>();
		LinkedList<Encounter> encounters = new LinkedList<>();
		this.doSpawnCyclesForTile(tileX, tileY, spawnCycleTime, spawnCycleIndex, tappables, encounters);

		long tappableCutoffTime = spawnCycleTime - SPAWN_INTERVAL;
		tappables.removeIf(tappable -> tappable.spawnTime() + tappable.validFor() < tappableCutoffTime);
		encounters.removeIf(encounter -> encounter.spawnTime() + encounter.validFor() < tappableCutoffTime);

		this.sendSpawnedTappables(tappables, encounters);
	}

	public void spawnTiles(@NotNull ActiveTiles.ActiveTile[] activeTiles)
	{
		long spawnCycleTime = this.spawnCycleTime;
		int spawnCycleIndex = this.spawnCycleIndex;

		while (spawnCycleTime < System.currentTimeMillis())
		{
			spawnCycleTime += SPAWN_INTERVAL;
			spawnCycleIndex++;
		}

		LinkedList<Tappable> tappables = new LinkedList<>();
		LinkedList<Encounter> encounters = new LinkedList<>();
		for (ActiveTiles.ActiveTile activeTile : activeTiles)
		{
			this.doSpawnCyclesForTile(activeTile.tileX(), activeTile.tileY(), spawnCycleTime, spawnCycleIndex, tappables, encounters);
		}

		long tappableCutoffTime = spawnCycleTime - SPAWN_INTERVAL;
		tappables.removeIf(tappable -> tappable.spawnTime() + tappable.validFor() < tappableCutoffTime);
		encounters.removeIf(encounter -> encounter.spawnTime() + encounter.validFor() < tappableCutoffTime);

		this.sendSpawnedTappables(tappables, encounters);
	}

	private void doSpawnCycle()
	{
		ActiveTiles.ActiveTile[] activeTiles = this.activeTiles.getActiveTiles(this.spawnCycleTime);

		while (this.spawnCycleTime < System.currentTimeMillis())
		{
			this.spawnCycleTime += SPAWN_INTERVAL;
			this.spawnCycleIndex++;
		}

		LinkedList<Tappable> tappables = new LinkedList<>();
		LinkedList<Encounter> encounters = new LinkedList<>();
		for (ActiveTiles.ActiveTile activeTile : activeTiles)
		{
			this.doSpawnCyclesForTile(activeTile.tileX(), activeTile.tileY(), this.spawnCycleTime, this.spawnCycleIndex, tappables, encounters);
		}

		long tappableCutoffTime = this.spawnCycleTime - SPAWN_INTERVAL;
		tappables.removeIf(tappable -> tappable.spawnTime() + tappable.validFor() < tappableCutoffTime);
		encounters.removeIf(encounter -> encounter.spawnTime() + encounter.validFor() < tappableCutoffTime);

		this.sendSpawnedTappables(tappables, encounters);
	}

	private void doSpawnCyclesForTile(int tileX, int tileY, long spawnCycleTime, int spawnCycleIndex, @NotNull LinkedList<Tappable> tappables, @NotNull LinkedList<Encounter> encounters)
	{
		int lastSpawnCycle = this.lastSpawnCycleForTile.getOrDefault((tileX << 16) + tileY, 0);
		int cyclesToSpawn = Math.min(spawnCycleIndex - lastSpawnCycle, this.maxTappableLifetimeIntervals);
		for (int index = 0; index < cyclesToSpawn; index++)
		{
			this.spawnTappablesForTile(tileX, tileY, spawnCycleTime - SPAWN_INTERVAL * (cyclesToSpawn - index - 1), tappables, encounters);
		}
		this.lastSpawnCycleForTile.put((tileX << 16) + tileY, spawnCycleIndex);
	}

	private void spawnTappablesForTile(int tileX, int tileY, long currentTime, @NotNull LinkedList<Tappable> tappables, @NotNull LinkedList<Encounter> encounters)
	{
		tappables.addAll(Arrays.asList(this.tappableGenerator.generateTappables(tileX, tileY, currentTime)));
		encounters.addAll(Arrays.asList(this.encounterGenerator.generateEncounters(tileX, tileY, currentTime)));
	}

	private void sendSpawnedTappables(@NotNull LinkedList<Tappable> tappables, @NotNull LinkedList<Encounter> encounters)
	{
		if (!this.publisher.publish("tappables", "tappableSpawn", new Gson().toJson(tappables.toArray(Tappable[]::new))).join())
		{
			LogManager.getLogger().error("Event bus server rejected tappable spawn event");
		}
		if (!this.publisher.publish("tappables", "encounterSpawn", new Gson().toJson(encounters.toArray(Encounter[]::new))).join())
		{
			LogManager.getLogger().error("Event bus server rejected encounter spawn event");
		}
	}
}