package micheal65536.vienna.apiserver.types.workshop;

import org.jetbrains.annotations.NotNull;

public record FinishPrice(
		int cost,
		int discount,
		@NotNull String validTime
)
{
}