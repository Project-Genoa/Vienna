package micheal65536.minecraftearth.apiserver.utils;

import org.jetbrains.annotations.NotNull;

import micheal65536.minecraftearth.db.EarthDB;

import java.util.HashMap;

public class EarthApiResponse<T>
{
	private final T result;
	private final HashMap<String, Integer> updates = new HashMap<>();

	public EarthApiResponse(T result)
	{
		this.result = result;
	}

	public EarthApiResponse(T result, Updates updates)
	{
		this.result = result;
		if (updates != null)
		{
			this.updates.putAll(updates.map);
		}
	}

	public static final class Updates
	{
		private final HashMap<String, Integer> map = new HashMap<>();

		public Updates(@NotNull EarthDB.Results results, @NotNull String... names)
		{
			for (String name : names)
			{
				this.map.put(name, results.get(name).version());
			}
		}

		public Updates()
		{
			// empty
		}

		@NotNull
		public Updates put(@NotNull String name, int version)
		{
			this.map.put(name, version);
			return this;
		}
	}
}