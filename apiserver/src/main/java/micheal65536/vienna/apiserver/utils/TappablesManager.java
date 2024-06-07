package micheal65536.vienna.apiserver.utils;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.eventbus.client.EventBusClient;
import micheal65536.vienna.eventbus.client.RequestSender;
import micheal65536.vienna.eventbus.client.Subscriber;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.stream.IntStream;

public final class TappablesManager
{
	private final long GRACE_PERIOD = 30000;

	private final Subscriber subscriber;
	private final RequestSender requestSender;

	private final HashMap<String, HashMap<String, Tappable>> tappables = new HashMap<>();
	private final HashMap<String, HashMap<String, Encounter>> encounters = new HashMap<>();
	private int pruneCounter = 0;

	public TappablesManager(@NotNull EventBusClient eventBusClient)
	{
		this.subscriber = eventBusClient.addSubscriber("tappables", new Subscriber.SubscriberListener()
		{
			@Override
			public void event(@NotNull Subscriber.Event event)
			{
				TappablesManager.this.handleEvent(event);
			}

			@Override
			public void error()
			{
				LogManager.getLogger().fatal("Tappables event bus subscriber error");
				System.exit(1);
			}
		});
		this.requestSender = eventBusClient.addRequestSender();
	}

	@NotNull
	public Tappable[] getTappablesAround(float lat, float lon, float radius)
	{
		return Arrays.stream(getTileIdsAround(lat, lon, radius))
				.map(tileId -> this.tappables.getOrDefault(tileId, null))
				.filter(tappables -> tappables != null)
				.map(HashMap::values)
				.flatMap(Collection::stream)
				.filter(tappable ->
				{
					float dx = lonToX(tappable.lon) * (1 << 16) - lonToX(lon) * (1 << 16);
					float dy = latToY(tappable.lat) * (1 << 16) - latToY(lat) * (1 << 16);
					float distanceSquared = dx * dx + dy * dy;
					return distanceSquared <= radius * radius;
				})
				.toArray(Tappable[]::new);
	}

	@NotNull
	public Encounter[] getEncountersAround(float lat, float lon, float radius)
	{
		return Arrays.stream(getTileIdsAround(lat, lon, radius))
				.map(tileId -> this.encounters.getOrDefault(tileId, null))
				.filter(encounters -> encounters != null)
				.map(HashMap::values)
				.flatMap(Collection::stream)
				.filter(encounter ->
				{
					float dx = lonToX(encounter.lon) * (1 << 16) - lonToX(lon) * (1 << 16);
					float dy = latToY(encounter.lat) * (1 << 16) - latToY(lat) * (1 << 16);
					float distanceSquared = dx * dx + dy * dy;
					return distanceSquared <= radius * radius;
				})
				.toArray(Encounter[]::new);
	}

	@NotNull
	private static String[] getTileIdsAround(float lat, float lon, float radius)
	{
		int tileX = xToTile(lonToX(lon));
		int tileY = yToTile(latToY(lat));
		int tileRadius = (int) Math.ceil(radius);
		return IntStream.range(tileX - tileRadius, tileX + tileRadius + 1).mapToObj(x -> IntStream.range(tileY - tileRadius, tileY + tileRadius + 1).mapToObj(y -> "%d_%d".formatted(x, y))).flatMap(stream -> stream).toArray(String[]::new);
	}

	@Nullable
	public Tappable getTappableWithId(@NotNull String id, @NotNull String tileId)
	{
		HashMap<String, Tappable> tappablesInTile = this.tappables.getOrDefault(tileId, null);
		if (tappablesInTile != null)
		{
			Tappable tappable = tappablesInTile.getOrDefault(id, null);
			if (tappable != null)
			{
				return tappable;
			}
		}
		return null;
	}

	@Nullable
	public Encounter getEncounterWithId(@NotNull String id, @NotNull String tileId)
	{
		HashMap<String, Encounter> encountersInTile = this.encounters.getOrDefault(tileId, null);
		if (encountersInTile != null)
		{
			Encounter encounter = encountersInTile.getOrDefault(id, null);
			if (encounter != null)
			{
				return encounter;
			}
		}
		return null;
	}

	public boolean isTappableValidFor(@NotNull Tappable tappable, long requestTime, float lat, float lon)
	{
		if (tappable.spawnTime - GRACE_PERIOD > requestTime || tappable.spawnTime + tappable.validFor + GRACE_PERIOD <= requestTime)
		{
			return false;
		}

		// TODO: check player location is in radius

		return true;
	}

	// TODO: actually use this
	public boolean isEncounterValidFor(@NotNull Encounter encounter, long requestTime, float lat, float lon)
	{
		if (encounter.spawnTime - GRACE_PERIOD > requestTime || encounter.spawnTime + encounter.validFor <= requestTime) // no grace period when checking end time because the buildplate instance shutdown does not include the grace period anyway
		{
			return false;
		}

		// TODO: check player location is in radius

		return true;
	}

