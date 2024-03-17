package micheal65536.vienna.apiserver.utils;

import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.db.model.player.Tokens;

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
}