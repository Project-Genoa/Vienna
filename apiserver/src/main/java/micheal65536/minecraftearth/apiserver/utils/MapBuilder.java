package micheal65536.minecraftearth.apiserver.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class MapBuilder<T>
{
	private final HashMap<String, T> map = new HashMap<>();

	public MapBuilder()
	{
		// empty
	}

	@NotNull
	public MapBuilder<T> put(@NotNull String name, @Nullable T value)
	{
		this.map.put(name, value);
		return this;
	}

	@NotNull
	public Map<String, T> getMap()
	{
		return this.map;
	}
}