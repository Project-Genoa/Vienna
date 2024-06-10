package micheal65536.vienna.apiserver.utils;

import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.db.model.player.ActivityLog;
import micheal65536.vienna.db.model.player.Profile;
import micheal65536.vienna.db.model.player.Tokens;
import micheal65536.vienna.staticdata.Levels;
import micheal65536.vienna.staticdata.StaticData;

public final class LevelUtils
{
	@NotNull
	public static EarthDB.Query checkAndHandlePlayerLevelUp(@NotNull String playerId, long currentTime, @NotNull StaticData staticData)
	{
		EarthDB.Query getQuery = new EarthDB.Query(true);
		getQuery.get("profile", playerId, Profile.class);
		getQuery.then(results ->
		{
			Profile profile = (Profile) results.get("profile").value();
			EarthDB.Query updateQuery = new EarthDB.Query(true);
			boolean changed = false;
			while (profile.level - 1 < staticData.levels.levels.length && profile.experience >= staticData.levels.levels[profile.level - 1].experienceRequired())
			{
				changed = true;
				profile.level++;
				Rewards rewards = makeLevelRewards(staticData.levels.levels[profile.level - 2]);
				updateQuery.then(ActivityLogUtils.addEntry(playerId, new ActivityLog.LevelUpEntry(currentTime, profile.level)));
				updateQuery.then(rewards.toRedeemQuery(playerId, currentTime, staticData));
				updateQuery.then(TokenUtils.addToken(playerId, new Tokens.LevelUpToken(profile.level, rewards.toDBRewardsModel())));
			}
			if (changed)
			{
				updateQuery.update("profile", playerId, profile);
			}
			return updateQuery;
		});
		return getQuery;
	}

	@NotNull
	public static Rewards makeLevelRewards(@NotNull Levels.Level level)
	{
		Rewards rewards = new Rewards();
		if (level.rubies() > 0)
		{
			rewards.addRubies(level.rubies());
		}
		for (Levels.Level.Item item : level.items())
		{
			rewards.addItem(item.id(), item.count());
		}
		for (String buildplate : level.buildplates())
		{
			rewards.addBuildplate(buildplate);
		}
		return rewards;
	}
}