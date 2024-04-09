package micheal65536.vienna.db.model.player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public final class Buildplates
{
	@NotNull
	private final HashMap<String, Buildplate> buildplates = new HashMap<>();

	public Buildplates()
	{
		// empty
	}

	public void addBuildplate(@NotNull String id, @NotNull Buildplate buildplate)
	{
		this.buildplates.put(id, buildplate);
	}

	@Nullable
	public Buildplate getBuildplate(@NotNull String id)
	{
		return this.buildplates.getOrDefault(id, null);
	}

	public record BuildplateEntry(
			@NotNull String id,
			@NotNull Buildplate buildplate
	)
	{
	}

	@NotNull
	public BuildplateEntry[] getBuildplates()
	{
		return this.buildplates.entrySet().stream().map(entry -> new BuildplateEntry(entry.getKey(), entry.getValue())).toArray(BuildplateEntry[]::new);
	}

	public static final class Buildplate
	{
		public final int size;
		public final int offset;
		public final int scale;

		public final boolean night;

		public long lastModified;
		@NotNull
		public String serverDataObjectId;
		@NotNull
		public String previewObjectId;

		public Buildplate(int size, int offset, int scale, boolean night, long lastModified, @NotNull String serverDataObjectId, @NotNull String previewObjectId)
		{
			this.size = size;
			this.offset = offset;
			this.scale = scale;

			this.night = night;

			this.lastModified = lastModified;
			this.serverDataObjectId = serverDataObjectId;
			this.previewObjectId = previewObjectId;
		}
	}
}