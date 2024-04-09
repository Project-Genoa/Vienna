package micheal65536.vienna.apiserver.types.buildplates;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;

public record OwnedBuildplate(
		@NotNull String id,
		@NotNull String templateId,
		@NotNull Dimension dimension,
		@NotNull Offset offset,
		int blocksPerMeter,
		@NotNull Type type,
		@NotNull SurfaceOrientation surfaceOrientation,
		@NotNull String model,
		int order,
		boolean locked,
		int requiredLevel,
		boolean isModified,
		@NotNull String lastUpdated,
		int numberOfBlocks,
		@NotNull String eTag
)
{
	public enum Type
	{
		@SerializedName("Survival") SURVIVAL
	}
}