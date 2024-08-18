package micheal65536.vienna.apiserver.routes.player;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.apiserver.routing.Request;
import micheal65536.vienna.apiserver.routing.Response;
import micheal65536.vienna.apiserver.routing.Router;
import micheal65536.vienna.apiserver.routing.ServerErrorException;
import micheal65536.vienna.apiserver.types.profile.SplitRubies;
import micheal65536.vienna.apiserver.utils.BoostUtils;
import micheal65536.vienna.apiserver.utils.EarthApiResponse;
import micheal65536.vienna.apiserver.utils.LevelUtils;
import micheal65536.vienna.db.DatabaseException;
import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.db.model.player.Boosts;
import micheal65536.vienna.db.model.player.Profile;
import micheal65536.vienna.staticdata.Levels;
import micheal65536.vienna.staticdata.StaticData;

import java.util.HashMap;
import java.util.Locale;
import java.util.stream.IntStream;

public class ProfileRouter extends Router
{
	public ProfileRouter(@NotNull EarthDB earthDB, @NotNull StaticData staticData)
	{
		this.addHandler(new Route.Builder(Request.Method.GET, "/player/profile/$userId").build(), request ->
		{
			// TODO: decide if we should allow requests for profiles of other players
			String userId = request.getParameter("userId").toLowerCase(Locale.ROOT);

			try
			{
				EarthDB.Results results = new EarthDB.Query(false)
						.get("profile", userId, Profile.class)
						.get("boosts", userId, Boosts.class)
						.execute(earthDB);

				Profile profile = (Profile) results.get("profile").value();
				Boosts boosts = (Boosts) results.get("boosts").value();

				Levels.Level[] levels = staticData.levels.levels;
				int currentLevelExperience = profile.experience - (profile.level > 1 ? (profile.level - 2 < levels.length ? levels[profile.level - 2].experienceRequired() : levels[levels.length - 1].experienceRequired()) : 0);
				int experienceRemaining = profile.level - 1 < levels.length ? levels[profile.level - 1].experienceRequired() - profile.experience : 0;

				int maxPlayerHealth = BoostUtils.getMaxPlayerHealth(boosts, request.timestamp, staticData.catalog.itemsCatalog);
				if (profile.health > maxPlayerHealth)
				{
					profile.health = maxPlayerHealth;
				}

				return Response.okFromJson(new EarthApiResponse<>(new micheal65536.vienna.apiserver.types.profile.Profile(
						IntStream.range(0, levels.length).collect(HashMap<Integer, micheal65536.vienna.apiserver.types.profile.Profile.Level>::new, (hashMap, levelIndex) ->
						{
							Levels.Level level = levels[levelIndex];
							hashMap.put(levelIndex + 1, new micheal65536.vienna.apiserver.types.profile.Profile.Level(level.experienceRequired(), LevelUtils.makeLevelRewards(level).toApiResponse()));
						}, HashMap::putAll),
						profile.experience,
						profile.level,
						currentLevelExperience,
						experienceRemaining,
						profile.health,
						((float) profile.health / (float) maxPlayerHealth) * 100.0f
				)), EarthApiResponse.class);
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});

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

		// required for the language selection option in the client to work
		this.addHandler(new Route.Builder(Request.Method.POST, "/player/profile/language").build(), request ->
		{
			return Response.create(200);
		});
	}
}