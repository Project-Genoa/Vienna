package micheal65536.minecraftearth.db.model.player.workshop;

import org.jetbrains.annotations.NotNull;

import micheal65536.minecraftearth.db.model.common.NonStackableItemInstance;

public record InputItem(
		@NotNull String id,
		int count,
		@NotNull NonStackableItemInstance[] instances
)
{
}