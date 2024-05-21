package micheal65536.vienna.tappablesgenerator;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.staticdata.EncountersConfig;
import micheal65536.vienna.staticdata.StaticData;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Random;
import java.util.UUID;

public class EncounterGenerator
{
	// TODO: make these configurable
	private static final int CHANCE_PER_TILE = 4;
	private static final long MIN_DELAY = 1 * 60 * 1000;
	private static final long MAX_DELAY = 2 * 60 * 1000;

	private final StaticData staticData;
	private final int maxDuration;

	private final Random random;

	public EncounterGenerator(@NotNull StaticData staticData)
	{
		this.staticData = staticData;
		if (this.staticData.encountersConfig.encounters.length == 0)
		{
			LogManager.getLogger().warn("No encounter configs provided");
		}
		this.maxDuration = Arrays.stream(this.staticData.encountersConfig.encounters).mapToInt(encounterConfig -> encounterConfig.duration()).max().orElse(0) * 1000;

		this.random = new Random();
	}

	public long getMaxEncounterLifetime()
	{
		return MAX_DELAY + this.maxDuration + 30 * 1000;
	}

	@NotNull
	public Encounter[] generateEncounters(int tileX, int tileY, long currentTime)
	{
		if (this.staticData.encountersConfig.encounters.length == 0)
		{
			return new Encounter[0];
		}

		LinkedList<Encounter> encounters = new LinkedList<>();
		if (this.random.nextInt(0, CHANCE_PER_TILE) == 0)
		{
			long spawnDelay = this.random.nextLong(MIN_DELAY, MAX_DELAY + 1);

			EncountersConfig.EncounterConfig encounterConfig = this.staticData.encountersConfig.encounters[this.random.nextInt(0, this.staticData.encountersConfig.encounters.length)];

			float[] tileBounds = getTileBounds(tileX, tileY);
			float lat = this.random.nextFloat(tileBounds[1], tileBounds[0]);
			float lon = this.random.nextFloat(tileBounds[2], tileBounds[3]);

			Encounter encounter = new Encounter(
					UUID.randomUUID().toString(),
					lat,
					lon,
					currentTime + spawnDelay,
					encounterConfig.duration() * 1000,
					encounterConfig.icon(),
					Encounter.Rarity.valueOf(encounterConfig.rarity().name()),
					encounterConfig.encounterBuildplateId()
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