package micheal65536.minecraftearth.apiserver.routes;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import micheal65536.minecraftearth.apiserver.Catalog;
import micheal65536.minecraftearth.apiserver.routes.player.InventoryRouter;
import micheal65536.minecraftearth.apiserver.routes.player.WorkshopRouter;
import micheal65536.minecraftearth.apiserver.routing.Request;
import micheal65536.minecraftearth.apiserver.routing.Response;
import micheal65536.minecraftearth.apiserver.routing.Router;
import micheal65536.minecraftearth.apiserver.types.common.SplitRubies;
import micheal65536.minecraftearth.apiserver.utils.EarthApiResponse;
import micheal65536.minecraftearth.apiserver.utils.MapBuilder;
import micheal65536.minecraftearth.db.DatabaseException;
import micheal65536.minecraftearth.db.EarthDB;
import micheal65536.minecraftearth.db.model.player.Rubies;

import java.util.HashMap;

public class PlayerRouter extends Router
{
	public PlayerRouter(@NotNull EarthDB earthDB, @NotNull Catalog catalog)
	{
		this.addHandler(new Route.Builder(Request.Method.GET, "/player/rubies").build(), request ->
		{
			try
			{
				Rubies rubies = (Rubies) new EarthDB.Query(false)
						.get("rubies", request.getContextData("playerId"), Rubies.class)
						.execute(earthDB)
						.get("rubies").value();
				return Response.okFromJson(new EarthApiResponse<>(rubies.purchased + rubies.earned), EarthApiResponse.class);
			}
			catch (DatabaseException exception)
			{
				LogManager.getLogger().error(exception);
				return Response.serverError();
			}
		});
		this.addHandler(new Route.Builder(Request.Method.GET, "/player/splitRubies").build(), request ->
		{
			try
			{
				Rubies rubies = (Rubies) new EarthDB.Query(false)
						.get("rubies", request.getContextData("playerId"), Rubies.class)
						.execute(earthDB)
						.get("rubies").value();
				return Response.okFromJson(new EarthApiResponse<>(new SplitRubies(rubies.purchased, rubies.earned)), EarthApiResponse.class);
			}
			catch (DatabaseException exception)
			{
				LogManager.getLogger().error(exception);
				return Response.serverError();
			}
		});

		// TODO
		this.addHandler(new Route.Builder(Request.Method.GET, "/player/profile/$userId").build(), request ->
		{
			return Response.okFromJson(new EarthApiResponse<>(new MapBuilder<>().put("levelDistribution", new MapBuilder<>().put("2", new MapBuilder<>().put("experienceRequired", 500).put("inventory", new Object[0]).put("rubies", 1).put("buildplates", new Object[0]).put("challenges", new Object[0]).put("personaItems", new Object[0]).put("utilityBlocks", new Object[0]).getMap()).getMap()).put("totalExperience", 0).put("level", 1).put("currentLevelExperience", 0).put("experienceRemaining", 500).put("health", 20).put("healthPercentage", 100).getMap()), EarthApiResponse.class);
		});

		// TODO
		this.addHandler(new Route.Builder(Request.Method.GET, "/player/tokens").build(), request ->
		{
			return Response.okFromJson(new EarthApiResponse<>(new MapBuilder<>().put("tokens", new HashMap<>()).getMap()), EarthApiResponse.class);
		});

		this.addSubRouter("/*", 0, new InventoryRouter(earthDB));
		this.addSubRouter("/*", 0, new WorkshopRouter(earthDB, catalog));
	}
}