package micheal65536.vienna.db.model.global;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public final class EncounterBuildplates
{
	@NotNull
	private final HashMap<String, EncounterBuildplate> encounterBuildplates = new HashMap<>();

	public EncounterBuildplates()
	{
		// empty
	}

	@Nullable
	public EncounterBuildplate getEncounterBuildplate(@NotNull String id)
	{
		return this.encounterBuildplates.getOrDefault(id, null);
	}

	public static final class EncounterBuildplate
	{
		public final int size;
		public final int offset;
		public final int scale;

		@NotNull
		public final String serverDataObjectId;

		public EncounterBuildplate(int size, int offset, int scale, @NotNull String serverDataObjectId)
		{
			this.size = size;
			this.offset = offset;
			this.scale = scale;

			this.serverDataObjectId = serverDataObjectId;
		}
	}
}