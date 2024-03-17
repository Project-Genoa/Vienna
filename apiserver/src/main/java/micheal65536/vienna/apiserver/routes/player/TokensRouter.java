package micheal65536.vienna.apiserver.routes.player;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.apiserver.routing.Request;
import micheal65536.vienna.apiserver.routing.Response;
import micheal65536.vienna.apiserver.routing.Router;
import micheal65536.vienna.apiserver.routing.ServerErrorException;
import micheal65536.vienna.apiserver.types.common.Token;
import micheal65536.vienna.apiserver.utils.EarthApiResponse;
import micheal65536.vienna.apiserver.utils.MapBuilder;
import micheal65536.vienna.apiserver.utils.Rewards;
import micheal65536.vienna.db.DatabaseException;
import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.db.model.player.Tokens;

import java.util.Arrays;
import java.util.HashMap;

public class TokensRouter extends Router
{
	public TokensRouter(@NotNull EarthDB earthDB)
	{
		this.addHandler(new Route.Builder(Request.Method.GET, "/player/tokens").build(), request ->
		{
			try
			{
				Tokens tokens = (Tokens) new EarthDB.Query(false)
						.get("tokens", request.getContextData("playerId"), Tokens.class)
						.execute(earthDB)
						.get("tokens").value();
				return Response.okFromJson(new EarthApiResponse<>(
						new MapBuilder<>().put("tokens",
								Arrays.stream(tokens.getTokens()).collect(HashMap<String, Token>::new, (hashMap, token) ->
								{
									hashMap.put(token.id(), new Token(
											Token.Type.valueOf(token.token().type().name()),
											new HashMap<>(token.token().properties()),
											Rewards.fromDBRewardsModel(token.token().rewards()).toApiResponse(),
											Token.Lifetime.valueOf(token.token().lifetime().name())
									));
								}, HashMap::putAll)
						).getMap()
				), EarthApiResponse.class);
			}
			catch (DatabaseException exception)
			{
				LogManager.getLogger().error(exception);
				return Response.serverError();
			}
		});

		// TODO: some token types might have actions to perform when they're redeemed?
		this.addHandler(new Route.Builder(Request.Method.POST, "/player/tokens/$tokenId/redeem").build(), request ->
		{
			Tokens.Token token;
			try
			{
				String playerId = request.getContextData("playerId");
				String tokenId = request.getParameter("tokenId");
				EarthDB.Results results = new EarthDB.Query(true)
						.get("tokens", playerId, Tokens.class)
						.then(results1 ->
						{
							Tokens tokens = (Tokens) results1.get("tokens").value();
							Tokens.Token removedToken = tokens.removeToken(tokenId);
							if (removedToken != null)
							{
								return new EarthDB.Query(true)
										.update("tokens", playerId, tokens)
										.extra("success", true)
										.extra("token", removedToken);
							}
							else
							{
								return new EarthDB.Query(false)
										.extra("success", false);
							}
						})
						.execute(earthDB);
				token = (boolean) results.getExtra("success") ? (Tokens.Token) results.getExtra("token") : null;
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}

			if (token != null)
			{
				return Response.okFromJson(new Token(
						Token.Type.valueOf(token.type().name()),
						new HashMap<>(token.properties()),
						Rewards.fromDBRewardsModel(token.rewards()).toApiResponse(),
						Token.Lifetime.valueOf(token.lifetime().name())
				), Token.class);
			}
			else
			{
				return Response.badRequest();
			}
		});
	}
}