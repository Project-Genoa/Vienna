package micheal65536.vienna.db.model.player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedList;

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

	@NotNull
	public ActiveBoost[] prune(long currentTime)
	{
		LinkedList<ActiveBoost> prunedBoosts = new LinkedList<>();
		for (int index = 0; index < this.activeBoosts.length; index++)
		{
			ActiveBoost activeBoost = this.activeBoosts[index];
			if (activeBoost != null && activeBoost.startTime + activeBoost.duration < currentTime)
			{
				this.activeBoosts[index] = null;
				prunedBoosts.add(activeBoost);
			}
		}
		return prunedBoosts.toArray(ActiveBoost[]::new);
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