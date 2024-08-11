package micheal65536.vienna.db.model.player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public final class Boosts
{
	private final HashMap<String, ActiveBoost> activeBoosts = new HashMap<>();

	public Boosts()
	{
		// empty
	}

	@NotNull
	public ActiveBoost[] getAll()
	{
		return this.activeBoosts.values().toArray(ActiveBoost[]::new);
	}

	@Nullable
	public ActiveBoost get(@NotNull String instanceId)
	{
		return this.activeBoosts.getOrDefault(instanceId, null);
	}

	public void add(@NotNull String instanceId, @NotNull String itemId, long startTime, long duration)
	{
		this.activeBoosts.put(instanceId, new ActiveBoost(instanceId, itemId, startTime, duration));
	}

	public void remove(@NotNull String instanceId)
	{
		this.activeBoosts.remove(instanceId);
	}

	public void prune(long currentTime)
	{
		this.activeBoosts.entrySet().removeIf(entry -> entry.getValue().startTime + entry.getValue().duration < currentTime);
	}

	public record ActiveBoost(
			@NotNull String instanceId,
			@NotNull String itemId,
			long startTime,
			long duration
	)
	{
	}
}