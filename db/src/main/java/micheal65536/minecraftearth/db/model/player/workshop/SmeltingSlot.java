package micheal65536.minecraftearth.db.model.player.workshop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SmeltingSlot
{
	@Nullable
	public ActiveJob activeJob;
	@Nullable
	public Burning burning;
	public boolean locked;

	public SmeltingSlot()
	{
		this.activeJob = null;
		this.burning = null;
		this.locked = false;
	}

	public record ActiveJob(
			@NotNull String sessionId,
			@NotNull String recipeId,
			long startTime,
			@NotNull InputItem input,
			@Nullable Fuel addedFuel,
			int totalRounds,
			int collectedRounds,
			boolean finishedEarly
	)
	{
	}

	public record Fuel(
			@NotNull InputItem item,
			int burnDuration,
			int heatPerSecond
	)
	{
	}

	public record Burning(
			@NotNull Fuel fuel,
			int remainingHeat
	)
	{
	}
}