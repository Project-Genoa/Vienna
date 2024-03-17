package micheal65536.vienna.db.model.common;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public record Rewards(
		int rubies,
		int experiencePoints,
		@Nullable Integer level,
		@NotNull HashMap<String, Integer> items,
		@NotNull String[] buildplates,
		@NotNull String[] challenges
)
{
}