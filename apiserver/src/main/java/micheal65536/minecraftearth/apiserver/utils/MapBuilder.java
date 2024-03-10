package micheal65536.minecraftearth.apiserver.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public final class MapBuilder<K, V>
{
	private final HashMap<K, V> map = new HashMap<>();

	public MapBuilder()
	{
		// empty
	}

	@NotNull
	public MapBuilder<K, V> put(@NotNull K name, @Nullable V value)
	{
		this.map.put(name, value);
		return this;
	}

	@NotNull
	public HashMap<K, V> getMap()
	{
		return this.map;
	}
}