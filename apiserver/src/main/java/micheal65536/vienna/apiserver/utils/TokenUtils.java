package micheal65536.vienna.apiserver.utils;

import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.db.model.player.ActivityLog;
import micheal65536.vienna.db.model.player.Tokens;
import micheal65536.vienna.staticdata.StaticData;

import java.util.UUID;

public final class TokenUtils
{
	@NotNull
	public static EarthDB.Query addToken(@NotNull String playerId, @NotNull Tokens.Token token)
	{
		EarthDB.Query getQuery = new EarthDB.Query(true);
		getQuery.get("tokens", playerId, Tokens.class);
		getQuery.then(results ->
		{
			Tokens tokens = (Tokens) results.get("tokens").value();
			String id = UUID.randomUUID().toString();
			tokens.addToken(id, token);
			EarthDB.Query updateQuery = new EarthDB.Query(true);
			updateQuery.update("tokens", playerId, tokens);
			updateQuery.extra("tokenId", id);
			return updateQuery;
		});
		return getQuery;
	}

	// does not handle redeeming the token itself (removing it from the list of tokens belonging to the player)
	@NotNull
	public static EarthDB.Query doActionsOnRedeemedToken(@NotNull Tokens.Token token, @NotNull String playerId, long currentTime, @NotNull StaticData staticData)
	{
		EarthDB.Query getQuery = new EarthDB.Query(true);

		switch (token.type)
		{
			case LEVEL_UP ->
			{
				Tokens.LevelUpToken levelUpToken = (Tokens.LevelUpToken) token;
				getQuery.then(results ->
				{
					EarthDB.Query updateQuery = new EarthDB.Query(true);

					updateQuery.then(ActivityLogUtils.addEntry(playerId, new ActivityLog.LevelUpEntry(currentTime, levelUpToken.level)));

					updateQuery.then(Rewards.fromDBRewardsModel(levelUpToken.rewards).toRedeemQuery(playerId, currentTime, staticData));

					return updateQuery;
				}, false);
			}
			case JOURNAL_ITEM_UNLOCKED ->
			{
				Tokens.JournalItemUnlockedToken journalItemUnlockedToken = (Tokens.JournalItemUnlockedToken) token;
				getQuery.then(results ->
				{
					EarthDB.Query updateQuery = new EarthDB.Query(true);

					updateQuery.then(ActivityLogUtils.addEntry(playerId, new ActivityLog.JournalItemUnlockedEntry(currentTime, journalItemUnlockedToken.itemId)));

					/*int experiencePoints = staticData.catalog.itemsCatalog.getItem(journalItemUnlockedToken.itemId).experience().journal();
					if (experiencePoints > 0)
					{
						updateQuery.then(new Rewards().addExperiencePoints(experiencePoints).toRedeemQuery(playerId, currentTime, staticData));
					}*/

					return updateQuery;
				}, false);
			}
		}

		getQuery.extra("token", token);

		return getQuery;
	}
}