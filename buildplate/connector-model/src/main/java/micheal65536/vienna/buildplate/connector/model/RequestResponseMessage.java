package micheal65536.vienna.buildplate.connector.model;

import org.jetbrains.annotations.NotNull;

public record RequestResponseMessage(
		@NotNull String requestId,
		@NotNull String message
)
{
}