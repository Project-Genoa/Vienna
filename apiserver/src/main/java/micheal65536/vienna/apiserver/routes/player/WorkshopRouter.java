package micheal65536.vienna.apiserver.routes.player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.vienna.apiserver.routing.Request;
import micheal65536.vienna.apiserver.routing.Response;
import micheal65536.vienna.apiserver.routing.Router;
import micheal65536.vienna.apiserver.routing.ServerErrorException;
import micheal65536.vienna.apiserver.types.common.BurnRate;
import micheal65536.vienna.apiserver.types.common.ExpectedPurchasePrice;
import micheal65536.vienna.apiserver.types.profile.SplitRubies;
import micheal65536.vienna.apiserver.types.workshop.FinishPrice;
import micheal65536.vienna.apiserver.types.workshop.OutputItem;
import micheal65536.vienna.apiserver.types.workshop.State;
import micheal65536.vienna.apiserver.types.workshop.UnlockPrice;
import micheal65536.vienna.apiserver.utils.ActivityLogUtils;
import micheal65536.vienna.apiserver.utils.CraftingCalculator;
import micheal65536.vienna.apiserver.utils.EarthApiResponse;
import micheal65536.vienna.apiserver.utils.MapBuilder;
import micheal65536.vienna.apiserver.utils.Rewards;
import micheal65536.vienna.apiserver.utils.SmeltingCalculator;
import micheal65536.vienna.apiserver.utils.TimeFormatter;
import micheal65536.vienna.db.DatabaseException;
import micheal65536.vienna.db.EarthDB;
import micheal65536.vienna.db.model.common.NonStackableItemInstance;
import micheal65536.vienna.db.model.player.ActivityLog;
import micheal65536.vienna.db.model.player.Hotbar;
import micheal65536.vienna.db.model.player.Inventory;
import micheal65536.vienna.db.model.player.Journal;
import micheal65536.vienna.db.model.player.Profile;
import micheal65536.vienna.db.model.player.workshop.CraftingSlot;
import micheal65536.vienna.db.model.player.workshop.CraftingSlots;
import micheal65536.vienna.db.model.player.workshop.InputItem;
import micheal65536.vienna.db.model.player.workshop.SmeltingSlot;
import micheal65536.vienna.db.model.player.workshop.SmeltingSlots;
import micheal65536.vienna.staticdata.Catalog;
import micheal65536.vienna.staticdata.StaticData;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

public class WorkshopRouter extends Router
{
	private final StaticData staticData;

