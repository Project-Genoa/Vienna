package micheal65536.vienna.db.model.common;

import org.jetbrains.annotations.NotNull;

public record NonStackableItemInstance(
		@NotNull String instanceId,
		int wear
)
{
}