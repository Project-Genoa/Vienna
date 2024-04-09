package micheal65536.vienna.buildplate.connector.model;

import org.jetbrains.annotations.NotNull;

public record PlayerDisconnectedRequest(
		@NotNull String playerId
)
{
}