package micheal65536.vienna.db.model.player.workshop;

import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.db.model.common.NonStackableItemInstance;

public record InputItem(
		@NotNull String id,
		int count,
		@NotNull NonStackableItemInstance[] instances
)
{
}