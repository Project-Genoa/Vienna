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

	public EarthApiResponse(T result, @NotNull Updates updates)
	{
		this.result = result;
		this.updates.putAll(updates.map);
	}

	public static final class Updates
	{
		private final HashMap<String, Integer> map = new HashMap<>();

		public Updates(@NotNull EarthDB.Results results)
		{
			HashMap<String, Integer> updates = results.getUpdates();
			this.put(updates, "profile", "characterProfile");
			this.put(updates, "inventory", "inventory");
			this.put(updates, "crafting", "crafting");
			this.put(updates, "smelting", "smelting");
			this.put(updates, "boosts", "boosts");
			this.put(updates, "buildplates", "buildplates");
			this.put(updates, "journal", "playerJournal");
			this.put(updates, "challenges", "challenges");
			this.put(updates, "tokens", "tokens");
		}

		private void put(@NotNull HashMap<String, Integer> updates, @NotNull String name, @NotNull String as)
		{
			Integer version = updates.getOrDefault(name, null);
			if (version != null)
			{
				this.map.put(as, version);
			}
		}
	}
}