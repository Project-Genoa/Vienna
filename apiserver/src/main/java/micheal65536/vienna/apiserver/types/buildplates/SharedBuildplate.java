package micheal65536.vienna.apiserver.types.buildplates;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.apiserver.types.inventory.HotbarItem;
import micheal65536.vienna.apiserver.types.inventory.NonStackableInventoryItem;
import micheal65536.vienna.apiserver.types.inventory.StackableInventoryItem;

public record SharedBuildplate(
		@NotNull String playerId,
		@NotNull String sharedOn,
		@NotNull BuildplateData buildplateData,
		@NotNull Inventory inventory
)
{
	public record BuildplateData(
			@NotNull Dimension dimension,
			@NotNull Offset offset,
			int blocksPerMeter,
			@NotNull Type type,
			@NotNull SurfaceOrientation surfaceOrientation,
			@NotNull String model,
			int order
	)
	{
		public enum Type
		{
			@SerializedName("Survival") SURVIVAL
		}
	}

	public record Inventory(
			HotbarItem[] hotbar,
			@NotNull StackableInventoryItem[] stackableItems,
			@NotNull NonStackableInventoryItem[] nonStackableItems
	)
	{
	}
}