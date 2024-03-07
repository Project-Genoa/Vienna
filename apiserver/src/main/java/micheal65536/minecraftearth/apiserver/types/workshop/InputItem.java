package micheal65536.minecraftearth.apiserver.types.workshop;

import org.jetbrains.annotations.NotNull;

public record InputItem(
		@NotNull String itemId,
		int quantity,
		String[] instanceIds
)
{
}