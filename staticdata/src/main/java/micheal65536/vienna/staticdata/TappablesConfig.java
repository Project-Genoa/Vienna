package micheal65536.vienna.staticdata;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.LinkedList;

public final class TappablesConfig
{
	@NotNull
	public final TappableConfig[] tappables;

	TappablesConfig(@NotNull File dir) throws StaticDataException
	{
		try
		{
			LinkedList<TappableConfig> tappables = new LinkedList<>();
			for (File file : dir.listFiles())
			{
				tappables.add(new Gson().fromJson(new FileReader(file), TappableConfig.class));
			}
			this.tappables = tappables.toArray(TappableConfig[]::new);

			for (TappableConfig tappableConfig : this.tappables)
			{
				for (TappableConfig.DropSet dropSet : tappableConfig.dropSets)
				{
					for (String itemId : dropSet.items)
					{
						if (!tappableConfig.itemCounts.containsKey(itemId))
						{
							throw new StaticDataException("Tappable config %s has no item count for item %s".formatted(tappableConfig.icon, itemId));
						}
					}
				}
			}
		}
		catch (StaticDataException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new StaticDataException(exception);
		}
	}

	public record TappableConfig(
			@NotNull String icon,
			@NotNull DropSet[] dropSets,
			@NotNull HashMap<String, ItemCount> itemCounts
	)
	{
		public record DropSet(
				@NotNull String[] items,
				int chance
		)
		{
		}

		public record ItemCount(
				int min,
				int max
		)
		{
		}
	}
}