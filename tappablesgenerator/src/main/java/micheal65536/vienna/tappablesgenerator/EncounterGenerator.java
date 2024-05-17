package micheal65536.vienna.tappablesgenerator;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.util.LinkedList;
import java.util.Random;
import java.util.UUID;

public class EncounterGenerator
{
	// TODO: make these configurable
	private static final int CHANCE_PER_TILE = 4;
	private static final long MIN_DELAY = 1 * 60 * 1000;
	private static final long MAX_DELAY = 2 * 60 * 1000;

	private record EncounterConfig(
			@NotNull String icon,
			@NotNull Rarity rarity,
			@NotNull String encounterBuildplateId,
			int duration
	)
	{
		public enum Rarity
		{
			// TODO: find actual weights
			@SerializedName("Common") COMMON(1.0f),
			@SerializedName("Uncommon") UNCOMMON(0.75f),
			@SerializedName("Rare") RARE(0.5f),
			@SerializedName("Epic") EPIC(0.25f),
			@SerializedName("Legendary") LEGENDARY(0.125f);

			public final float weight;

			Rarity(float weight)
			{
				this.weight = weight;
			}
		}
	}

	private final EncounterConfig[] encounterConfigs;
	private final float totalWeight;
	private final int maxDuration;

	private final Random random;

	public EncounterGenerator()
	{
		try
		{
			LogManager.getLogger().info("Loading encounter generator data");
			File dataDir = new File("data", "encounter");
			LinkedList<EncounterConfig> encounterConfigs = new LinkedList<>();
			for (File file : dataDir.listFiles())
			{
				encounterConfigs.add(new Gson().fromJson(new FileReader(file), EncounterConfig.class));
			}
			this.encounterConfigs = encounterConfigs.toArray(EncounterConfig[]::new);
			this.totalWeight = (float) encounterConfigs.stream().mapToDouble(encounterConfig -> encounterConfig.rarity.weight).sum();
			this.maxDuration = encounterConfigs.stream().mapToInt(encounterConfig -> encounterConfig.duration).max().orElse(0) * 1000;
		}
		catch (Exception exception)
		{
			LogManager.getLogger().fatal("Failed to load encounter generator data", exception);
			System.exit(1);
			throw new AssertionError();
		}

		if (this.encounterConfigs.length == 0)
		{
			LogManager.getLogger().warn("No encounter configs provided");
		}

		this.random = new Random();
	}

	public long getMaxEncounterLifetime()
	{
		return MAX_DELAY + this.maxDuration + 30 * 1000;
	}

	@NotNull
	public Encounter[] generateEncounters(int tileX, int tileY, long currentTime)
	{
		if (this.encounterConfigs.length == 0)
		{
			return new Encounter[0];
		}

		LinkedList<Encounter> encounters = new LinkedList<>();
		if (this.random.nextInt(0, CHANCE_PER_TILE) == 0)
		{
			long spawnDelay = this.random.nextLong(MIN_DELAY, MAX_DELAY + 1);

			float configPos = this.random.nextFloat(0.0f, this.totalWeight);
			EncounterConfig encounterConfig = null;
			for (EncounterConfig encounterConfig1 : this.encounterConfigs)
			{
				encounterConfig = encounterConfig1;
				configPos -= encounterConfig1.rarity.weight;
				if (configPos <= 0.0f)
				{
					break;
				}
			}
			if (encounterConfig == null)
			{
				throw new AssertionError();
			}

			float[] tileBounds = getTileBounds(tileX, tileY);
			float lat = this.random.nextFloat(tileBounds[1], tileBounds[0]);
			float lon = this.random.nextFloat(tileBounds[2], tileBounds[3]);

			Encounter encounter = new Encounter(
					UUID.randomUUID().toString(),
					lat,
					lon,
					currentTime + spawnDelay,
					encounterConfig.duration * 1000,
					encounterConfig.icon,
					Encounter.Rarity.valueOf(encounterConfig.rarity.name()),
					encounterConfig.encounterBuildplateId
			);
			encounters.add(encounter);
		}
		return encounters.toArray(Encounter[]::new);
	}

	private static float[] getTileBounds(int tileX, int tileY)
	{
		return new float[]{
				yToLat((float) tileY / (1 << 16)),
				yToLat((float) (tileY + 1) / (1 << 16)),
				xToLon((float) tileX / (1 << 16)),
				xToLon((float) (tileX + 1) / (1 << 16))
		};
	}

	private static float xToLon(float x)
	{
		return (float) Math.toDegrees((x * 2.0 - 1.0) * Math.PI);
	}

	private static float yToLat(float y)
	{
		return (float) Math.toDegrees(Math.atan(Math.sinh((1.0 - y * 2.0) * Math.PI)));
	}
}