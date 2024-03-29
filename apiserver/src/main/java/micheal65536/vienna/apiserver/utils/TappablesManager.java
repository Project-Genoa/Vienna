package micheal65536.vienna.apiserver.utils;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.eventbus.client.EventBusClient;
import micheal65536.vienna.eventbus.client.Publisher;
import micheal65536.vienna.eventbus.client.Subscriber;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.stream.IntStream;

public final class TappablesManager
{
	private final Publisher publisher;
	private final Subscriber subscriber;

	private final HashMap<String, HashMap<String, Tappable>> tappables = new HashMap<>();

	public TappablesManager(@NotNull EventBusClient eventBusClient)
	{
		this.publisher = eventBusClient.addPublisher();
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

	public void notifyTileActive(@NotNull String playerId, float lat, float lon)
	{
		int tileX = xToTile(lonToX(lon));
		int tileY = yToTile(latToY(lat));
		this.publisher.publish("tappables", "activeTile", new Gson().toJson(new ActiveTileNotification(tileX, tileY, playerId))).thenAccept(success ->
		{
			if (!success)
			{
				LogManager.getLogger().error("Event bus server rejected active tile notification event");
			}
		});
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
				// TODO: prune expired tappables
				Tappable tappable;
				try
				{
					tappable = new Gson().fromJson(event.data, Tappable.class);
				}
				catch (Exception exception)
				{
					LogManager.getLogger().error("Could not deserialise tappable spawn event", exception);
					break;
				}
				this.addTappable(tappable);
			}
			case "activeTile" ->
			{
				break;
			}
			default ->
			{
				LogManager.getLogger().error("Invalid tappables event bus event type {}", event.type);
			}
		}
	}

	private void addTappable(@NotNull Tappable tappable)
	{
		String tileId = locationToTileId(tappable.lat, tappable.lon);
		this.tappables.computeIfAbsent(tileId, tileId1 -> new HashMap<>()).put(tappable.id, tappable);
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
}