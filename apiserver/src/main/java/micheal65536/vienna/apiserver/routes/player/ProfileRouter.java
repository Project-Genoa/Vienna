package micheal65536.vienna.apiserver.routes.player;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.apiserver.routing.Request;
import micheal65536.vienna.apiserver.routing.Response;
import micheal65536.vienna.apiserver.routing.Router;
import micheal65536.vienna.apiserver.types.profile.SplitRubies;
import micheal65536.vienna.apiserver.utils.EarthApiResponse;
import micheal65536.vienna.apiserver.utils.LevelUtils;
import micheal65536.vienna.db.DatabaseException;
import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.db.model.player.Profile;

import java.util.HashMap;
import java.util.Locale;
import java.util.stream.IntStream;

public class ProfileRouter extends Router
{
	public ProfileRouter(@NotNull EarthDB earthDB)
	{
		this.addHandler(new Route.Builder(Request.Method.GET, "/player/profile/$userId").build(), request ->
		{
			// TODO: decide if we should allow requests for profiles of other players
			try
			{
				Profile profile = (Profile) new EarthDB.Query(false)
						.get("profile", request.getParameter("userId").toLowerCase(Locale.ROOT), Profile.class)
						.execute(earthDB)
						.get("profile").value();
				LevelUtils.Level[] levels = LevelUtils.getLevels();
				int currentLevelExperience = profile.experience - (profile.level > 1 ? (profile.level - 2 < levels.length ? levels[profile.level - 2].experienceRequired() : levels[levels.length - 1].experienceRequired()) : 0);
				int experienceRemaining = profile.level - 1 < levels.length ? levels[profile.level - 1].experienceRequired() - profile.experience : 0;
				return Response.okFromJson(new EarthApiResponse<>(new micheal65536.vienna.apiserver.types.profile.Profile(
						IntStream.range(0, levels.length).collect(HashMap<Integer, micheal65536.vienna.apiserver.types.profile.Profile.Level>::new, (hashMap, levelIndex) ->
						{
							LevelUtils.Level level = levels[levelIndex];
							hashMap.put(levelIndex + 1, new micheal65536.vienna.apiserver.types.profile.Profile.Level(level.experienceRequired(), level.rewards().toApiResponse()));
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
	}
}