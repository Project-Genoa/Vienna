package micheal65536.vienna.apiserver.routes.player;

import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.apiserver.Catalog;
import micheal65536.vienna.apiserver.routing.Request;
import micheal65536.vienna.apiserver.routing.Response;
import micheal65536.vienna.apiserver.routing.Router;
import micheal65536.vienna.apiserver.routing.ServerErrorException;
import micheal65536.vienna.apiserver.types.common.Coordinate;
import micheal65536.vienna.apiserver.types.common.Rarity;
import micheal65536.vienna.apiserver.types.common.Token;
import micheal65536.vienna.apiserver.types.tappables.ActiveLocation;
import micheal65536.vienna.apiserver.utils.ActivityLogUtils;
import micheal65536.vienna.apiserver.utils.EarthApiResponse;
import micheal65536.vienna.apiserver.utils.MapBuilder;
import micheal65536.vienna.apiserver.utils.Rewards;
import micheal65536.vienna.apiserver.utils.TappablesManager;
import micheal65536.vienna.apiserver.utils.TimeFormatter;
import micheal65536.vienna.db.DatabaseException;
import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.db.model.player.ActivityLog;
import micheal65536.vienna.db.model.player.RedeemedTappables;
import micheal65536.vienna.eventbus.client.EventBusClient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;

public class TappablesRouter extends Router
{
	public TappablesRouter(@NotNull EarthDB earthDB, @NotNull EventBusClient eventBusClient, @NotNull TappablesManager tappablesManager, @NotNull Catalog catalog)
	{
		this.addHandler(new Route.Builder(Request.Method.GET, "/locations/$lat/$lon").build(), request ->
		{
			String playerId = request.getContextData("playerId");
			float lat = request.getParameterFloat("lat");
			float lon = request.getParameterFloat("lon");

			tappablesManager.notifyTileActive(playerId, lat, lon);

			TappablesManager.Tappable[] tappables = tappablesManager.getTappablesAround(lat, lon, 5.0f);    // TODO: radius

			try
			{
				EarthDB.Results results = new EarthDB.Query(false)
						.get("redeemedTappables", playerId, RedeemedTappables.class)
						.execute(earthDB);
				RedeemedTappables redeemedTappables = (RedeemedTappables) results.get("redeemedTappables").value();

				ActiveLocation[] activeLocations = Arrays.stream(tappables)
						.filter(tappable -> tappable.spawnTime() + tappable.validFor() > request.timestamp && !redeemedTappables.isRedeemed(tappable.id()))
						.map(tappable -> new ActiveLocation(
								tappable.id(),
								TappablesManager.locationToTileId(tappable.lat(), tappable.lon()),
								new Coordinate(tappable.lat(), tappable.lon()),
								TimeFormatter.formatTime(tappable.spawnTime()),
								TimeFormatter.formatTime(tappable.spawnTime() + tappable.validFor()),
								ActiveLocation.Type.TAPPABLE,
								tappable.icon(),
								new ActiveLocation.Metadata(UUID.randomUUID().toString(), Rarity.valueOf(tappable.rarity().name())),
								new ActiveLocation.TappableMetadata(Rarity.valueOf(tappable.rarity().name())),
								null
						))
						.toArray(ActiveLocation[]::new);

				return Response.okFromJson(new EarthApiResponse<>(new MapBuilder<>().put("activeLocations", activeLocations).put("killSwitchedTileIds", new int[0]).getMap()), EarthApiResponse.class);
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});

		this.addHandler(new Route.Builder(Request.Method.POST, "/tappables/$tileId").build(), request ->
		{
			String tileId = request.getParameter("tileId");

			record TappableRequest(
					@NotNull String id,
					@NotNull Coordinate playerCoordinate
			)
			{
			}
			TappableRequest tappableRequest = request.getBodyAsJson(TappableRequest.class);

			TappablesManager.Tappable tappable = tappablesManager.getTappableWithId(tappableRequest.id, tileId);
			if (tappable == null || tappable.spawnTime() > request.timestamp || tappable.spawnTime() + tappable.validFor() <= request.timestamp) // TODO: check player location is in radius
			{
				return Response.badRequest();
			}

			try
			{
				String playerId = request.getContextData("playerId");
				EarthDB.Results results = new EarthDB.Query(true)
						.get("redeemedTappables", playerId, RedeemedTappables.class)
						.then(results1 ->
						{
							EarthDB.Query query = new EarthDB.Query(true);

							RedeemedTappables redeemedTappables = (RedeemedTappables) results1.get("redeemedTappables").value();

							if (redeemedTappables.isRedeemed(tappable.id()))
							{
								query.extra("success", false);
								return query;
							}

							Rewards rewards = new Rewards();
							rewards.addExperiencePoints(tappable.drops().experiencePoints());
							for (TappablesManager.Tappable.Drops.Item item : tappable.drops().items())
							{
								rewards.addItem(item.id(), item.count());
							}
							rewards.addRubies(1);

							redeemedTappables.add(tappable.id(), tappable.spawnTime() + tappable.validFor());
							redeemedTappables.prune(request.timestamp);

							query.update("redeemedTappables", playerId, redeemedTappables);
							query.then(ActivityLogUtils.addEntry(playerId, new ActivityLog.TappableEntry(request.timestamp, rewards.toDBRewardsModel())));
							query.then(rewards.toRedeemQuery(playerId, request.timestamp, catalog));
							query.then(results2 -> new EarthDB.Query(false).extra("success", true).extra("rewards", rewards));

							return query;
						})
						.execute(earthDB);

				if ((boolean) results.getExtra("success"))
				{
					return Response.okFromJson(new EarthApiResponse<>(new MapBuilder<>()
							.put("token", new Token(
											Token.Type.TAPPABLE,
											new HashMap<>(),
											((Rewards) results.getExtra("rewards")).toApiResponse(),
											Token.Lifetime.PERSISTENT
									)
							)
							.put("updates", null) // TODO: why is there an updates field here and what is it used for? it is null even when the global updates field is not null
							.getMap(), new EarthApiResponse.Updates(results)), EarthApiResponse.class);
				}
				else
				{
					return Response.badRequest();
				}
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});
	}
}