	public WorkshopRouter(@NotNull EarthDB earthDB, @NotNull StaticData staticData)
	{
		this.staticData = staticData;

		this.addHandler(new Route.Builder(Request.Method.GET, "/player/utilityBlocks").build(), request ->
		{
			String playerId = request.getContextData("playerId");
			EarthDB.Results.Result<CraftingSlots> craftingSlotsResult;
			EarthDB.Results.Result<SmeltingSlots> smeltingSlotsResult;
			try
			{
				EarthDB.Results results = new EarthDB.Query(false)
						.get("crafting", playerId, CraftingSlots.class)
						.get("smelting", playerId, SmeltingSlots.class)
						.execute(earthDB);
				craftingSlotsResult = results.get("crafting");
				smeltingSlotsResult = results.get("smelting");
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}

			HashMap<String, Object> workshop = new HashMap<>();
			workshop.put("crafting", new MapBuilder<>()
					.put("1", this.craftingSlotModelToResponseIncludingLocked(craftingSlotsResult.value().slots[0], request.timestamp, craftingSlotsResult.version(), 1))
					.put("2", this.craftingSlotModelToResponseIncludingLocked(craftingSlotsResult.value().slots[1], request.timestamp, craftingSlotsResult.version(), 2))
					.put("3", this.craftingSlotModelToResponseIncludingLocked(craftingSlotsResult.value().slots[2], request.timestamp, craftingSlotsResult.version(), 3))
					.getMap());
			workshop.put("smelting", new MapBuilder<>()
					.put("1", this.smeltingSlotModelToResponseIncludingLocked(smeltingSlotsResult.value().slots[0], request.timestamp, smeltingSlotsResult.version(), 1))
					.put("2", this.smeltingSlotModelToResponseIncludingLocked(smeltingSlotsResult.value().slots[1], request.timestamp, smeltingSlotsResult.version(), 2))
					.put("3", this.smeltingSlotModelToResponseIncludingLocked(smeltingSlotsResult.value().slots[2], request.timestamp, smeltingSlotsResult.version(), 3))
					.getMap());

			return Response.okFromJson(new EarthApiResponse<>(workshop), EarthApiResponse.class);
		});

		this.addHandler(new Route.Builder(Request.Method.GET, "/crafting/$slotIndex").build(), request ->
		{
			int slotIndex = request.getParameterInt("slotIndex");
			if (slotIndex < 1 || slotIndex > 3)
			{
				return Response.badRequest();
			}

			try
			{
				String playerId = request.getContextData("playerId");
				EarthDB.Results results = new EarthDB.Query(false)
						.get("crafting", playerId, CraftingSlots.class)
						.execute(earthDB);
				EarthDB.Results.Result<CraftingSlots> craftingSlotsResult = results.get("crafting");
				return Response.okFromJson(new EarthApiResponse<>(this.craftingSlotModelToResponseIncludingLocked(craftingSlotsResult.value().slots[slotIndex - 1], request.timestamp, craftingSlotsResult.version(), slotIndex)), EarthApiResponse.class);
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});
		this.addHandler(new Route.Builder(Request.Method.GET, "/smelting/$slotIndex").build(), request ->
		{
			int slotIndex = request.getParameterInt("slotIndex");
			if (slotIndex < 1 || slotIndex > 3)
			{
				return Response.badRequest();
			}

			try
			{
				String playerId = request.getContextData("playerId");
				EarthDB.Results results = new EarthDB.Query(false)
						.get("smelting", playerId, SmeltingSlots.class)
						.execute(earthDB);
				EarthDB.Results.Result<SmeltingSlots> smeltingSlotsResult = results.get("smelting");
				return Response.okFromJson(new EarthApiResponse<>(this.smeltingSlotModelToResponseIncludingLocked(smeltingSlotsResult.value().slots[slotIndex - 1], request.timestamp, smeltingSlotsResult.version(), slotIndex)), EarthApiResponse.class);
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});

		this.addHandler(new Route.Builder(Request.Method.POST, "/crafting/$slotIndex/start").build(), request ->
		{
			int slotIndex = request.getParameterInt("slotIndex");
			if (slotIndex < 1 || slotIndex > 3)
			{
				return Response.badRequest();
			}

			record StartRequest(
					@NotNull String sessionId,
					@NotNull String recipeId,
					int multiplier,
					@NotNull Item[] ingredients
			)
			{
				record Item(
						@NotNull String itemId,
						int quantity,
						String[] itemInstanceIds
				)
				{
				}
			}
			StartRequest startRequest = request.getBodyAsJson(StartRequest.class);
			if (startRequest.multiplier < 1)
			{
				return Response.badRequest();
			}
			if (Arrays.stream(startRequest.ingredients).anyMatch(item -> item == null || item.quantity < 1 || (item.itemInstanceIds != null && item.itemInstanceIds.length > 0 && item.itemInstanceIds.length != item.quantity)))
			{
				return Response.badRequest();
			}
			Catalog.RecipesCatalog.CraftingRecipe recipe = this.staticData.catalog.recipesCatalog.getCraftingRecipe(startRequest.recipeId);
			if (recipe == null)
			{
				return Response.badRequest();
			}
			if (recipe.returnItems().length > 0)
			{
				// TODO: implement returnItems
				throw new UnsupportedOperationException();
			}

			try
			{
				String playerId = request.getContextData("playerId");
				EarthDB.Results results = new EarthDB.Query(true)
						.get("crafting", playerId, CraftingSlots.class)
						.get("inventory", playerId, Inventory.class)
						.get("hotbar", playerId, Hotbar.class)
						.then(results1 ->
						{
							EarthDB.Query query = new EarthDB.Query(true);

							CraftingSlots craftingSlots = (CraftingSlots) results1.get("crafting").value();
							CraftingSlot craftingSlot = craftingSlots.slots[slotIndex - 1];
							Inventory inventory = (Inventory) results1.get("inventory").value();
							Hotbar hotbar = (Hotbar) results1.get("hotbar").value();

							if (craftingSlot.locked || craftingSlot.activeJob != null)
							{
								return query;
							}

							InputItem[] providedItems = new InputItem[startRequest.ingredients.length];
							for (int index = 0; index < startRequest.ingredients.length; index++)
							{
								StartRequest.Item item = startRequest.ingredients[index];
								if (item.itemInstanceIds == null || item.itemInstanceIds.length == 0)
								{
									if (!inventory.takeItems(item.itemId, item.quantity))
									{
										return query;
									}
									providedItems[index] = new InputItem(item.itemId, item.quantity, new NonStackableItemInstance[0]);
								}
								else
								{
									NonStackableItemInstance[] instances = inventory.takeItems(item.itemId, item.itemInstanceIds);
									if (instances == null)
									{
										return query;
									}
									providedItems[index] = new InputItem(item.itemId, item.quantity, instances);
								}
							}
							hotbar.limitToInventory(inventory);

							LinkedList<LinkedList<InputItem>> inputItems = new LinkedList<>();
							for (Catalog.RecipesCatalog.CraftingRecipe.Ingredient ingredient : recipe.ingredients())
							{
								LinkedList<InputItem> ingredientItems = new LinkedList<>();
								int requiredCount = ingredient.count() * startRequest.multiplier;
								for (int index = 0; index < providedItems.length; index++)
								{
									InputItem providedItem = providedItems[index];
									if (providedItem.count() == 0)
									{
										continue;
									}
									if (Arrays.stream(ingredient.possibleItemIds()).noneMatch(id -> id.equals(providedItem.id())))
									{
										continue;
									}
									if (requiredCount > providedItem.count())
									{
										requiredCount -= providedItem.count();
										ingredientItems.add(providedItem);
										providedItems[index] = new InputItem(providedItem.id(), 0, new NonStackableItemInstance[0]);
									}
									else
									{
										NonStackableItemInstance[] takenInstances;
										NonStackableItemInstance[] remainingInstances;
										if (providedItem.instances().length > 0)
										{
											takenInstances = Arrays.copyOfRange(providedItem.instances(), 0, requiredCount);
											remainingInstances = Arrays.copyOfRange(providedItem.instances(), requiredCount, providedItem.count());
										}
										else
										{
											takenInstances = new NonStackableItemInstance[0];
											remainingInstances = new NonStackableItemInstance[0];
										}
										ingredientItems.add(new InputItem(providedItem.id(), requiredCount, takenInstances));
										providedItems[index] = new InputItem(providedItem.id(), providedItem.count() - requiredCount, remainingInstances);
										requiredCount = 0;
									}
									if (requiredCount == 0)
									{
										break;
									}
								}
								if (requiredCount > 0)
								{
									return query;
								}
								if (ingredientItems.isEmpty())
								{
									throw new AssertionError();
								}
								inputItems.add(ingredientItems);
							}
							if (inputItems.size() != recipe.ingredients().length)
							{
								throw new AssertionError();
							}
							if (Arrays.stream(providedItems).anyMatch(item -> item.count() > 0))
							{
								return query;
							}

							craftingSlot.activeJob = new CraftingSlot.ActiveJob(startRequest.sessionId, recipe.id(), request.timestamp, inputItems.stream().map(inputItems1 -> inputItems1.toArray(InputItem[]::new)).toArray(InputItem[][]::new), startRequest.multiplier, 0, false);

							query.update("crafting", playerId, craftingSlots).update("inventory", playerId, inventory).update("hotbar", playerId, hotbar);

							return query;
						})
						.execute(earthDB);
				return Response.okFromJson(new EarthApiResponse<>(new HashMap<>(), new EarthApiResponse.Updates(results)), EarthApiResponse.class);
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});
		this.addHandler(new Route.Builder(Request.Method.POST, "/smelting/$slotIndex/start").build(), request ->
		{
			int slotIndex = request.getParameterInt("slotIndex");
			if (slotIndex < 1 || slotIndex > 3)
			{
				return Response.badRequest();
			}

			record StartRequest(
					@NotNull String sessionId,
					@NotNull String recipeId,
					int multiplier,
					@NotNull Item input,
					@Nullable Item fuel
			)
			{
				record Item(
						@NotNull String itemId,
						int quantity,
						String[] itemInstanceIds
				)
				{
				}
			}
			StartRequest startRequest = request.getBodyAsJson(StartRequest.class);
			if (startRequest.multiplier < 1)
			{
				return Response.badRequest();
			}
			if (startRequest.input.quantity < 1 || (startRequest.input.itemInstanceIds != null && startRequest.input.itemInstanceIds.length > 0 && startRequest.input.itemInstanceIds.length != startRequest.input.quantity))
			{
				return Response.badRequest();
			}
			if (startRequest.fuel != null && startRequest.fuel.quantity > 0 && startRequest.fuel.itemInstanceIds != null && startRequest.fuel.itemInstanceIds.length > 0 && startRequest.fuel.itemInstanceIds.length != startRequest.fuel.quantity)
			{
				return Response.badRequest();
			}
			Catalog.RecipesCatalog.SmeltingRecipe recipe = this.staticData.catalog.recipesCatalog.getSmeltingRecipe(startRequest.recipeId);
			Catalog.ItemsCatalog.Item fuelCatalogItem = startRequest.fuel != null ? this.staticData.catalog.itemsCatalog.getItem(startRequest.fuel.itemId) : null;
			if (recipe == null)
			{
				return Response.badRequest();
			}
			if (startRequest.fuel != null && (fuelCatalogItem == null || fuelCatalogItem.fuelInfo() == null))
			{
				return Response.badRequest();
			}
			if (recipe.returnItemId() != null)
			{
				// TODO: implement returnItems
				throw new UnsupportedOperationException();
			}
			if (startRequest.fuel != null && fuelCatalogItem.fuelInfo().returnItemId() != null)
			{
				// TODO: implement returnItems
				throw new UnsupportedOperationException();
			}
			if (!startRequest.input.itemId.equals(recipe.input()) || startRequest.input.quantity != startRequest.multiplier)
			{
				return Response.badRequest();
			}

			try
			{
				String playerId = request.getContextData("playerId");
				EarthDB.Results results = new EarthDB.Query(true)
						.get("smelting", playerId, SmeltingSlots.class)
						.get("inventory", playerId, Inventory.class)
						.get("hotbar", playerId, Hotbar.class)
						.then(results1 ->
						{
							EarthDB.Query query = new EarthDB.Query(true);

							SmeltingSlots smeltingSlots = (SmeltingSlots) results1.get("smelting").value();
							SmeltingSlot smeltingSlot = smeltingSlots.slots[slotIndex - 1];
							Inventory inventory = (Inventory) results1.get("inventory").value();
							Hotbar hotbar = (Hotbar) results1.get("hotbar").value();

							if (smeltingSlot.locked || smeltingSlot.activeJob != null)
							{
								return query;
							}

							InputItem input;
							if (startRequest.input.itemInstanceIds == null || startRequest.input.itemInstanceIds.length == 0)
							{
								if (!inventory.takeItems(startRequest.input.itemId, startRequest.input.quantity))
								{
									return query;
								}
								input = new InputItem(startRequest.input.itemId, startRequest.input.quantity, new NonStackableItemInstance[0]);
							}
							else
							{
								NonStackableItemInstance[] instances = inventory.takeItems(startRequest.input.itemId, startRequest.input.itemInstanceIds);
								if (instances == null)
								{
									return query;
								}
								input = new InputItem(startRequest.input.itemId, startRequest.input.quantity, instances);
							}

							SmeltingSlot.Fuel fuel;
							int requiredFuelHeat = recipe.heatRequired() * startRequest.multiplier - (smeltingSlot.burning != null ? smeltingSlot.burning.remainingHeat() : 0);
							if (startRequest.fuel != null && startRequest.fuel.quantity > 0)
							{
								int requiredFuelCount = 0;
								while (requiredFuelHeat > 0)
								{
									requiredFuelCount += 1;
									requiredFuelHeat -= fuelCatalogItem.fuelInfo().heatPerSecond() * fuelCatalogItem.fuelInfo().burnTime();
								}
								if (startRequest.fuel.quantity < requiredFuelCount)
								{
									return query;
								}
								if (requiredFuelCount > 0)
								{
									InputItem fuelItem;
									if (startRequest.fuel.itemInstanceIds == null || startRequest.fuel.itemInstanceIds.length == 0)
									{
										if (!inventory.takeItems(startRequest.fuel.itemId, requiredFuelCount))
										{
											return query;
										}
										fuelItem = new InputItem(startRequest.fuel.itemId, requiredFuelCount, new NonStackableItemInstance[0]);
									}
									else
									{
										NonStackableItemInstance[] instances = inventory.takeItems(startRequest.fuel.itemId, Arrays.copyOf(startRequest.fuel.itemInstanceIds, requiredFuelCount));
										if (instances == null)
										{
											return query;
										}
										fuelItem = new InputItem(startRequest.fuel.itemId, requiredFuelCount, instances);
									}
									fuel = new SmeltingSlot.Fuel(fuelItem, fuelCatalogItem.fuelInfo().burnTime(), fuelCatalogItem.fuelInfo().heatPerSecond());
								}
								else
								{
									fuel = null;
								}
							}
							else
							{
								if (requiredFuelHeat > 0)
								{
									return query;
								}
								fuel = null;
							}

							hotbar.limitToInventory(inventory);

							smeltingSlot.activeJob = new SmeltingSlot.ActiveJob(startRequest.sessionId, recipe.id(), request.timestamp, input, fuel, startRequest.multiplier, 0, false);

							query.update("smelting", playerId, smeltingSlots).update("inventory", playerId, inventory).update("hotbar", playerId, hotbar);

							return query;
						})
						.execute(earthDB);
				return Response.okFromJson(new EarthApiResponse<>(new HashMap<>(), new EarthApiResponse.Updates(results)), EarthApiResponse.class);
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});

		this.addHandler(new Route.Builder(Request.Method.POST, "/crafting/$slotIndex/collectItems").build(), request ->
		{
			int slotIndex = request.getParameterInt("slotIndex");
			if (slotIndex < 1 || slotIndex > 3)
			{
				return Response.badRequest();
			}

			try
			{
				String playerId = request.getContextData("playerId");
				EarthDB.Results results = new EarthDB.Query(true)
						.get("crafting", playerId, CraftingSlots.class)
						.then(results1 ->
						{
							CraftingSlots craftingSlots = (CraftingSlots) results1.get("crafting").value();
							CraftingSlot craftingSlot = craftingSlots.slots[slotIndex - 1];

							Rewards rewards = new Rewards();
							if (craftingSlot.activeJob != null)
							{
								CraftingCalculator.State state = CraftingCalculator.calculateState(request.timestamp, craftingSlot.activeJob, this.staticData.catalog);

								int quantity = state.availableRounds() * state.output().count();
								if (quantity > 0)
								{
									rewards.addItem(state.output().id(), quantity);
								}

								if (state.completed())
								{
									craftingSlot.activeJob = null;
								}
								else
								{
									CraftingSlot.ActiveJob activeJob = craftingSlot.activeJob;
									craftingSlot.activeJob = new CraftingSlot.ActiveJob(activeJob.sessionId(), activeJob.recipeId(), activeJob.startTime(), activeJob.input(), activeJob.totalRounds(), activeJob.collectedRounds() + state.availableRounds(), activeJob.finishedEarly());
								}
							}

							return new EarthDB.Query(true)
									.update("crafting", playerId, craftingSlots)
									.then(ActivityLogUtils.addEntry(playerId, new ActivityLog.CraftingCompletedEntry(request.timestamp, rewards.toDBRewardsModel())))
									.then(rewards.toRedeemQuery(playerId, request.timestamp, staticData));
						})
						.execute(earthDB);
				return Response.okFromJson(new EarthApiResponse<>(new MapBuilder<>().put("rewards", ((Rewards) results.getExtra("rewards")).toApiResponse()).getMap(), new EarthApiResponse.Updates(results)), EarthApiResponse.class);
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});
		this.addHandler(new Route.Builder(Request.Method.POST, "/smelting/$slotIndex/collectItems").build(), request ->
		{
			int slotIndex = request.getParameterInt("slotIndex");
			if (slotIndex < 1 || slotIndex > 3)
			{
				return Response.badRequest();
			}

			try
			{
				String playerId = request.getContextData("playerId");
				EarthDB.Results results = new EarthDB.Query(true)
						.get("smelting", playerId, SmeltingSlots.class)
						.then(results1 ->
						{
							SmeltingSlots smeltingSlots = (SmeltingSlots) results1.get("smelting").value();
							SmeltingSlot smeltingSlot = smeltingSlots.slots[slotIndex - 1];

							Rewards rewards = new Rewards();
							if (smeltingSlot.activeJob != null)
							{
								SmeltingCalculator.State state = SmeltingCalculator.calculateState(request.timestamp, smeltingSlot.activeJob, smeltingSlot.burning, this.staticData.catalog);

								int quantity = state.availableRounds() * state.output().count();
								if (quantity > 0)
								{
									rewards.addItem(state.output().id(), quantity);
								}

								if (state.completed())
								{
									smeltingSlot.activeJob = null;
									if (state.remainingHeat() > 0)
									{
										smeltingSlot.burning = new SmeltingSlot.Burning(
												state.currentBurningFuel(),
												state.remainingHeat()
										);
									}
									else
									{
										smeltingSlot.burning = null;
									}
								}
								else
								{
									SmeltingSlot.ActiveJob activeJob = smeltingSlot.activeJob;
									smeltingSlot.activeJob = new SmeltingSlot.ActiveJob(activeJob.sessionId(), activeJob.recipeId(), activeJob.startTime(), activeJob.input(), activeJob.addedFuel(), activeJob.totalRounds(), activeJob.collectedRounds() + state.availableRounds(), activeJob.finishedEarly());
								}
							}

							return new EarthDB.Query(true)
									.update("smelting", playerId, smeltingSlots)
									.then(ActivityLogUtils.addEntry(playerId, new ActivityLog.SmeltingCompletedEntry(request.timestamp, rewards.toDBRewardsModel())))
									.then(rewards.toRedeemQuery(playerId, request.timestamp, staticData));
						})
						.execute(earthDB);
				return Response.okFromJson(new EarthApiResponse<>(new MapBuilder<>().put("rewards", ((Rewards) results.getExtra("rewards")).toApiResponse()).getMap(), new EarthApiResponse.Updates(results)), EarthApiResponse.class);
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});

		this.addHandler(new Route.Builder(Request.Method.POST, "/crafting/$slotIndex/stop").build(), request ->
		{
			int slotIndex = request.getParameterInt("slotIndex");
			if (slotIndex < 1 || slotIndex > 3)
			{
				return Response.badRequest();
			}

			try
			{
				String playerId = request.getContextData("playerId");
				EarthDB.Results results = new EarthDB.Query(true)
						.get("crafting", playerId, CraftingSlots.class)
						.get("inventory", playerId, Inventory.class)
						.get("journal", playerId, Journal.class)
						.then(results1 ->
						{
							EarthDB.Query query = new EarthDB.Query(true);
							query.get("crafting", playerId, CraftingSlots.class);

							CraftingSlots craftingSlots = (CraftingSlots) results1.get("crafting").value();
							CraftingSlot craftingSlot = craftingSlots.slots[slotIndex - 1];
							Inventory inventory = (Inventory) results1.get("inventory").value();
							Journal journal = (Journal) results1.get("journal").value();

							if (craftingSlot.activeJob == null)
							{
								return query;
							}
							CraftingCalculator.State state = CraftingCalculator.calculateState(request.timestamp, craftingSlot.activeJob, this.staticData.catalog);

							for (InputItem inputItem : state.input())
							{
								if (inputItem.instances().length > 0)
								{
									inventory.addItems(inputItem.id(), Arrays.stream(inputItem.instances()).map(instance -> new NonStackableItemInstance(instance.instanceId(), instance.wear())).toArray(NonStackableItemInstance[]::new));
								}
								else if (inputItem.count() > 0)
								{
									inventory.addItems(inputItem.id(), inputItem.count());
								}
								journal.addCollectedItem(inputItem.id(), request.timestamp, 0);
							}

							Rewards rewards = new Rewards();
							int outputQuantity = state.availableRounds() * state.output().count();
							if (outputQuantity > 0)
							{
								rewards.addItem(state.output().id(), outputQuantity);
							}

							craftingSlot.activeJob = null;

							query.update("crafting", playerId, craftingSlots).update("inventory", playerId, inventory).update("journal", playerId, journal);
							query.then(ActivityLogUtils.addEntry(playerId, new ActivityLog.CraftingCompletedEntry(request.timestamp, rewards.toDBRewardsModel())), false);
							query.then(rewards.toRedeemQuery(playerId, request.timestamp, staticData), false);

							return query;
						})
						.execute(earthDB);
				EarthDB.Results.Result<CraftingSlots> craftingSlotsResult = results.get("crafting");
				return Response.okFromJson(new EarthApiResponse<>(craftingSlotModelToResponse(craftingSlotsResult.value().slots[slotIndex - 1], request.timestamp, craftingSlotsResult.version()), new EarthApiResponse.Updates(results)), EarthApiResponse.class);
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});
		this.addHandler(new Route.Builder(Request.Method.POST, "/smelting/$slotIndex/stop").build(), request ->
		{
			int slotIndex = request.getParameterInt("slotIndex");
			if (slotIndex < 1 || slotIndex > 3)
			{
				return Response.badRequest();
			}

			try
			{
				String playerId = request.getContextData("playerId");
				EarthDB.Results results = new EarthDB.Query(true)
						.get("smelting", playerId, SmeltingSlots.class)
						.get("inventory", playerId, Inventory.class)
						.get("journal", playerId, Journal.class)
						.then(results1 ->
						{
							EarthDB.Query query = new EarthDB.Query(true);
							query.get("smelting", playerId, SmeltingSlots.class);

							SmeltingSlots smeltingSlots = (SmeltingSlots) results1.get("smelting").value();
							SmeltingSlot smeltingSlot = smeltingSlots.slots[slotIndex - 1];
							Inventory inventory = (Inventory) results1.get("inventory").value();
							Journal journal = (Journal) results1.get("journal").value();

							if (smeltingSlot.activeJob == null)
							{
								return query;
							}
							SmeltingCalculator.State state = SmeltingCalculator.calculateState(request.timestamp, smeltingSlot.activeJob, smeltingSlot.burning, this.staticData.catalog);

							if (state.input().instances().length > 0)
							{
								inventory.addItems(state.input().id(), Arrays.stream(state.input().instances()).map(instance -> new NonStackableItemInstance(instance.instanceId(), instance.wear())).toArray(NonStackableItemInstance[]::new));
							}
							else if (state.input().count() > 0)
							{
								inventory.addItems(state.input().id(), state.input().count());
							}
							journal.addCollectedItem(state.input().id(), request.timestamp, 0);

							if (state.remainingAddedFuel() != null)
							{
								if (state.remainingAddedFuel().item().instances().length > 0)
								{
									inventory.addItems(state.remainingAddedFuel().item().id(), Arrays.stream(state.remainingAddedFuel().item().instances()).map(instance -> new NonStackableItemInstance(instance.instanceId(), instance.wear())).toArray(NonStackableItemInstance[]::new));
								}
								else if (state.remainingAddedFuel().item().count() > 0)
								{
									inventory.addItems(state.remainingAddedFuel().item().id(), state.remainingAddedFuel().item().count());
								}
								journal.addCollectedItem(state.remainingAddedFuel().item().id(), request.timestamp, 0);
							}

							Rewards rewards = new Rewards();
							int outputQuantity = state.availableRounds() * state.output().count();
							if (outputQuantity > 0)
							{
								rewards.addItem(state.output().id(), outputQuantity);
							}

							smeltingSlot.activeJob = null;
							if (state.remainingHeat() > 0)
							{
								smeltingSlot.burning = new SmeltingSlot.Burning(
										state.currentBurningFuel(),
										state.remainingHeat()
								);
							}
							else
							{
								smeltingSlot.burning = null;
							}

							query.update("smelting", playerId, smeltingSlots).update("inventory", playerId, inventory).update("journal", playerId, journal);
							query.then(ActivityLogUtils.addEntry(playerId, new ActivityLog.SmeltingCompletedEntry(request.timestamp, rewards.toDBRewardsModel())), false);
							query.then(rewards.toRedeemQuery(playerId, request.timestamp, staticData), false);

							return query;
						})
						.execute(earthDB);
				EarthDB.Results.Result<SmeltingSlots> smeltingSlotsResult = results.get("smelting");
				return Response.okFromJson(new EarthApiResponse<>(smeltingSlotModelToResponse(smeltingSlotsResult.value().slots[slotIndex - 1], request.timestamp, smeltingSlotsResult.version()), new EarthApiResponse.Updates(results)), EarthApiResponse.class);
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});

		this.addHandler(new Route.Builder(Request.Method.POST, "/crafting/$slotIndex/finish").build(), request ->
		{
			int slotIndex = request.getParameterInt("slotIndex");
			if (slotIndex < 1 || slotIndex > 3)
			{
				return Response.badRequest();
			}
			ExpectedPurchasePrice expectedPurchasePrice = request.getBodyAsJson(ExpectedPurchasePrice.class);
			if (expectedPurchasePrice.expectedPurchasePrice() < 0)
			{
				return Response.badRequest();
			}

			try
			{
				String playerId = request.getContextData("playerId");
				EarthDB.Results results = new EarthDB.Query(true)
						.get("crafting", playerId, CraftingSlots.class)
						.get("profile", playerId, Profile.class)
						.then(results1 ->
						{
							EarthDB.Query query = new EarthDB.Query(true);
							query.get("profile", playerId, Profile.class);

							CraftingSlots craftingSlots = (CraftingSlots) results1.get("crafting").value();
							CraftingSlot craftingSlot = craftingSlots.slots[slotIndex - 1];
							Profile profile = (Profile) results1.get("profile").value();

							if (craftingSlot.activeJob == null)
							{
								return query;
							}
							CraftingCalculator.State state = CraftingCalculator.calculateState(request.timestamp, craftingSlot.activeJob, this.staticData.catalog);
							if (state.completed())
							{
								return query;
							}
							int remainingTime = (int) (state.totalCompletionTime() - request.timestamp);
							if (remainingTime < 0)
							{
								return query;
							}
							CraftingCalculator.FinishPrice finishPrice = CraftingCalculator.calculateFinishPrice(remainingTime);

							if (expectedPurchasePrice.expectedPurchasePrice() < finishPrice.price())
							{
								return query;
							}
							if (!profile.rubies.spend(finishPrice.price()))
							{
								return query;
							}

							CraftingSlot.ActiveJob activeJob = craftingSlot.activeJob;
							craftingSlot.activeJob = new CraftingSlot.ActiveJob(activeJob.sessionId(), activeJob.recipeId(), activeJob.startTime(), activeJob.input(), activeJob.totalRounds(), activeJob.collectedRounds(), true);

							query.update("crafting", playerId, craftingSlots).update("profile", playerId, profile);

							return query;
						})
						.execute(earthDB);
				Profile profile = (Profile) results.get("profile").value();
				return Response.okFromJson(new EarthApiResponse<>(new SplitRubies(profile.rubies.purchased, profile.rubies.earned), new EarthApiResponse.Updates(results)), EarthApiResponse.class);
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});
		this.addHandler(new Route.Builder(Request.Method.POST, "/smelting/$slotIndex/finish").build(), request ->
		{
			int slotIndex = request.getParameterInt("slotIndex");
			if (slotIndex < 1 || slotIndex > 3)
			{
				return Response.badRequest();
			}
			ExpectedPurchasePrice expectedPurchasePrice = request.getBodyAsJson(ExpectedPurchasePrice.class);
			if (expectedPurchasePrice.expectedPurchasePrice() < 0)
			{
				return Response.badRequest();
			}

			try
			{
				String playerId = request.getContextData("playerId");
				EarthDB.Results results = new EarthDB.Query(true)
						.get("smelting", playerId, SmeltingSlots.class)
						.get("profile", playerId, Profile.class)
						.then(results1 ->
						{
							EarthDB.Query query = new EarthDB.Query(true);
							query.get("profile", playerId, Profile.class);

							SmeltingSlots smeltingSlots = (SmeltingSlots) results1.get("smelting").value();
							SmeltingSlot smeltingSlot = smeltingSlots.slots[slotIndex - 1];
							Profile profile = (Profile) results1.get("profile").value();

							if (smeltingSlot.activeJob == null)
							{
								return query;
							}
							SmeltingCalculator.State state = SmeltingCalculator.calculateState(request.timestamp, smeltingSlot.activeJob, smeltingSlot.burning, this.staticData.catalog);
							if (state.completed())
							{
								return query;
							}
							int remainingTime = (int) (state.totalCompletionTime() - request.timestamp);
							if (remainingTime < 0)
							{
								return query;
							}
							SmeltingCalculator.FinishPrice finishPrice = SmeltingCalculator.calculateFinishPrice(remainingTime);

							if (expectedPurchasePrice.expectedPurchasePrice() < finishPrice.price())
							{
								return query;
							}
							if (!profile.rubies.spend(finishPrice.price()))
							{
								return query;
							}

							SmeltingSlot.ActiveJob activeJob = smeltingSlot.activeJob;
							smeltingSlot.activeJob = new SmeltingSlot.ActiveJob(activeJob.sessionId(), activeJob.recipeId(), activeJob.startTime(), activeJob.input(), activeJob.addedFuel(), activeJob.totalRounds(), activeJob.collectedRounds(), true);

							query.update("smelting", playerId, smeltingSlots).update("profile", playerId, profile);

							return query;
						})
						.execute(earthDB);
				Profile profile = (Profile) results.get("profile").value();
				return Response.okFromJson(new EarthApiResponse<>(new SplitRubies(profile.rubies.purchased, profile.rubies.earned), new EarthApiResponse.Updates(results)), EarthApiResponse.class);
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});

		this.addHandler(new Route.Builder(Request.Method.GET, "/crafting/finish/price").addQueryParameter("remainingTime").build(), request ->
		{
			int remainingTime;
			try
			{
				remainingTime = (int) TimeFormatter.parseDuration(request.getParameter("remainingTime"));
				if (remainingTime < 0)
				{
					return Response.badRequest();
				}
			}
			catch (Exception exception)
			{
				return Response.badRequest();
			}

			CraftingCalculator.FinishPrice finishPrice = CraftingCalculator.calculateFinishPrice(remainingTime);

			return Response.okFromJson(new EarthApiResponse<>(new FinishPrice(finishPrice.price(), 0, TimeFormatter.formatDuration(finishPrice.validFor()))), EarthApiResponse.class);
		});
		this.addHandler(new Route.Builder(Request.Method.GET, "/smelting/finish/price").addQueryParameter("remainingTime").build(), request ->
		{
			int remainingTime;
			try
			{
				remainingTime = (int) TimeFormatter.parseDuration(request.getParameter("remainingTime"));
				if (remainingTime < 0)
				{
					return Response.badRequest();
				}
			}
			catch (Exception exception)
			{
				return Response.badRequest();
			}

			SmeltingCalculator.FinishPrice finishPrice = SmeltingCalculator.calculateFinishPrice(remainingTime);

			return Response.okFromJson(new EarthApiResponse<>(new FinishPrice(finishPrice.price(), 0, TimeFormatter.formatDuration(finishPrice.validFor()))), EarthApiResponse.class);
		});

		this.addHandler(new Route.Builder(Request.Method.POST, "/crafting/$slotIndex/unlock").build(), request ->
		{
			int slotIndex = request.getParameterInt("slotIndex");
			if (slotIndex < 1 || slotIndex > 3)
			{
				return Response.badRequest();
			}
			ExpectedPurchasePrice expectedPurchasePrice = request.getBodyAsJson(ExpectedPurchasePrice.class);
			if (expectedPurchasePrice.expectedPurchasePrice() < 0)
			{
				return Response.badRequest();
			}

			try
			{
				String playerId = request.getContextData("playerId");
				EarthDB.Results results = new EarthDB.Query(true)
						.get("crafting", playerId, CraftingSlots.class)
						.get("profile", playerId, Profile.class)
						.then(results1 ->
						{
							EarthDB.Query query = new EarthDB.Query(true);

							CraftingSlots craftingSlots = (CraftingSlots) results1.get("crafting").value();
							CraftingSlot craftingSlot = craftingSlots.slots[slotIndex - 1];
							Profile profile = (Profile) results1.get("profile").value();

							if (!craftingSlot.locked)
							{
								return query;
							}
							int unlockPrice = CraftingCalculator.calculateUnlockPrice(slotIndex);

							if (expectedPurchasePrice.expectedPurchasePrice() != unlockPrice)
							{
								return query;
							}
							if (!profile.rubies.spend(unlockPrice))
							{
								return query;
							}

							craftingSlot.locked = false;

							query.update("crafting", playerId, craftingSlots).update("profile", playerId, profile);

							return query;
						})
						.execute(earthDB);
				return Response.okFromJson(new EarthApiResponse<>(new HashMap<>(), new EarthApiResponse.Updates(results)), EarthApiResponse.class);
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});
		this.addHandler(new Route.Builder(Request.Method.POST, "/smelting/$slotIndex/unlock").build(), request ->
		{
			int slotIndex = request.getParameterInt("slotIndex");
			if (slotIndex < 1 || slotIndex > 3)
			{
				return Response.badRequest();
			}
			ExpectedPurchasePrice expectedPurchasePrice = request.getBodyAsJson(ExpectedPurchasePrice.class);
			if (expectedPurchasePrice.expectedPurchasePrice() < 0)
			{
				return Response.badRequest();
			}

			try
			{
				String playerId = request.getContextData("playerId");
				EarthDB.Results results = new EarthDB.Query(true)
						.get("smelting", playerId, SmeltingSlots.class)
						.get("profile", playerId, Profile.class)
						.then(results1 ->
						{
							EarthDB.Query query = new EarthDB.Query(true);

							SmeltingSlots smeltingSlots = (SmeltingSlots) results1.get("smelting").value();
							SmeltingSlot smeltingSlot = smeltingSlots.slots[slotIndex - 1];
							Profile profile = (Profile) results1.get("profile").value();

							if (!smeltingSlot.locked)
							{
								return query;
							}
							int unlockPrice = SmeltingCalculator.calculateUnlockPrice(slotIndex);

							if (expectedPurchasePrice.expectedPurchasePrice() != unlockPrice)
							{
								return query;
							}
							if (!profile.rubies.spend(unlockPrice))
							{
								return query;
							}

							smeltingSlot.locked = false;

							query.update("smelting", playerId, smeltingSlots).update("profile", playerId, profile);

							return query;
						})
						.execute(earthDB);
				return Response.okFromJson(new EarthApiResponse<>(new HashMap<>(), new EarthApiResponse.Updates(results)), EarthApiResponse.class);
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});
	}

	@NotNull
	private micheal65536.vienna.apiserver.types.workshop.CraftingSlot craftingSlotModelToResponseIncludingLocked(@NotNull CraftingSlot craftingSlotModel, long currentTime, int streamVersion, int slotIndex)
	{
		if (craftingSlotModel.locked)
		{
			return new micheal65536.vienna.apiserver.types.workshop.CraftingSlot(null, null, null, null, 0, 0, 0, null, null, State.LOCKED, null, new UnlockPrice(CraftingCalculator.calculateUnlockPrice(slotIndex), 0), streamVersion);
		}
		else
		{
			return this.craftingSlotModelToResponse(craftingSlotModel, currentTime, streamVersion);
		}
	}

	@NotNull
	private micheal65536.vienna.apiserver.types.workshop.CraftingSlot craftingSlotModelToResponse(@NotNull CraftingSlot craftingSlotModel, long currentTime, int streamVersion)
	{
		if (craftingSlotModel.locked)
		{
			throw new IllegalArgumentException();
		}

		CraftingSlot.ActiveJob activeJob = craftingSlotModel.activeJob;
		if (activeJob != null)
		{
			CraftingCalculator.State state = CraftingCalculator.calculateState(currentTime, activeJob, this.staticData.catalog);
			return new micheal65536.vienna.apiserver.types.workshop.CraftingSlot(
					activeJob.sessionId(),
					activeJob.recipeId(),
					new OutputItem(state.output().id(), state.output().count()),
					Arrays.stream(activeJob.input()).flatMap(inputItems -> Arrays.stream(inputItems).map(item -> new micheal65536.vienna.apiserver.types.workshop.InputItem(
							item.id(),
							item.count(),
							Arrays.stream(item.instances()).map(NonStackableItemInstance::instanceId).toArray(String[]::new)
					))).toArray(micheal65536.vienna.apiserver.types.workshop.InputItem[]::new),
					state.completedRounds(),
					state.availableRounds(),
					state.totalRounds(),
					!state.completed() ? TimeFormatter.formatTime(state.nextCompletionTime()) : null,
					!state.completed() ? TimeFormatter.formatTime(state.totalCompletionTime()) : null,
					state.completed() ? State.COMPLETED : State.ACTIVE,
					null,
					null,
					streamVersion
			);
		}
		else
		{
			return new micheal65536.vienna.apiserver.types.workshop.CraftingSlot(null, null, null, null, 0, 0, 0, null, null, State.EMPTY, null, null, streamVersion);
		}
	}

	@NotNull
	private micheal65536.vienna.apiserver.types.workshop.SmeltingSlot smeltingSlotModelToResponseIncludingLocked(@NotNull SmeltingSlot smeltingSlotModel, long currentTime, int streamVersion, int slotIndex)
	{
		if (smeltingSlotModel.locked)
		{
			return new micheal65536.vienna.apiserver.types.workshop.SmeltingSlot(null, null, null, null, null, null, 0, 0, 0, null, null, State.LOCKED, null, new UnlockPrice(SmeltingCalculator.calculateUnlockPrice(slotIndex), 0), streamVersion);
		}
		else
		{
			return this.smeltingSlotModelToResponse(smeltingSlotModel, currentTime, streamVersion);
		}
	}

	@NotNull
	private micheal65536.vienna.apiserver.types.workshop.SmeltingSlot smeltingSlotModelToResponse(@NotNull SmeltingSlot smeltingSlotModel, long currentTime, int streamVersion)
	{
		if (smeltingSlotModel.locked)
		{
			throw new IllegalArgumentException();
		}

		SmeltingSlot.ActiveJob activeJob = smeltingSlotModel.activeJob;
		if (activeJob != null)
		{
			SmeltingCalculator.State state = SmeltingCalculator.calculateState(currentTime, activeJob, smeltingSlotModel.burning, this.staticData.catalog);

			micheal65536.vienna.apiserver.types.workshop.SmeltingSlot.Fuel fuel;
			if (state.remainingAddedFuel() != null && state.remainingAddedFuel().item().count() > 0)
			{
				fuel = new micheal65536.vienna.apiserver.types.workshop.SmeltingSlot.Fuel(
						new BurnRate(state.remainingAddedFuel().burnDuration(), state.remainingAddedFuel().heatPerSecond()),
						state.remainingAddedFuel().item().id(),
						state.remainingAddedFuel().item().count(),
						Arrays.stream(state.remainingAddedFuel().item().instances()).map(NonStackableItemInstance::instanceId).toArray(String[]::new)
				);
			}
			else
			{
				fuel = null;
			}

			micheal65536.vienna.apiserver.types.workshop.SmeltingSlot.Burning burning = new micheal65536.vienna.apiserver.types.workshop.SmeltingSlot.Burning(
					!state.completed() ? TimeFormatter.formatTime(state.burnStartTime()) : null,
					!state.completed() ? TimeFormatter.formatTime(state.burnEndTime()) : null,
					TimeFormatter.formatDuration((state.remainingHeat() * 1000) / state.currentBurningFuel().heatPerSecond()),
					(float) state.currentBurningFuel().burnDuration() * state.currentBurningFuel().heatPerSecond() - state.remainingHeat(),
					new micheal65536.vienna.apiserver.types.workshop.SmeltingSlot.Fuel(
							new BurnRate(state.currentBurningFuel().burnDuration(), state.currentBurningFuel().heatPerSecond()),
							state.currentBurningFuel().item().id(),
							state.currentBurningFuel().item().count(),
							Arrays.stream(state.currentBurningFuel().item().instances()).map(NonStackableItemInstance::instanceId).toArray(String[]::new)
					)
			);

			return new micheal65536.vienna.apiserver.types.workshop.SmeltingSlot(
					fuel,
					burning,
					activeJob.sessionId(),
					activeJob.recipeId(),
					new OutputItem(state.output().id(), state.output().count()),
					state.input().count() > 0 ? new micheal65536.vienna.apiserver.types.workshop.InputItem[]{new micheal65536.vienna.apiserver.types.workshop.InputItem(state.input().id(), state.input().count(), Arrays.stream(state.input().instances()).map(NonStackableItemInstance::instanceId).toArray(String[]::new))} : new micheal65536.vienna.apiserver.types.workshop.InputItem[0],
					state.completedRounds(),
					state.availableRounds(),
					state.totalRounds(),
					!state.completed() ? TimeFormatter.formatTime(state.nextCompletionTime()) : null,
					!state.completed() ? TimeFormatter.formatTime(state.totalCompletionTime()) : null,
					state.completed() ? State.COMPLETED : State.ACTIVE,
					null,
					null,
					streamVersion
			);
		}
		else
		{
			SmeltingSlot.Burning burningModel = smeltingSlotModel.burning;
			micheal65536.vienna.apiserver.types.workshop.SmeltingSlot.Burning burning = burningModel != null ? new micheal65536.vienna.apiserver.types.workshop.SmeltingSlot.Burning(
					null,
					null,
					TimeFormatter.formatDuration((burningModel.remainingHeat() * 1000) / burningModel.fuel().heatPerSecond()),
					(float) burningModel.fuel().burnDuration() * burningModel.fuel().heatPerSecond() * burningModel.fuel().item().count() - burningModel.remainingHeat(),
					new micheal65536.vienna.apiserver.types.workshop.SmeltingSlot.Fuel(
							new BurnRate(burningModel.fuel().burnDuration(), burningModel.fuel().heatPerSecond()),
							burningModel.fuel().item().id(),
							burningModel.fuel().item().count(),
							Arrays.stream(burningModel.fuel().item().instances()).map(NonStackableItemInstance::instanceId).toArray(String[]::new)
					)
			) : null;
			return new micheal65536.vienna.apiserver.types.workshop.SmeltingSlot(null, burning, null, null, null, null, 0, 0, 0, null, null, State.EMPTY, null, null, streamVersion);
		}
	}
}