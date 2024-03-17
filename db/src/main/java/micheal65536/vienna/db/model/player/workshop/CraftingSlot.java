package micheal65536.vienna.db.model.player.workshop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CraftingSlot
{
	@Nullable
	public ActiveJob activeJob;
	public boolean locked;

	public CraftingSlot()
	{
		this.activeJob = null;
		this.locked = false;
	}

	public record ActiveJob(
			@NotNull String sessionId,
			@NotNull String recipeId,
			long startTime,
			@NotNull InputItem[] input,
			int totalRounds,
			int collectedRounds,
			boolean finishedEarly
	)
	{
	}
}