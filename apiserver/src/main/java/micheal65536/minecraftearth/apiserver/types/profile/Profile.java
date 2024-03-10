package micheal65536.minecraftearth.apiserver.types.profile;

import org.jetbrains.annotations.NotNull;

import micheal65536.minecraftearth.apiserver.types.common.Rewards;

import java.util.HashMap;

public record Profile(
		@NotNull HashMap<Integer, Level> levelDistribution,
		int totalExperience,
		int level,
		int currentLevelExperience,
		int experienceRemaining,
		int health,
		float healthPercentage
)
{
	public record Level(
			int experienceRequired,
			@NotNull Rewards rewards
	)
	{
	}
}