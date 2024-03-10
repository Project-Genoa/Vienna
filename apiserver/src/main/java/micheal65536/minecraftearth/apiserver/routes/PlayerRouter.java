package micheal65536.minecraftearth.apiserver.routes;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import micheal65536.minecraftearth.apiserver.Catalog;
import micheal65536.minecraftearth.apiserver.routes.player.InventoryRouter;
import micheal65536.minecraftearth.apiserver.routes.player.TappablesRouter;
import micheal65536.minecraftearth.apiserver.routes.player.WorkshopRouter;
import micheal65536.minecraftearth.apiserver.routing.Request;
import micheal65536.minecraftearth.apiserver.routing.Response;
import micheal65536.minecraftearth.apiserver.routing.Router;
import micheal65536.minecraftearth.apiserver.types.profile.SplitRubies;
import micheal65536.minecraftearth.apiserver.utils.EarthApiResponse;
import micheal65536.minecraftearth.apiserver.utils.LevelUtils;
import micheal65536.minecraftearth.apiserver.utils.MapBuilder;
import micheal65536.minecraftearth.db.DatabaseException;
import micheal65536.minecraftearth.db.EarthDB;
import micheal65536.minecraftearth.db.model.player.Profile;
import micheal65536.minecraftearth.eventbus.client.EventBusClient;

import java.util.HashMap;
import java.util.stream.IntStream;

public class PlayerRouter extends Router
{
	public PlayerRouter(@NotNull EarthDB earthDB, @NotNull EventBusClient eventBusClient, @NotNull Catalog catalog)
	{
		this.addHandler(new Route.Builder(Request.Method.GET, "/player/rubies").build(), request ->
		{
			try
			{
				Profile profile = (Profile) new EarthDB.Query(false)
						.get("profile", request.getContextData("playerId"), Profile.class)
						.execute(earthDB)
						.get("profile").value();
				return Response.okFromJson(new EarthApiResponse<>(profile.rubies.purchased + profile.rubies.earned), EarthApiResponse.class);
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
				Profile profile = (Profile) new EarthDB.Query(false)
						.get("profile", request.getContextData("playerId"), Profile.class)
						.execute(earthDB)
						.get("profile").value();
				return Response.okFromJson(new EarthApiResponse<>(new SplitRubies(profile.rubies.purchased, profile.rubies.earned)), EarthApiResponse.class);
			}
			catch (DatabaseException exception)
			{
				LogManager.getLogger().error(exception);
				return Response.serverError();
			}
		});

		this.addHandler(new Route.Builder(Request.Method.GET, "/player/profile/$userId").build(), request ->
		{
			try
			{
				Profile profile = (Profile) new EarthDB.Query(false)
						.get("profile", request.getContextData("playerId"), Profile.class)
						.execute(earthDB)
						.get("profile").value();
				LevelUtils.Level[] levels = LevelUtils.getLevels();
				int currentLevelExperience = profile.experience - (profile.level > 1 ? (profile.level - 2 < levels.length ? levels[profile.level - 2].experienceRequired() : levels[levels.length - 1].experienceRequired()) : 0);
				int experienceRemaining = profile.level - 1 < levels.length ? levels[profile.level - 1].experienceRequired() - profile.experience : 0;
				return Response.okFromJson(new EarthApiResponse<>(new micheal65536.minecraftearth.apiserver.types.profile.Profile(
						IntStream.range(0, levels.length).collect(HashMap<Integer, micheal65536.minecraftearth.apiserver.types.profile.Profile.Level>::new, (hashMap, levelIndex) ->
						{
							LevelUtils.Level level = levels[levelIndex];
							hashMap.put(levelIndex + 1, new micheal65536.minecraftearth.apiserver.types.profile.Profile.Level(level.experienceRequired(), level.rewards().toApiResponse()));
						}, HashMap::putAll),
						profile.experience,
						profile.level,
						currentLevelExperience,
						experienceRemaining,
						profile.health,
						((float) profile.health / 20.0f) * 100.0f
				)), EarthApiResponse.class);
			}
			catch (DatabaseException exception)
			{
				LogManager.getLogger().error(exception);
				return Response.serverError();
			}
		});

		// TODO
		this.addHandler(new Route.Builder(Request.Method.GET, "/player/tokens").build(), request ->
		{
			return Response.okFromJson(new EarthApiResponse<>(new MapBuilder<>().put("tokens", new HashMap<>()).getMap()), EarthApiResponse.class);
		});

		// TODO
		this.addHandler(new Route.Builder(Request.Method.GET, "/boosts").build(), request ->
		{
			return Response.okFromJson(new EarthApiResponse<>(new MapBuilder<>().put("potions", new Object[5]).put("miniFigs", new Object[5]).put("miniFigRecords", new HashMap<>()).put("activeEffects", new Object[0]).put("scenarioBoosts", new HashMap<>()).put("expiration", null).put("statusEffects", new MapBuilder<>().put("tappableInteractionRadius", null).put("experiencePointRate", null).put("itemExperiencePointRates", null).put("attackDamageRate", null).put("playerDefenseRate", null).put("blockDamageRate", null).put("maximumPlayerHealth", 20).put("craftingSpeed", null).put("smeltingFuelIntensity", null).put("foodHealthRate", null).getMap()).getMap()), EarthApiResponse.class);
		});

		this.addSubRouter("/*", 0, new InventoryRouter(earthDB));
		this.addSubRouter("/*", 0, new WorkshopRouter(earthDB, catalog));
		this.addSubRouter("/*", 0, new TappablesRouter(earthDB, eventBusClient, catalog));
	}
}