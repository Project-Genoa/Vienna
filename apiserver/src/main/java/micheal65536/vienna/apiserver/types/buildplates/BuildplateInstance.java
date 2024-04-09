package micheal65536.vienna.apiserver.types.buildplates;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.apiserver.types.common.Coordinate;
import micheal65536.vienna.apiserver.types.common.Rarity;

import java.util.HashMap;

// TODO: actually implement proper snapshot and shutdown behavior in the buildplate server
public record BuildplateInstance(
		@NotNull String instanceId,
		@NotNull String partitionId,
		@NotNull String fqdn,
		@NotNull String ipV4Address,
		int port,
		boolean serverReady,
		@NotNull ApplicationStatus applicationStatus,
		@NotNull ServerStatus serverStatus,
		@NotNull String metadata,
		@NotNull GameplayMetadata gameplayMetadata,
		@NotNull String roleInstance,    // TODO: find out what this is
		@NotNull Coordinate hostCoordinate
)
{
	public enum ApplicationStatus
	{
		@SerializedName("Unknown") UNKNOWN,
		@SerializedName("Ready") READY
	}

	public enum ServerStatus
	{
		@SerializedName("Running") RUNNING
	}

	public record GameplayMetadata(
			@NotNull String worldId,
			@NotNull String templateId,
			@NotNull String spawningPlayerId,
			@NotNull String spawningClientBuildNumber,
			@NotNull String playerJoinCode,
			@NotNull Dimension dimension,
			@NotNull Offset offset,
			int blocksPerMeter,
			boolean isFullSize,
			@NotNull GameplayMode gameplayMode,
			@NotNull SurfaceOrientation surfaceOrientation,
			@Nullable String augmentedImageSetId,
			@Nullable Rarity rarity,
			@NotNull ShutdownBehavior[] shutdownBehavior,
			@NotNull SnapshotOptions snapshotOptions,
			@NotNull HashMap<String, Object> breakableItemToItemLootMap    // TODO: find out what this is
	)
	{
		public enum GameplayMode
		{
			@SerializedName("Buildplate") BUILDPLATE,
			@SerializedName("Encounter") ENCOUNTER
		}

		public enum ShutdownBehavior
		{
			@SerializedName("ServerShutdownWhenAllPlayersQuit") ALL_PLAYERS_QUIT,
			@SerializedName("ServerShutdownWhenHostPlayerQuits") HOST_PLAYER_QUITS
		}

		public record SnapshotOptions(
				@NotNull SnapshotWorldStorage snapshotWorldStorage,
				@NotNull SaveState saveState,
				@NotNull SnapshotTriggerConditions snapshotTriggerConditions,
				@NotNull TriggerCondition[] triggerConditions,
				@NotNull String triggerInterval
		)
		{
			public enum SnapshotWorldStorage
			{
				@SerializedName("Buildplate") BUILDPLATE
			}

			public record SaveState(
					boolean boosts,
					boolean experiencePoints,
					boolean health,
					boolean inventory,
					boolean model,
					boolean world
			)
			{
			}

			public enum SnapshotTriggerConditions
			{
				@SerializedName("None") NONE
			}

			public enum TriggerCondition
			{
				@SerializedName("Interval") INTERVAL,
				@SerializedName("PlayerExits") PLAYER_EXITS
			}
		}
	}
}