package micheal65536.vienna.apiserver.types.tappables;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;

public record EncounterState(
		@NotNull ActiveEncounterState activeEncounterState
)
{
	public enum ActiveEncounterState
	{
		@SerializedName("Pristine") PRISTINE,
		@SerializedName("Dirty") DIRTY
	}
}