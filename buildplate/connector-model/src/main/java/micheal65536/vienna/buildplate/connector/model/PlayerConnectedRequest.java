package micheal65536.vienna.buildplate.connector.model;

import org.jetbrains.annotations.NotNull;

public record PlayerConnectedRequest(
		@NotNull String uuid,
		@NotNull String joinCode
)
{
}