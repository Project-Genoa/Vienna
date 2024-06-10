package micheal65536.vienna.staticdata;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.util.LinkedList;

public final class Levels
{
	@NotNull
	public final Level[] levels;

	Levels(@NotNull File dir) throws StaticDataException
	{
		try
		{
			LinkedList<Level> levels = new LinkedList<>();
			File file;
			for (int level = 2; (file = new File(dir, Integer.toString(level) + ".json")).isFile(); level++)
			{
				levels.add(new Gson().fromJson(new FileReader(file), Level.class));
			}
			this.levels = levels.toArray(Level[]::new);

			for (int index = 1; index < this.levels.length; index++)
			{
				if (this.levels[index].experienceRequired <= this.levels[index - 1].experienceRequired)
				{
					throw new StaticDataException("Level %d has lower experience required than preceding level %d".formatted(index + 2, index + 1));
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

	public record Level(
			int experienceRequired,
			int rubies,
			@NotNull Item[] items,
			@NotNull String[] buildplates
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