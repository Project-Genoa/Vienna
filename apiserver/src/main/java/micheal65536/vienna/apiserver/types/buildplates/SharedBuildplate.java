package micheal65536.vienna.apiserver.types.buildplates;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.apiserver.types.inventory.Inventory;

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
}