package micheal65536.minecraftearth.apiserver.routes;

import micheal65536.minecraftearth.apiserver.routing.Request;
import micheal65536.minecraftearth.apiserver.routing.Response;
import micheal65536.minecraftearth.apiserver.routing.Router;
import micheal65536.minecraftearth.apiserver.utils.EarthApiResponse;
import micheal65536.minecraftearth.apiserver.utils.MapBuilder;

public class EnvironmentSettingsRouter extends Router
{
	public EnvironmentSettingsRouter()
	{
		this.addHandler(new Route.Builder(Request.Method.GET, "/features").build(), request -> Response.okFromJson(new EarthApiResponse<>(new MapBuilder<>()
				.put("workshop_enabled", true)
				.put("buildplates_enabled", true)
				.put("enable_ruby_purchasing", true)
				.put("commerce_enabled", true)
				.put("full_logging_enabled", true)
				.put("challenges_enabled", true)
				.put("craftingv2_enabled", true)
				.put("smeltingv2_enabled", true)
				.put("inventory_item_boosts_enabled", true)
				.put("player_health_enabled", true)
				.put("minifigs_enabled", true)
				.put("potions_enabled", true)
				.put("social_link_launch_enabled", true)
				.put("social_link_share_enabled", true)
				.put("encoded_join_enabled", true)
				.put("adventure_crystals_enabled", true)
				.put("item_limits_enabled", true)
				.put("adventure_crystals_ftue_enabled", true)
				.put("expire_crystals_on_cleanup_enabled", true)
				.put("challenges_v2_enabled", true)
				.put("player_journal_enabled", true)
				.put("player_stats_enabled", true)
				.put("activity_log_enabled", true)
				.put("seasons_enabled", true)
				.put("daily_login_enabled", true)
				.put("store_pdp_enabled", true)
				.put("hotbar_stacksplitting_enabled", true)
				.put("fancy_rewards_screen_enabled", true)
				.put("async_ecs_dispatcher", true)
				.put("adventure_oobe_enabled", true)
				.put("tappable_oobe_enabled", true)
				.put("map_permission_oobe_enabled", true)
				.put("journal_oobe_enabled", true)
				.put("freedom_oobe_enabled", true)
				.put("challenge_oobe_enabled", true)
				.put("level_rewards_v2_enabled", true)
				.put("content_driven_season_assets", true)
				.put("paid_earned_rubies_enabled", true)
				.getMap()
		), EarthApiResponse.class));

		this.addHandler(new Route.Builder(Request.Method.GET, "/settings").build(), request -> Response.okFromJson(new EarthApiResponse<>(new MapBuilder<>()
				.put("encounterinteractionradius", 40)
				.put("tappableinteractionradius", 70)
				.put("tappablevisibleradius", -5)
				.put("targetpossibletappables", 100)
				.put("tile0", 10537)
				.put("slowrequesttimeout", 2500)
				.put("cullingradius", 50)
				.put("commontapcount", 3)
				.put("epictapcount", 7)
				.put("speedwarningcooldown", 3600)
				.put("mintappablesrequiredpertile", 22)
				.put("targetactivetappables", 30)
				.put("tappablecullingradius", 500)
				.put("raretapcount", 5)
				.put("requestwarningtimeout", 10000)
				.put("speedwarningthreshold", 11.176f)
				.put("asaanchormaxplaneheightthreshold", 0.5f)
				.put("maxannouncementscount", 0)
				.put("removethislater", 23)
				.put("crystalslotcap", 3)
				.put("crystaluncommonduration", 10)
				.put("crystalrareduration", 10)
				.put("crystalepicduration", 10)
				.put("crystalcommonduration", 10)
				.put("crystallegendaryduration", 10)
				.put("maximumpersonaltimedchallenges", 3)
				.put("maximumpersonalcontinuouschallenges", 3)
				.getMap()
		), EarthApiResponse.class));
	}
}