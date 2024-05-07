package micheal65536.vienna.apiserver.routes.player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.apiserver.routing.Request;
import micheal65536.vienna.apiserver.routing.Response;
import micheal65536.vienna.apiserver.routing.Router;
import micheal65536.vienna.apiserver.types.common.Rarity;
import micheal65536.vienna.apiserver.types.common.Rewards;
import micheal65536.vienna.apiserver.utils.EarthApiResponse;
import micheal65536.vienna.apiserver.utils.MapBuilder;
import micheal65536.vienna.apiserver.utils.TimeFormatter;
import micheal65536.vienna.db.EarthDB;

public class ChallengesRouter extends Router
{
	public ChallengesRouter(@NotNull EarthDB earthDB)
	{
		this.addHandler(new Route.Builder(Request.Method.GET, "/player/challenges").build(), request ->
		{
			// TODO: this is currently just a stub required for the journal to load properly in the client
			record Challenge(
					@NotNull String referenceId,
					@Nullable String parentId,
					@NotNull String groupId,
					@NotNull String duration,
					@NotNull String type,
					@NotNull String category,
					@Nullable Rarity rarity,
					int order,
					@NotNull String endTimeUtc,
					@NotNull String state,
					boolean isComplete,
					int percentComplete,
					int currentCount,
					int totalThreshold,
					@NotNull String[] prerequisiteIds,
					@NotNull String prerequisiteLogicalCondition,
					@NotNull Rewards rewards,
					@NotNull Object clientProperties
			)
			{
			}
			return Response.okFromJson(new EarthApiResponse<>(new MapBuilder<>()
					.put("challenges", new MapBuilder<String, Challenge>()
							// client requires two season challenges with these specific persona item reward UUIDs to exist in order for the journal to load, and no one has any idea why
							.put("00000000-0000-0000-0000-000000000001", new Challenge(
									"00000000-0000-0000-0000-000000000001",
									null,
									"00000000-0000-0000-0000-000000000001",
									"Season",
									"Regular",
									"season_1",
									null,
									0,
									TimeFormatter.formatTime(System.currentTimeMillis() + 24 * 60 * 60 * 1000),
									"Locked",
									false,
									0,
									0,
									1,
									new String[0],
									"And",
									new Rewards(null, null, null, new Rewards.Item[0], new Rewards.Buildplate[0], new Rewards.Challenge[0], new String[]{"230f5996-04b2-4f0e-83e5-4056c7f1d946"}, new Rewards.UtilityBlock[0]),
									new Object()
							))
							.put("00000000-0000-0000-0000-000000000002", new Challenge(
									"00000000-0000-0000-0000-000000000002",
									null,
									"00000000-0000-0000-0000-000000000001",
									"Season",
									"Regular",
									"season_1",
									null,
									0,
									TimeFormatter.formatTime(System.currentTimeMillis() + 24 * 60 * 60 * 1000),
									"Locked",
									false,
									0,
									0,
									1,
									new String[0],
									"And",
									new Rewards(null, null, null, new Rewards.Item[0], new Rewards.Buildplate[0], new Rewards.Challenge[0], new String[]{"d7725840-4376-44fc-9220-585f45775371"}, new Rewards.UtilityBlock[0]),
									new Object()
							))
							.getMap()
					)
					.put("activeSeasonChallenge", "00000000-0000-0000-0000-000000000000")
					.getMap()
			), EarthApiResponse.class);
		});
	}
}