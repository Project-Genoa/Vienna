package micheal65536.vienna.apiserver.routes.player;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.apiserver.routing.Request;
import micheal65536.vienna.apiserver.routing.Response;
import micheal65536.vienna.apiserver.routing.Router;
import micheal65536.vienna.apiserver.routing.ServerErrorException;
import micheal65536.vienna.apiserver.types.common.Effect;
import micheal65536.vienna.apiserver.utils.ActivityLogUtils;
import micheal65536.vienna.apiserver.utils.BoostUtils;
import micheal65536.vienna.apiserver.utils.EarthApiResponse;
import micheal65536.vienna.apiserver.utils.TimeFormatter;
import micheal65536.vienna.db.DatabaseException;
import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.db.model.player.ActivityLog;
import micheal65536.vienna.db.model.player.Boosts;
import micheal65536.vienna.db.model.player.Inventory;
import micheal65536.vienna.db.model.player.Profile;
import micheal65536.vienna.staticdata.Catalog;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.UUID;

public class BoostsRouter extends Router
{
	public BoostsRouter(@NotNull EarthDB earthDB, @NotNull Catalog catalog)
	{
		this.addHandler(new Route.Builder(Request.Method.GET, "/boosts").build(), request ->
		{
			String playerId = request.getContextData("playerId");
			EarthDB.Results results;
			try
			{
				results = new EarthDB.Query(true)
						.get("boosts", playerId, Boosts.class)
						.get("profile", playerId, Profile.class)
						.then(results1 ->
						{
							// I know this is ugly, we're making changes to the database in response to a GET request, but if we don't then the client won't correctly update the player health bar in the UI

							Boosts boosts = (Boosts) results1.get("boosts").value();
							Profile profile = (Profile) results1.get("profile").value();

							if (pruneBoostsAndUpdateProfile(boosts, profile, request.timestamp, catalog.itemsCatalog))
							{
								return new EarthDB.Query(true)
										.update("boosts", playerId, boosts)
										.update("profile", playerId, profile)
										.extra("boosts", boosts);
							}
							else
							{
								return new EarthDB.Query(false)
										.extra("boosts", boosts);
							}
						})
						.execute(earthDB);
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
			Boosts boosts = (Boosts) results.getExtra("boosts");

			micheal65536.vienna.apiserver.types.boosts.Boosts.Potion[] potions = Arrays.stream(boosts.activeBoosts).map(activeBoost ->
			{
				if (activeBoost == null)
				{
					return null;
				}
				else
				{
					return new micheal65536.vienna.apiserver.types.boosts.Boosts.Potion(true, activeBoost.itemId(), activeBoost.instanceId(), TimeFormatter.formatTime(activeBoost.startTime() + activeBoost.duration()));
				}
			}).toArray(micheal65536.vienna.apiserver.types.boosts.Boosts.Potion[]::new);

			record ActiveBoostInfo(
					@NotNull Boosts.ActiveBoost activeBoost,
					@NotNull Catalog.ItemsCatalog.Item.BoostInfo boostInfo
			)
			{
			}
			LinkedHashMap<String, ActiveBoostInfo> activeBoostsWithInfo = new LinkedHashMap<>();
			for (Boosts.ActiveBoost activeBoost : boosts.activeBoosts)
			{
				if (activeBoost == null)
				{
					continue;
				}

				Catalog.ItemsCatalog.Item item = catalog.itemsCatalog.getItem(activeBoost.itemId());
				if (item == null || item.boostInfo() == null)
				{
					continue;
				}

				ActiveBoostInfo existingActiveBoostInfo = activeBoostsWithInfo.getOrDefault(item.boostInfo().name(), null);
				if (existingActiveBoostInfo != null && existingActiveBoostInfo.boostInfo.level() > item.boostInfo().level())
				{
					continue;
				}

				activeBoostsWithInfo.put(item.boostInfo().name(), new ActiveBoostInfo(activeBoost, item.boostInfo()));
			}

			LinkedList<micheal65536.vienna.apiserver.types.boosts.Boosts.ActiveEffect> activeEffects = new LinkedList<>();
			LinkedList<micheal65536.vienna.apiserver.types.boosts.Boosts.ScenarioBoost> triggeredOnDeathBoosts = new LinkedList<>();
			for (ActiveBoostInfo activeBoostInfo : activeBoostsWithInfo.values())
			{
				if (!activeBoostInfo.boostInfo.triggeredOnDeath())
				{
					for (Catalog.ItemsCatalog.Item.BoostInfo.Effect effect : activeBoostInfo.boostInfo.effects())
					{
						if (effect.activation() != Catalog.ItemsCatalog.Item.BoostInfo.Effect.Activation.TIMED)
						{
							LogManager.getLogger().warn("Active boost {} has effect with activation {}", activeBoostInfo.activeBoost.itemId(), effect.activation());
							continue;
						}

						activeEffects.add(new micheal65536.vienna.apiserver.types.boosts.Boosts.ActiveEffect(BoostUtils.boostEffectToApiResponse(effect, activeBoostInfo.activeBoost.duration()), TimeFormatter.formatTime(activeBoostInfo.activeBoost.startTime() + activeBoostInfo.activeBoost.duration())));
					}
				}
				else
				{
					LinkedList<Effect> effects = new LinkedList<>();
					for (Catalog.ItemsCatalog.Item.BoostInfo.Effect effect : activeBoostInfo.boostInfo.effects())
					{
						if (effect.activation() != Catalog.ItemsCatalog.Item.BoostInfo.Effect.Activation.TRIGGERED)
						{
							LogManager.getLogger().warn("Active boost {} has effect with activation {}", activeBoostInfo.activeBoost.itemId(), effect.activation());
							continue;
						}

						effects.add(BoostUtils.boostEffectToApiResponse(effect, activeBoostInfo.activeBoost.duration()));
					}
					triggeredOnDeathBoosts.add(new micheal65536.vienna.apiserver.types.boosts.Boosts.ScenarioBoost(true, activeBoostInfo.activeBoost.instanceId(), effects.toArray(Effect[]::new), TimeFormatter.formatTime(activeBoostInfo.activeBoost.startTime() + activeBoostInfo.activeBoost.duration())));
				}
			}

			HashMap<String, micheal65536.vienna.apiserver.types.boosts.Boosts.ScenarioBoost[]> scenarioBoosts = new HashMap<>();
			if (!triggeredOnDeathBoosts.isEmpty())
			{
				scenarioBoosts.put("death", triggeredOnDeathBoosts.toArray(micheal65536.vienna.apiserver.types.boosts.Boosts.ScenarioBoost[]::new));
			}

			BoostUtils.StatModiferValues statModiferValues = BoostUtils.getActiveStatModifiers(boosts, request.timestamp, catalog.itemsCatalog);

			micheal65536.vienna.apiserver.types.boosts.Boosts boostsResponse = new micheal65536.vienna.apiserver.types.boosts.Boosts(
					potions,
					new micheal65536.vienna.apiserver.types.boosts.Boosts.MiniFig[5],
					activeEffects.toArray(micheal65536.vienna.apiserver.types.boosts.Boosts.ActiveEffect[]::new),
					scenarioBoosts,
					new micheal65536.vienna.apiserver.types.boosts.Boosts.StatusEffects(
							statModiferValues.tappableInteractionRadiusExtraMeters() > 0 ? statModiferValues.tappableInteractionRadiusExtraMeters() + 70 : null,
							null,
							null,
							statModiferValues.attackMultiplier() > 0 ? statModiferValues.attackMultiplier() + 100 : null,
							statModiferValues.defenseMultiplier() > 0 ? statModiferValues.defenseMultiplier() + 100 : null,
							statModiferValues.miningSpeedMultiplier() > 0 ? statModiferValues.miningSpeedMultiplier() + 100 : null,
							statModiferValues.maxPlayerHealthMultiplier() > 0 ? (20 * statModiferValues.maxPlayerHealthMultiplier()) / 100 + 20 : 20,
							statModiferValues.craftingSpeedMultiplier() > 0 ? statModiferValues.craftingSpeedMultiplier() / 100 + 1 : null,
							statModiferValues.smeltingSpeedMultiplier() > 0 ? statModiferValues.smeltingSpeedMultiplier() / 100 + 1 : null,
							statModiferValues.foodMultiplier() > 0 ? (statModiferValues.foodMultiplier() + 100) / 100.0f : null
					),
					new HashMap<>(),
					!activeBoostsWithInfo.isEmpty() ? TimeFormatter.formatTime(activeBoostsWithInfo.values().stream().mapToLong(activeBoostInfo -> activeBoostInfo.activeBoost.startTime() + activeBoostInfo.activeBoost.duration()).min().orElseThrow()) : null
			);
			return Response.okFromJson(new EarthApiResponse<>(boostsResponse, new EarthApiResponse.Updates(results)), EarthApiResponse.class);
		});

		this.addHandler(new Route.Builder(Request.Method.POST, "/boosts/potions/$itemId/activate").build(), request ->
		{
			String playerId = request.getContextData("playerId");
			String itemId = request.getParameter("itemId");

			Catalog.ItemsCatalog.Item item = catalog.itemsCatalog.getItem(itemId);
			if (item == null || item.boostInfo() == null || item.boostInfo().type() != Catalog.ItemsCatalog.Item.BoostInfo.Type.POTION)
			{
				return Response.badRequest();
			}

			try
			{
				EarthDB.Results results = new EarthDB.Query(true)
						.get("inventory", playerId, Inventory.class)
						.get("boosts", playerId, Boosts.class)
						.get("profile", playerId, Profile.class)
						.then(results1 ->
						{
							Inventory inventory = (Inventory) results1.get("inventory").value();
							Boosts boosts = (Boosts) results1.get("boosts").value();
							Profile profile = (Profile) results1.get("profile").value();
							boolean profileChanged = false;

							if (pruneBoostsAndUpdateProfile(boosts, profile, request.timestamp, catalog.itemsCatalog))
							{
								profileChanged = true;
							}

							if (!inventory.takeItems(itemId, 1))
							{
								return new EarthDB.Query(false);
							}

							int newIndex = -1;
							boolean extendExisting = false;
							for (int index = 0; index < boosts.activeBoosts.length; index++)
							{
								if (boosts.activeBoosts[index] != null && boosts.activeBoosts[index].itemId().equals(itemId))
								{
									newIndex = index;
									extendExisting = true;
									break;
								}
							}
							if (!extendExisting)
							{
								for (int index = 0; index < boosts.activeBoosts.length; index++)
								{
									if (boosts.activeBoosts[index] == null)
									{
										newIndex = index;
										break;
									}
								}
							}
							if (newIndex == -1)
							{
								return new EarthDB.Query(false);
							}

							if (extendExisting)
							{
								Boosts.ActiveBoost existingBoost = boosts.activeBoosts[newIndex];
								boosts.activeBoosts[newIndex] = new Boosts.ActiveBoost(existingBoost.instanceId(), existingBoost.itemId(), existingBoost.startTime(), existingBoost.duration() + item.boostInfo().duration());
							}
							else
							{
								boosts.activeBoosts[newIndex] = new Boosts.ActiveBoost(UUID.randomUUID().toString(), itemId, request.timestamp, item.boostInfo().duration());

								if (Arrays.stream(item.boostInfo().effects()).anyMatch(effect -> effect.type() == Catalog.ItemsCatalog.Item.BoostInfo.Effect.Type.HEALTH))
								{
									// TODO: determine if we should add new player health straight away
									profileChanged = true;
								}
							}

							EarthDB.Query updateQuery = new EarthDB.Query(true);
							updateQuery.update("inventory", playerId, inventory);
							updateQuery.update("boosts", playerId, boosts);
							if (profileChanged)
							{
								updateQuery.update("profile", playerId, profile);
							}
							updateQuery.then(ActivityLogUtils.addEntry(playerId, new ActivityLog.BoostActivatedEntry(request.timestamp, itemId)));
							return updateQuery;
						})
						.execute(earthDB);
				return Response.okFromJson(new EarthApiResponse<>(null, new EarthApiResponse.Updates(results)), EarthApiResponse.class);
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});

		this.addHandler(new Route.Builder(Request.Method.DELETE, "/boosts/$instanceId").build(), request ->
		{
			String playerId = request.getContextData("playerId");
			String instanceId = request.getParameter("instanceId");

			try
			{
				EarthDB.Results results = new EarthDB.Query(true)
						.get("boosts", playerId, Boosts.class)
						.get("profile", playerId, Profile.class)
						.then(results1 ->
						{
							Boosts boosts = (Boosts) results1.get("boosts").value();
							Profile profile = (Profile) results1.get("profile").value();
							boolean profileChanged = false;

							if (pruneBoostsAndUpdateProfile(boosts, profile, request.timestamp, catalog.itemsCatalog))
							{
								profileChanged = true;
							}

							Boosts.ActiveBoost activeBoost = boosts.get(instanceId);
							if (activeBoost == null)
							{
								return new EarthDB.Query(false);
							}

							Catalog.ItemsCatalog.Item item = catalog.itemsCatalog.getItem(activeBoost.itemId());
							if (item == null || item.boostInfo() == null || !item.boostInfo().canBeRemoved())
							{
								return new EarthDB.Query(false);
							}

							for (int index = 0; index < boosts.activeBoosts.length; index++)
							{
								if (boosts.activeBoosts[index] != null && boosts.activeBoosts[index].instanceId().equals(instanceId))
								{
									boosts.activeBoosts[index] = null;
								}
							}

							if (Arrays.stream(item.boostInfo().effects()).anyMatch(effect -> effect.type() == Catalog.ItemsCatalog.Item.BoostInfo.Effect.Type.HEALTH))
							{
								profileChanged = true;
								int maxPlayerHealth = BoostUtils.getMaxPlayerHealth(boosts, request.timestamp, catalog.itemsCatalog);
								if (profile.health > maxPlayerHealth)
								{
									profile.health = maxPlayerHealth;
								}
							}

							EarthDB.Query updateQuery = new EarthDB.Query(true);
							updateQuery.update("boosts", playerId, boosts);
							if (profileChanged)
							{
								updateQuery.update("profile", playerId, profile);
							}
							return updateQuery;
						})
						.execute(earthDB);
				return Response.okFromJson(new EarthApiResponse<>(null, new EarthApiResponse.Updates(results)), EarthApiResponse.class);
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});
	}

	private static boolean pruneBoostsAndUpdateProfile(@NotNull Boosts boosts, @NotNull Profile profile, long currentTime, @NotNull Catalog.ItemsCatalog itemsCatalog)
	{
		boolean profileChanged = false;
		Boosts.ActiveBoost[] prunedBoosts = boosts.prune(currentTime);
		if (Arrays.stream(prunedBoosts).flatMap(activeBoost -> Arrays.stream(itemsCatalog.getItem(activeBoost.itemId()).boostInfo().effects())).anyMatch(effect -> effect.type() == Catalog.ItemsCatalog.Item.BoostInfo.Effect.Type.HEALTH))
		{
			profileChanged = true;
		}
		int maxPlayerHealth = BoostUtils.getMaxPlayerHealth(boosts, currentTime, itemsCatalog);
		if (profile.health > maxPlayerHealth)
		{
			profile.health = maxPlayerHealth;
			profileChanged = true;
		}
		return profileChanged;
	}
}