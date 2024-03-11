package micheal65536.minecraftearth.apiserver.routes;

import org.jetbrains.annotations.NotNull;

import micheal65536.minecraftearth.apiserver.Catalog;
import micheal65536.minecraftearth.apiserver.routes.player.InventoryRouter;
import micheal65536.minecraftearth.apiserver.routes.player.ProfileRouter;
import micheal65536.minecraftearth.apiserver.routes.player.TappablesRouter;
import micheal65536.minecraftearth.apiserver.routes.player.TokensRouter;
import micheal65536.minecraftearth.apiserver.routes.player.WorkshopRouter;
import micheal65536.minecraftearth.apiserver.routing.Request;
import micheal65536.minecraftearth.apiserver.routing.Response;
import micheal65536.minecraftearth.apiserver.routing.Router;
import micheal65536.minecraftearth.apiserver.utils.EarthApiResponse;
import micheal65536.minecraftearth.apiserver.utils.MapBuilder;
import micheal65536.minecraftearth.db.EarthDB;
import micheal65536.minecraftearth.eventbus.client.EventBusClient;

import java.util.HashMap;

public class PlayerRouter extends Router
{
	public PlayerRouter(@NotNull EarthDB earthDB, @NotNull EventBusClient eventBusClient, @NotNull Catalog catalog)
	{
		// TODO
		this.addHandler(new Route.Builder(Request.Method.GET, "/boosts").build(), request ->
		{
			return Response.okFromJson(new EarthApiResponse<>(new MapBuilder<>().put("potions", new Object[5]).put("miniFigs", new Object[5]).put("miniFigRecords", new HashMap<>()).put("activeEffects", new Object[0]).put("scenarioBoosts", new HashMap<>()).put("expiration", null).put("statusEffects", new MapBuilder<>().put("tappableInteractionRadius", null).put("experiencePointRate", null).put("itemExperiencePointRates", null).put("attackDamageRate", null).put("playerDefenseRate", null).put("blockDamageRate", null).put("maximumPlayerHealth", 20).put("craftingSpeed", null).put("smeltingFuelIntensity", null).put("foodHealthRate", null).getMap()).getMap()), EarthApiResponse.class);
		});

		this.addSubRouter("/*", 0, new ProfileRouter(earthDB));
		this.addSubRouter("/*", 0, new TokensRouter(earthDB));
		this.addSubRouter("/*", 0, new InventoryRouter(earthDB));
		this.addSubRouter("/*", 0, new WorkshopRouter(earthDB, catalog));
		this.addSubRouter("/*", 0, new TappablesRouter(earthDB, eventBusClient, catalog));
	}
}