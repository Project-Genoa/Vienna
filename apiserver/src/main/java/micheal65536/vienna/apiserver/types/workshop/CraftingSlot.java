package micheal65536.vienna.apiserver.types.workshop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record CraftingSlot(
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
}