package micheal65536.vienna.apiserver.types.tappables;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.apiserver.types.common.Coordinate;
import micheal65536.vienna.apiserver.types.common.Rarity;

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
		@SerializedName("Encounter") ENCOUNTER,
		@SerializedName("PlayerAdventure") PLAYER_ADVENTURE
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
			@NotNull EncounterType encounterType,
			@NotNull String locationId,
			@NotNull String worldId,
			@NotNull AnchorState anchorState,
			@NotNull String anchorId,
			@NotNull String augmentedImageSetId
	)
	{
		// TODO: what do these actually do?
		public enum EncounterType
		{
			@SerializedName("None") NONE,
			@SerializedName("Short4X4Peaceful") SHORT_4X4_PEACEFUL,
			@SerializedName("Short4X4Hostile") SHORT_4X4_HOSTILE,
			@SerializedName("Short8X8Peaceful") SHORT_8X8_PEACEFUL,
			@SerializedName("Short8X8Hostile") SHORT_8X8_HOSTILE,
			@SerializedName("Short16X16Peaceful") SHORT_16X16_PEACEFUL,
			@SerializedName("Short16X16Hostile") SHORT_16X16_HOSTILE,
			@SerializedName("Tall4X4Peaceful") TALL_4X4_PEACEFUL,
			@SerializedName("Tall4X4Hostile") TALL_4X4_HOSTILE,
			@SerializedName("Tall8X8Peaceful") TALL_8X8_PEACEFUL,
			@SerializedName("Tall8X8Hostile") TALL_8X8_HOSTILE,
			@SerializedName("Tall16X16Peaceful") TALL_16X16_PEACEFUL,
			@SerializedName("Tall16X16Hostile") TALL_16X16_HOSTILE
		}

		public enum AnchorState
		{
			@SerializedName("Off") OFF
		}
	}
}