package micheal65536.vienna.apiserver.utils;

import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.apiserver.Catalog;
import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.db.model.player.ActivityLog;
import micheal65536.vienna.db.model.player.Profile;
import micheal65536.vienna.db.model.player.Tokens;

public final class LevelUtils
{
	// TODO: load this from data file
	@NotNull
	private static final Level[] levels = new Level[]{
			new Level(500, new Rewards().addRubies(15).addItem("730573d1-ba59-4fd4-89e0-85d4647466c2", 1).addItem("20dbd5fc-06b7-1aa1-5943-7ddaa2061e6a", 8).addItem("1eaa0d8c-2d89-2b84-aa1f-b75ccc85faff", 64))
	};

	@NotNull
	public static Level[] getLevels()
	{
		return levels;
	}

	@NotNull
	public static EarthDB.Query checkAndHandlePlayerLevelUp(@NotNull String playerId, long currentTime, @NotNull Catalog catalog)
	{
		EarthDB.Query getQuery = new EarthDB.Query(true);
		getQuery.get("profile", playerId, Profile.class);
		getQuery.then(results ->
		{
			Profile profile = (Profile) results.get("profile").value();
			EarthDB.Query updateQuery = new EarthDB.Query(true);
			boolean changed = false;
			while (profile.level - 1 < levels.length && profile.experience >= levels[profile.level - 1].experienceRequired)
			{
				changed = true;
				profile.level++;
				Rewards rewards = levels[profile.level - 2].rewards;
				updateQuery.then(ActivityLogUtils.addEntry(playerId, new ActivityLog.LevelUpEntry(currentTime, profile.level)));
				updateQuery.then(rewards.toRedeemQuery(playerId, currentTime, catalog));
				updateQuery.then(TokenUtils.addToken(playerId, new Tokens.LevelUpToken(profile.level)));
			}
			if (changed)
			{
				updateQuery.update("profile", playerId, profile);
			}
			return updateQuery;
		});
		return getQuery;
	}

	public record Level(
			int experienceRequired,
			@NotNull Rewards rewards
	)
	{
	}
}