	public void notifyTileActive(@NotNull String playerId, float lat, float lon)
	{
		int tileX = xToTile(lonToX(lon));
		int tileY = yToTile(latToY(lat));
		String response = this.requestSender.request("tappables", "activeTile", new Gson().toJson(new ActiveTileNotification(tileX, tileY, playerId))).join();
		if (response == null)
		{
			LogManager.getLogger().warn("Active tile notification event was rejected/ignored");
		}
	}

	private record ActiveTileNotification(
			int x,
			int y,
			@NotNull String playerId
	)
	{
	}

	private void handleEvent(@NotNull Subscriber.Event event)
	{
		switch (event.type)
		{
			case "tappableSpawn" ->
			{
				Tappable[] tappables;
				try
				{
					tappables = new Gson().fromJson(event.data, Tappable[].class);
				}
				catch (Exception exception)
				{
					LogManager.getLogger().error("Could not deserialise tappable spawn event", exception);
					break;
				}
				for (Tappable tappable : tappables)
				{
					this.addTappable(tappable);
				}

				if (this.pruneCounter++ == 10)
				{
					this.pruneCounter = 0;
					this.prune(event.timestamp);
				}
			}
			case "encounterSpawn" ->
			{
				Encounter[] encounters;
				try
				{
					encounters = new Gson().fromJson(event.data, Encounter[].class);
				}
				catch (Exception exception)
				{
					LogManager.getLogger().error("Could not deserialise encounter spawn event", exception);
					break;
				}
				for (Encounter encounter : encounters)
				{
					this.addEncounter(encounter);
				}

				if (this.pruneCounter++ == 10)
				{
					this.pruneCounter = 0;
					this.prune(event.timestamp);
				}
			}
		}
	}

	private void addTappable(@NotNull Tappable tappable)
	{
		String tileId = locationToTileId(tappable.lat, tappable.lon);
		this.tappables.computeIfAbsent(tileId, tileId1 -> new HashMap<>()).put(tappable.id, tappable);
	}

	private void addEncounter(@NotNull Encounter encounter)
	{
		String tileId = locationToTileId(encounter.lat, encounter.lon);
		this.encounters.computeIfAbsent(tileId, tileId1 -> new HashMap<>()).put(encounter.id, encounter);
	}

	private void prune(long currentTime)
	{
		this.tappables.values().forEach(tileTappables -> tileTappables.entrySet().removeIf(entry ->
		{
			Tappable tappable = entry.getValue();
			long expiresAt = tappable.spawnTime + tappable.validFor;
			return expiresAt + GRACE_PERIOD <= currentTime;
		}));
		this.tappables.entrySet().removeIf(entry -> entry.getValue().isEmpty());

		this.encounters.values().forEach(tileEncounters -> tileEncounters.entrySet().removeIf(entry ->
		{
			Encounter encounter = entry.getValue();
			long expiresAt = encounter.spawnTime + encounter.validFor;
			return expiresAt + GRACE_PERIOD <= currentTime;
		}));
		this.encounters.entrySet().removeIf(entry -> entry.getValue().isEmpty());
	}

	@NotNull
	public static String locationToTileId(float lat, float lon)
	{
		return "%d_%d".formatted(xToTile(lonToX(lon)), yToTile(latToY(lat)));
	}

	private static float lonToX(float lon)
	{
		return (float) ((1.0 + Math.toRadians(lon) / Math.PI) / 2.0);
	}

	private static float latToY(float lat)
	{
		return (float) ((1.0 - (Math.log(Math.tan(Math.toRadians(lat)) + 1.0 / Math.cos(Math.toRadians(lat)))) / Math.PI) / 2.0);
	}

	private static int xToTile(float x)
	{
		return (int) Math.floor(x * (1 << 16));
	}

	private static int yToTile(float y)
	{
		return (int) Math.floor(y * (1 << 16));
	}

	public record Tappable(
			@NotNull String id,
			float lat,
			float lon,
			long spawnTime,
			long validFor,
			@NotNull String icon,
			@NotNull Rarity rarity,
			@NotNull Drops drops
	)
	{
		public enum Rarity
		{
			COMMON,
			UNCOMMON,
			RARE,
			EPIC,
			LEGENDARY
		}

		public record Drops(
				int experiencePoints,
				@NotNull Item[] items
		)
		{
			public record Item(
					@NotNull String id,
					int count
			)
			{
			}
		}
	}

	public record Encounter(
			@NotNull String id,
			float lat,
			float lon,
			long spawnTime,
			long validFor,
			@NotNull String icon,
			@NotNull Rarity rarity,
			@NotNull String encounterBuildplateId
	)
	{
		public enum Rarity
		{
			COMMON,
			UNCOMMON,
			RARE,
			EPIC,
			LEGENDARY
		}
	}
}