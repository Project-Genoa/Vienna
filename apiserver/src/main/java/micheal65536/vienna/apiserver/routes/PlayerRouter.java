package micheal65536.vienna.apiserver.routes;

import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.apiserver.routes.player.BuildplatesRouter;
import micheal65536.vienna.apiserver.routes.player.ChallengesRouter;
import micheal65536.vienna.apiserver.routes.player.InventoryRouter;
import micheal65536.vienna.apiserver.routes.player.JournalRouter;
import micheal65536.vienna.apiserver.routes.player.ProfileRouter;
import micheal65536.vienna.apiserver.routes.player.TappablesRouter;
import micheal65536.vienna.apiserver.routes.player.TokensRouter;
import micheal65536.vienna.apiserver.routes.player.WorkshopRouter;
import micheal65536.vienna.apiserver.routing.Request;
import micheal65536.vienna.apiserver.routing.Response;
import micheal65536.vienna.apiserver.routing.Router;
import micheal65536.vienna.apiserver.utils.BuildplateInstancesManager;
import micheal65536.vienna.apiserver.utils.EarthApiResponse;
import micheal65536.vienna.apiserver.utils.MapBuilder;
import micheal65536.vienna.apiserver.utils.TappablesManager;
import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.eventbus.client.EventBusClient;
import micheal65536.vienna.objectstore.client.ObjectStoreClient;
import micheal65536.vienna.staticdata.StaticData;

import java.util.HashMap;

public class PlayerRouter extends Router
{
	public PlayerRouter(@NotNull EarthDB earthDB, @NotNull EventBusClient eventBusClient, @NotNull ObjectStoreClient objectStoreClient, @NotNull BuildplateInstancesManager buildplateInstancesManager, @NotNull StaticData staticData)
	{
		// TODO
		this.addHandler(new Route.Builder(Request.Method.GET, "/boosts").build(), request ->
		{
			return Response.okFromJson(new EarthApiResponse<>(new MapBuilder<>().put("potions", new Object[5]).put("miniFigs", new Object[5]).put("miniFigRecords", new HashMap<>()).put("activeEffects", new Object[0]).put("scenarioBoosts", new HashMap<>()).put("expiration", null).put("statusEffects", new MapBuilder<>().put("tappableInteractionRadius", null).put("experiencePointRate", null).put("itemExperiencePointRates", null).put("attackDamageRate", null).put("playerDefenseRate", null).put("blockDamageRate", null).put("maximumPlayerHealth", 20).put("craftingSpeed", null).put("smeltingFuelIntensity", null).put("foodHealthRate", null).getMap()).getMap()), EarthApiResponse.class);
		});

		TappablesManager tappablesManager = new TappablesManager(eventBusClient);

		this.addSubRouter("/*", 0, new ProfileRouter(earthDB, staticData));
		this.addSubRouter("/*", 0, new TokensRouter(earthDB, staticData));
		this.addSubRouter("/*", 0, new InventoryRouter(earthDB, staticData.catalog));
		this.addSubRouter("/*", 0, new WorkshopRouter(earthDB, staticData));
		this.addSubRouter("/*", 0, new JournalRouter(earthDB));
		this.addSubRouter("/*", 0, new BuildplatesRouter(earthDB, objectStoreClient, buildplateInstancesManager, tappablesManager, staticData.catalog));
		this.addSubRouter("/*", 0, new TappablesRouter(earthDB, eventBusClient, tappablesManager, staticData));
		this.addSubRouter("/*", 0, new ChallengesRouter(earthDB));
	}
}