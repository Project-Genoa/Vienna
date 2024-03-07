package micheal65536.minecraftearth.apiserver.types.workshop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.minecraftearth.apiserver.types.common.BurnRate;

public record SmeltingSlot(
		@Nullable Fuel fuel,
		@Nullable Burning burning,
		@Nullable String sessionId,
		@Nullable String recipeId,
		@Nullable OutputItem output,
		InputItem[] escrow,
		int completed,
		int available,
		int total,
		@Nullable String nextCompletionUtc,
		@Nullable String totalCompletionUtc,
		@NotNull State state,
		@Nullable BoostState boostState,
		@Nullable UnlockPrice unlockPrice,
		int streamVersion
)
{
	public record Fuel(
			@NotNull BurnRate burnRate,
			@NotNull String itemId,
			int quantity,
			String[] itemInstanceIds
	)
	{
	}

	public record Burning(
			@Nullable String burnStartTime,
			@Nullable String burnsUntil,
			@Nullable String remainingBurnTime,
			@Nullable Float heatDepleted,
			@NotNull Fuel fuel
	)
	{
	}
}