package micheal65536.vienna.db.model.player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public final class Boosts
{
	@Nullable
	public final ActiveBoost[] activeBoosts;

	public Boosts()
	{
		this.activeBoosts = new ActiveBoost[5];
	}

	@Nullable
	public ActiveBoost get(@NotNull String instanceId)
	{
		return Arrays.stream(this.activeBoosts).filter(activeBoost -> activeBoost != null && activeBoost.instanceId.equals(instanceId)).findFirst().orElse(null);
	}

	public void prune(long currentTime)
	{
		for (int index = 0; index < this.activeBoosts.length; index++)
		{
			ActiveBoost activeBoost = this.activeBoosts[index];
			if (activeBoost != null && activeBoost.startTime + activeBoost.duration < currentTime)
			{
				this.activeBoosts[index] = null;
			}
		}
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