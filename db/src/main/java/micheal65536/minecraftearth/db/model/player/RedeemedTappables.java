package micheal65536.minecraftearth.db.model.player;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public final class RedeemedTappables
{
	private final HashMap<String, Long> tappables = new HashMap<>();

	public RedeemedTappables()
	{
		// empty
	}

	public boolean isRedeemed(@NotNull String id)
	{
		return this.tappables.containsKey(id);
	}

	public void add(@NotNull String id, long expiresAt)
	{
		this.tappables.put(id, expiresAt);
	}

	public void prune(long currentTime)
	{
		this.tappables.entrySet().removeIf(entry -> entry.getValue() < currentTime);
	}
}