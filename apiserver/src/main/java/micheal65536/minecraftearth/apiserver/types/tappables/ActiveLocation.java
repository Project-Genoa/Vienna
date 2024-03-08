package micheal65536.minecraftearth.apiserver.types.tappables;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.minecraftearth.apiserver.types.common.Coordinate;
import micheal65536.minecraftearth.apiserver.types.common.Rarity;

public record ActiveLocation(
		@NotNull String id,
		@NotNull String tileId,
		@NotNull Coordinate coordinate,
		@NotNull String spawnTime,
		@NotNull String expirationTime,
		@NotNull Type type,
		@NotNull String icon,
		@NotNull Metadata metadata,
		@Nullable TappableMetadata tappableMetadata,
		@Nullable EncounterMetadata encounterMetadata
)
{
	public enum Type
	{
		@SerializedName("Tappable") TAPPABLE,
		@SerializedName("Encounter") ENCOUNTER    // TODO: unverified
	}

	public record Metadata(
			@NotNull String rewardId,
			@NotNull Rarity rarity
	)
	{
	}

	public record TappableMetadata(
			@NotNull Rarity rarity
	)
	{
	}

	public record EncounterMetadata(
			// TODO
	)
	{
	}
}