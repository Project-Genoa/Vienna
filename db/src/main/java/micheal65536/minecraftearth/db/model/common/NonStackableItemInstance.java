package micheal65536.minecraftearth.db.model.common;

import org.jetbrains.annotations.NotNull;

public record NonStackableItemInstance(
		@NotNull String instanceId,
		float health
)
{
}