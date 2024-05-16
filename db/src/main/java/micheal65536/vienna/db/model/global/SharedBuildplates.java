package micheal65536.vienna.db.model.global;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public final class SharedBuildplates
{
	@NotNull
	private final HashMap<String, SharedBuildplate> sharedBuildplates = new HashMap<>();

	public SharedBuildplates()
	{
		// empty
	}

	public void addSharedBuildplate(@NotNull String id, @NotNull SharedBuildplate buildplate)
	{
		this.sharedBuildplates.put(id, buildplate);
	}

	@Nullable
	public SharedBuildplate getSharedBuildplate(@NotNull String id)
	{
		return this.sharedBuildplates.getOrDefault(id, null);
	}

	public static final class SharedBuildplate
	{
		@NotNull
		public final String playerId;

		public final int size;
		public final int offset;
		public final int scale;

		public final boolean night;

		public final long created;
		public final long buildplateLastModifed;
		public long lastViewed;
		public int numberOfTimesViewed;

		public final HotbarItem[] hotbar;

		@NotNull
		public final String serverDataObjectId;

		public SharedBuildplate(@NotNull String playerId, int size, int offset, int scale, boolean night, long created, long buildplateLastModifed, @NotNull String serverDataObjectId)
		{
			this.playerId = playerId;

			this.size = size;
			this.offset = offset;
			this.scale = scale;

			this.night = night;

			this.created = created;
			this.buildplateLastModifed = buildplateLastModifed;
			this.lastViewed = 0;
			this.numberOfTimesViewed = 0;

			this.hotbar = new HotbarItem[7];

			this.serverDataObjectId = serverDataObjectId;
		}

		public record HotbarItem(
				@NotNull String uuid,
				int count,
				@Nullable String instanceId,
				int wear
		)
		{
		}
	}
}