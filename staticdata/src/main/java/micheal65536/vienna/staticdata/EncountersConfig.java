package micheal65536.vienna.staticdata;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.util.LinkedList;

public final class EncountersConfig
{
	@NotNull
	public final EncounterConfig[] encounters;

	EncountersConfig(@NotNull File dir) throws StaticDataException
	{
		try
		{
			LinkedList<EncounterConfig> encounters = new LinkedList<>();
			for (File file : dir.listFiles())
			{
				if (file.isFile() && file.getName().endsWith(".json"))
				{
					encounters.add(new Gson().fromJson(new FileReader(file), EncounterConfig.class));
				}
			}
			this.encounters = encounters.toArray(EncounterConfig[]::new);
		}
		catch (Exception exception)
		{
			throw new StaticDataException(exception);
		}
	}

	public record EncounterConfig(
			@NotNull String icon,
			@NotNull Rarity rarity,
			@NotNull String encounterBuildplateId,
			int duration
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