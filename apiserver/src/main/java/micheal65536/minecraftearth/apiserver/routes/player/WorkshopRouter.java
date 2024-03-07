package micheal65536.minecraftearth.apiserver.routes.player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.minecraftearth.apiserver.Catalog;
import micheal65536.minecraftearth.apiserver.routing.Request;
import micheal65536.minecraftearth.apiserver.routing.Response;
import micheal65536.minecraftearth.apiserver.routing.Router;
import micheal65536.minecraftearth.apiserver.routing.ServerErrorException;
import micheal65536.minecraftearth.apiserver.types.catalog.ItemsCatalog;
import micheal65536.minecraftearth.apiserver.types.catalog.RecipesCatalog;
import micheal65536.minecraftearth.apiserver.types.common.BurnRate;
import micheal65536.minecraftearth.apiserver.types.common.ExpectedPurchasePrice;
import micheal65536.minecraftearth.apiserver.types.common.Rewards;
import micheal65536.minecraftearth.apiserver.types.common.SplitRubies;
import micheal65536.minecraftearth.apiserver.types.workshop.FinishPrice;
import micheal65536.minecraftearth.apiserver.types.workshop.OutputItem;
import micheal65536.minecraftearth.apiserver.types.workshop.State;
import micheal65536.minecraftearth.apiserver.types.workshop.UnlockPrice;
import micheal65536.minecraftearth.apiserver.utils.CraftingCalculator;
import micheal65536.minecraftearth.apiserver.utils.EarthApiResponse;
import micheal65536.minecraftearth.apiserver.utils.MapBuilder;
import micheal65536.minecraftearth.apiserver.utils.SmeltingCalculator;
import micheal65536.minecraftearth.apiserver.utils.TimeFormatter;
import micheal65536.minecraftearth.db.DatabaseException;
import micheal65536.minecraftearth.db.EarthDB;
import micheal65536.minecraftearth.db.model.common.NonStackableItemInstance;
import micheal65536.minecraftearth.db.model.player.Hotbar;
import micheal65536.minecraftearth.db.model.player.Inventory;
import micheal65536.minecraftearth.db.model.player.Journal;
import micheal65536.minecraftearth.db.model.player.Rubies;
import micheal65536.minecraftearth.db.model.player.workshop.CraftingSlot;
import micheal65536.minecraftearth.db.model.player.workshop.CraftingSlots;
import micheal65536.minecraftearth.db.model.player.workshop.InputItem;
import micheal65536.minecraftearth.db.model.player.workshop.SmeltingSlot;
import micheal65536.minecraftearth.db.model.player.workshop.SmeltingSlots;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.UUID;
import java.util.stream.IntStream;

public class WorkshopRouter extends Router
{
	private final Catalog catalog;

	public WorkshopRouter(@NotNull EarthDB earthDB, @NotNull Catalog catalog)
	{
		this.catalog = catalog;

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
			RecipesCatalog.CraftingRecipe recipe = Arrays.stream(this.catalog.recipesCatalog.crafting()).filter(craftingRecipe -> craftingRecipe.id().equals(startRequest.recipeId)).findFirst().orElse(null);
			if (recipe == null)
			{
				return Response.badRequest();
			}
			if (startRequest.ingredients.length != recipe.ingredients().length)
			{
				return Response.badRequest();
			}
			if (recipe.returnItems().length > 0)
			{
				// TODO: implement returnItems
				throw new UnsupportedOperationException();
			}
			for (int index = 0; index < recipe.ingredients().length; index++)
			{
				RecipesCatalog.CraftingRecipe.Ingredient ingredient = recipe.ingredients()[index];
				StartRequest.Item item = startRequest.ingredients[index];
				if (Arrays.stream(ingredient.items()).noneMatch(id -> id.equals(item.itemId)))
				{
					return Response.badRequest();
				}
				if (item.quantity != ingredient.quantity() * startRequest.multiplier)
				{
					return Response.badRequest();
				}
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
							EarthDB.Query rejectedQuery = new EarthDB.Query(false)
									.get("crafting", playerId, CraftingSlots.class)
									.get("inventory", playerId, Inventory.class);

							CraftingSlots craftingSlots = (CraftingSlots) results1.get("crafting").value();
							CraftingSlot craftingSlot = craftingSlots.slots[slotIndex - 1];
							Inventory inventory = (Inventory) results1.get("inventory").value();
							Hotbar hotbar = (Hotbar) results1.get("hotbar").value();

							if (craftingSlot.locked || craftingSlot.activeJob != null)
							{
								return rejectedQuery;
							}

							LinkedList<InputItem> inputItems = new LinkedList<>();
							for (StartRequest.Item item : startRequest.ingredients)
							{
								if (item.itemInstanceIds == null || item.itemInstanceIds.length == 0)
								{
									if (!inventory.takeItems(item.itemId, item.quantity))
									{
										return rejectedQuery;
									}
									inputItems.add(new InputItem(item.itemId, item.quantity, new NonStackableItemInstance[0]));
								}
								else
								{
									NonStackableItemInstance[] instances = inventory.takeItems(item.itemId, item.itemInstanceIds);
									if (instances == null)
									{
										return rejectedQuery;
									}
									inputItems.add(new InputItem(item.itemId, item.quantity, instances));
								}
							}
							hotbar.limitToInventory(inventory);

							craftingSlot.activeJob = new CraftingSlot.ActiveJob(startRequest.sessionId, recipe.id(), request.timestamp, inputItems.toArray(InputItem[]::new), startRequest.multiplier, 0, false);

							return new EarthDB.Query(true)
									.update("crafting", playerId, craftingSlots)
									.update("inventory", playerId, inventory)
									.update("hotbar", playerId, hotbar)
									.get("crafting", playerId, CraftingSlots.class)
									.get("inventory", playerId, Inventory.class);
						})
						.execute(earthDB);
				return Response.okFromJson(new EarthApiResponse<>(new HashMap<>(), new EarthApiResponse.Updates(results, "crafting", "inventory")), EarthApiResponse.class);
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
			RecipesCatalog.SmeltingRecipe recipe = Arrays.stream(this.catalog.recipesCatalog.smelting()).filter(smeltingRecipe -> smeltingRecipe.id().equals(startRequest.recipeId)).findFirst().orElse(null);
			if (recipe == null)
			{
				return Response.badRequest();
			}
			if (recipe.returnItems().length > 0)
			{
				// TODO: implement returnItems
				throw new UnsupportedOperationException();
			}
			if (startRequest.fuel != null && Arrays.stream(this.catalog.itemsCatalog.items()).filter(item -> item.id().equals(startRequest.fuel.itemId)).findFirst().map(item -> item.fuelReturnItems().length > 0).orElse(false))
			{
				// TODO: implement returnItems
				throw new UnsupportedOperationException();
			}
			if (!startRequest.input.itemId.equals(recipe.inputItemId()) || startRequest.input.quantity != startRequest.multiplier)
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
							EarthDB.Query rejectedQuery = new EarthDB.Query(false)
									.get("smelting", playerId, SmeltingSlots.class)
									.get("inventory", playerId, Inventory.class);

							SmeltingSlots smeltingSlots = (SmeltingSlots) results1.get("smelting").value();
							SmeltingSlot smeltingSlot = smeltingSlots.slots[slotIndex - 1];
							Inventory inventory = (Inventory) results1.get("inventory").value();
							Hotbar hotbar = (Hotbar) results1.get("hotbar").value();

							if (smeltingSlot.locked || smeltingSlot.activeJob != null)
							{
								return rejectedQuery;
							}

							InputItem input;
							if (startRequest.input.itemInstanceIds == null || startRequest.input.itemInstanceIds.length == 0)
							{
								if (!inventory.takeItems(startRequest.input.itemId, startRequest.input.quantity))
								{
									return rejectedQuery;
								}
								input = new InputItem(startRequest.input.itemId, startRequest.input.quantity, new NonStackableItemInstance[0]);
							}
							else
							{
								NonStackableItemInstance[] instances = inventory.takeItems(startRequest.input.itemId, startRequest.input.itemInstanceIds);
								if (instances == null)
								{
									return rejectedQuery;
								}
								input = new InputItem(startRequest.input.itemId, startRequest.input.quantity, instances);
							}

							SmeltingSlot.Fuel fuel;
							int requiredFuelHeat = recipe.heatRequired() * startRequest.multiplier - (smeltingSlot.burning != null ? smeltingSlot.burning.remainingHeat() : 0);
							if (startRequest.fuel != null && startRequest.fuel.quantity > 0)
							{
								if (requiredFuelHeat <= 0)
								{
									return rejectedQuery;
								}
								BurnRate burnRate = Arrays.stream(this.catalog.itemsCatalog.items()).filter(item -> item.id().equals(startRequest.fuel.itemId)).findFirst().map(ItemsCatalog.Item::burnRate).orElse(null);
								if (burnRate == null)
								{
									return rejectedQuery;
								}
								int requiredFuelCount = 0;
								while (requiredFuelHeat > 0)
								{
									requiredFuelCount += 1;
									requiredFuelHeat -= burnRate.heatPerSecond() * burnRate.burnTime();
								}
								if (startRequest.fuel.quantity < requiredFuelCount)
								{
									return rejectedQuery;
								}
								InputItem fuelItem;
								if (startRequest.fuel.itemInstanceIds == null || startRequest.fuel.itemInstanceIds.length == 0)
								{
									if (!inventory.takeItems(startRequest.fuel.itemId, startRequest.fuel.quantity))
									{
										return rejectedQuery;
									}
									fuelItem = new InputItem(startRequest.fuel.itemId, requiredFuelCount, new NonStackableItemInstance[0]);
								}
								else
								{
									NonStackableItemInstance[] instances = inventory.takeItems(startRequest.fuel.itemId, startRequest.fuel.itemInstanceIds);
									if (instances == null)
									{
										return rejectedQuery;
									}
									fuelItem = new InputItem(startRequest.fuel.itemId, requiredFuelCount, instances);
								}
								fuel = new SmeltingSlot.Fuel(fuelItem, burnRate.burnTime(), burnRate.heatPerSecond());
							}
							else
							{
								if (requiredFuelHeat > 0)
								{
									return rejectedQuery;
								}
								fuel = null;
							}

							hotbar.limitToInventory(inventory);

							smeltingSlot.activeJob = new SmeltingSlot.ActiveJob(startRequest.sessionId, recipe.id(), request.timestamp, input, fuel, startRequest.multiplier, 0, false);

							return new EarthDB.Query(true)
									.update("smelting", playerId, smeltingSlots)
									.update("inventory", playerId, inventory)
									.update("hotbar", playerId, hotbar)
									.get("smelting", playerId, SmeltingSlots.class)
									.get("inventory", playerId, Inventory.class);
						})
						.execute(earthDB);
				return Response.okFromJson(new EarthApiResponse<>(new HashMap<>(), new EarthApiResponse.Updates(results, "smelting", "inventory")), EarthApiResponse.class);
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
						.get("inventory", playerId, Inventory.class)
						.get("journal", playerId, Journal.class)
						.then(results1 ->
						{
							EarthDB.Query rejectedQuery = new EarthDB.Query(false)
									.get("crafting", playerId, CraftingSlots.class)
									.get("inventory", playerId, Inventory.class)
									.get("journal", playerId, Journal.class)
									.extra("rewards", new Rewards(0, 0, new Rewards.Item[0], new Rewards.Buildplate[0], new Rewards.Challenge[0], new Rewards.PersonaItem[0], new Rewards.UtilityBlock[0]));

							CraftingSlots craftingSlots = (CraftingSlots) results1.get("crafting").value();
							CraftingSlot craftingSlot = craftingSlots.slots[slotIndex - 1];
							Inventory inventory = (Inventory) results1.get("inventory").value();
							Journal journal = (Journal) results1.get("journal").value();

							if (craftingSlot.activeJob == null)
							{
								return rejectedQuery;
							}
							CraftingCalculator.State state = CraftingCalculator.calculateState(request.timestamp, craftingSlot.activeJob, this.catalog);

							int quantity = state.availableRounds() * state.output().count();
							if (quantity > 0)
							{
								ItemsCatalog.Item item = Arrays.stream(this.catalog.itemsCatalog.items()).filter(item1 -> item1.id().equals(state.output().id())).findFirst().orElseThrow();
								if (item.stacks())
								{
									inventory.addItems(item.id(), quantity);
								}
								else
								{
									inventory.addItems(item.id(), IntStream.range(0, quantity).mapToObj(index -> new NonStackableItemInstance(UUID.randomUUID().toString(), 100.0f)).toArray(NonStackableItemInstance[]::new));
								}
								journal.touchItem(state.output().id(), request.timestamp);
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

							return new EarthDB.Query(true)
									.update("crafting", playerId, craftingSlots)
									.update("inventory", playerId, inventory)
									.update("journal", playerId, journal)
									.get("crafting", playerId, CraftingSlots.class)
									.get("inventory", playerId, Inventory.class)
									.get("journal", playerId, Journal.class)
									.extra("rewards", new Rewards(0, 0, new Rewards.Item[]{new Rewards.Item(state.output().id(), quantity)}, new Rewards.Buildplate[0], new Rewards.Challenge[0], new Rewards.PersonaItem[0], new Rewards.UtilityBlock[0]));
						})
						.execute(earthDB);
				return Response.okFromJson(new EarthApiResponse<>(new MapBuilder<>().put("rewards", results.getExtra("rewards")).getMap(), new EarthApiResponse.Updates(results, "crafting", "inventory")), EarthApiResponse.class);
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
						.get("inventory", playerId, Inventory.class)
						.get("journal", playerId, Journal.class)
						.then(results1 ->
						{
							EarthDB.Query rejectedQuery = new EarthDB.Query(false)
									.get("smelting", playerId, SmeltingSlots.class)
									.get("inventory", playerId, Inventory.class)
									.get("journal", playerId, Journal.class)
									.extra("rewards", new Rewards(0, 0, new Rewards.Item[0], new Rewards.Buildplate[0], new Rewards.Challenge[0], new Rewards.PersonaItem[0], new Rewards.UtilityBlock[0]));

							SmeltingSlots smeltingSlots = (SmeltingSlots) results1.get("smelting").value();
							SmeltingSlot smeltingSlot = smeltingSlots.slots[slotIndex - 1];
							;
							Inventory inventory = (Inventory) results1.get("inventory").value();
							Journal journal = (Journal) results1.get("journal").value();

							if (smeltingSlot.activeJob == null)
							{
								return rejectedQuery;
							}
							SmeltingCalculator.State state = SmeltingCalculator.calculateState(request.timestamp, smeltingSlot.activeJob, smeltingSlot.burning, this.catalog);

							int quantity = state.availableRounds() * state.output().count();
							if (quantity > 0)
							{
								ItemsCatalog.Item item = Arrays.stream(this.catalog.itemsCatalog.items()).filter(item1 -> item1.id().equals(state.output().id())).findFirst().orElseThrow();
								if (item.stacks())
								{
									inventory.addItems(item.id(), quantity);
								}
								else
								{
									inventory.addItems(item.id(), IntStream.range(0, quantity).mapToObj(index -> new NonStackableItemInstance(UUID.randomUUID().toString(), 100.0f)).toArray(NonStackableItemInstance[]::new));
								}
								journal.touchItem(state.output().id(), request.timestamp);
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

							return new EarthDB.Query(true)
									.update("smelting", playerId, smeltingSlots)
									.update("inventory", playerId, inventory)
									.update("journal", playerId, journal)
									.get("smelting", playerId, SmeltingSlots.class)
									.get("inventory", playerId, Inventory.class)
									.get("journal", playerId, Journal.class)
									.extra("rewards", new Rewards(0, 0, new Rewards.Item[]{new Rewards.Item(state.output().id(), quantity)}, new Rewards.Buildplate[0], new Rewards.Challenge[0], new Rewards.PersonaItem[0], new Rewards.UtilityBlock[0]));
						})
						.execute(earthDB);
				return Response.okFromJson(new EarthApiResponse<>(new MapBuilder<>().put("rewards", results.getExtra("rewards")).getMap(), new EarthApiResponse.Updates(results, "smelting", "inventory")), EarthApiResponse.class);
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
							EarthDB.Query rejectedQuery = new EarthDB.Query(false)
									.get("crafting", playerId, CraftingSlots.class)
									.get("inventory", playerId, Inventory.class)
									.get("journal", playerId, Journal.class);

							CraftingSlots craftingSlots = (CraftingSlots) results1.get("crafting").value();
							CraftingSlot craftingSlot = craftingSlots.slots[slotIndex - 1];
							Inventory inventory = (Inventory) results1.get("inventory").value();
							Journal journal = (Journal) results1.get("journal").value();

							if (craftingSlot.activeJob == null)
							{
								return rejectedQuery;
							}
							CraftingCalculator.State state = CraftingCalculator.calculateState(request.timestamp, craftingSlot.activeJob, this.catalog);

							for (InputItem inputItem : state.input())
							{
								if (inputItem.instances().length > 0)
								{
									inventory.addItems(inputItem.id(), Arrays.stream(inputItem.instances()).map(instance -> new NonStackableItemInstance(instance.instanceId(), instance.health())).toArray(NonStackableItemInstance[]::new));
								}
								else if (inputItem.count() > 0)
								{
									inventory.addItems(inputItem.id(), inputItem.count());
								}
								journal.touchItem(inputItem.id(), request.timestamp);
							}

							int outputQuantity = state.availableRounds() * state.output().count();
							if (outputQuantity > 0)
							{
								ItemsCatalog.Item item = Arrays.stream(this.catalog.itemsCatalog.items()).filter(item1 -> item1.id().equals(state.output().id())).findFirst().orElseThrow();
								if (item.stacks())
								{
									inventory.addItems(item.id(), outputQuantity);
								}
								else
								{
									inventory.addItems(item.id(), IntStream.range(0, outputQuantity).mapToObj(index -> new NonStackableItemInstance(UUID.randomUUID().toString(), 100.0f)).toArray(NonStackableItemInstance[]::new));
								}
								journal.touchItem(state.output().id(), request.timestamp);
							}

							craftingSlot.activeJob = null;

							return new EarthDB.Query(true)
									.update("crafting", playerId, craftingSlots)
									.update("inventory", playerId, inventory)
									.update("journal", playerId, journal)
									.get("crafting", playerId, CraftingSlots.class)
									.get("inventory", playerId, Inventory.class)
									.get("journal", playerId, Journal.class);
						})
						.execute(earthDB);
				EarthDB.Results.Result<CraftingSlots> craftingSlotsResult = results.get("crafting");
				return Response.okFromJson(new EarthApiResponse<>(craftingSlotModelToResponse(craftingSlotsResult.value().slots[slotIndex - 1], request.timestamp, craftingSlotsResult.version()), new EarthApiResponse.Updates(results, "crafting", "inventory")), EarthApiResponse.class);
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
							EarthDB.Query rejectedQuery = new EarthDB.Query(false)
									.get("smelting", playerId, SmeltingSlots.class)
									.get("inventory", playerId, Inventory.class)
									.get("journal", playerId, Journal.class);

							SmeltingSlots smeltingSlots = (SmeltingSlots) results1.get("smelting").value();
							SmeltingSlot smeltingSlot = smeltingSlots.slots[slotIndex - 1];
							Inventory inventory = (Inventory) results1.get("inventory").value();
							Journal journal = (Journal) results1.get("journal").value();

							if (smeltingSlot.activeJob == null)
							{
								return rejectedQuery;
							}
							SmeltingCalculator.State state = SmeltingCalculator.calculateState(request.timestamp, smeltingSlot.activeJob, smeltingSlot.burning, this.catalog);

							if (state.input().instances().length > 0)
							{
								inventory.addItems(state.input().id(), Arrays.stream(state.input().instances()).map(instance -> new NonStackableItemInstance(instance.instanceId(), instance.health())).toArray(NonStackableItemInstance[]::new));
							}
							else if (state.input().count() > 0)
							{
								inventory.addItems(state.input().id(), state.input().count());
							}
							journal.touchItem(state.input().id(), request.timestamp);

							int outputQuantity = state.availableRounds() * state.output().count();
							if (outputQuantity > 0)
							{
								ItemsCatalog.Item item = Arrays.stream(this.catalog.itemsCatalog.items()).filter(item1 -> item1.id().equals(state.output().id())).findFirst().orElseThrow();
								if (item.stacks())
								{
									inventory.addItems(item.id(), outputQuantity);
								}
								else
								{
									inventory.addItems(item.id(), IntStream.range(0, outputQuantity).mapToObj(index -> new NonStackableItemInstance(UUID.randomUUID().toString(), 100.0f)).toArray(NonStackableItemInstance[]::new));
								}
								journal.touchItem(state.output().id(), request.timestamp);
							}

							if (state.remainingAddedFuel() != null)
							{
								if (state.remainingAddedFuel().item().instances().length > 0)
								{
									inventory.addItems(state.remainingAddedFuel().item().id(), Arrays.stream(state.remainingAddedFuel().item().instances()).map(instance -> new NonStackableItemInstance(instance.instanceId(), instance.health())).toArray(NonStackableItemInstance[]::new));
								}
								else if (state.remainingAddedFuel().item().count() > 0)
								{
									inventory.addItems(state.remainingAddedFuel().item().id(), state.remainingAddedFuel().item().count());
								}
								journal.touchItem(state.remainingAddedFuel().item().id(), request.timestamp);
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

							return new EarthDB.Query(true)
									.update("smelting", playerId, smeltingSlots)
									.update("inventory", playerId, inventory)
									.update("journal", playerId, journal)
									.get("smelting", playerId, SmeltingSlots.class)
									.get("inventory", playerId, Inventory.class)
									.get("journal", playerId, Journal.class);
						})
						.execute(earthDB);
				EarthDB.Results.Result<SmeltingSlots> smeltingSlotsResult = results.get("smelting");
				return Response.okFromJson(new EarthApiResponse<>(smeltingSlotModelToResponse(smeltingSlotsResult.value().slots[slotIndex - 1], request.timestamp, smeltingSlotsResult.version()), new EarthApiResponse.Updates(results, "smelting", "inventory")), EarthApiResponse.class);
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
						.get("rubies", playerId, Rubies.class)
						.then(results1 ->
						{
							EarthDB.Query rejectedQuery = new EarthDB.Query(false)
									.get("crafting", playerId, CraftingSlots.class)
									.get("rubies", playerId, Rubies.class);

							CraftingSlots craftingSlots = (CraftingSlots) results1.get("crafting").value();
							CraftingSlot craftingSlot = craftingSlots.slots[slotIndex - 1];
							Rubies rubies = (Rubies) results1.get("rubies").value();

							if (craftingSlot.activeJob == null)
							{
								return rejectedQuery;
							}
							CraftingCalculator.State state = CraftingCalculator.calculateState(request.timestamp, craftingSlot.activeJob, this.catalog);
							if (state.completed())
							{
								return rejectedQuery;
							}
							int remainingTime = (int) (state.totalCompletionTime() - request.timestamp);
							if (remainingTime < 0)
							{
								return rejectedQuery;
							}
							CraftingCalculator.FinishPrice finishPrice = CraftingCalculator.calculateFinishPrice(remainingTime);

							if (expectedPurchasePrice.expectedPurchasePrice() < finishPrice.price())
							{
								return rejectedQuery;
							}
							if (!rubies.spend(finishPrice.price()))
							{
								return rejectedQuery;
							}

							CraftingSlot.ActiveJob activeJob = craftingSlot.activeJob;
							craftingSlot.activeJob = new CraftingSlot.ActiveJob(activeJob.sessionId(), activeJob.recipeId(), activeJob.startTime(), activeJob.input(), activeJob.totalRounds(), activeJob.collectedRounds(), true);

							return new EarthDB.Query(true)
									.update("crafting", playerId, craftingSlots)
									.update("rubies", playerId, rubies)
									.get("crafting", playerId, CraftingSlots.class)
									.get("rubies", playerId, Rubies.class);
						})
						.execute(earthDB);
				Rubies rubies = (Rubies) results.get("rubies").value();
				return Response.okFromJson(new EarthApiResponse<>(new SplitRubies(rubies.purchased, rubies.earned), new EarthApiResponse.Updates(results, "crafting")), EarthApiResponse.class);
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
						.get("rubies", playerId, Rubies.class)
						.then(results1 ->
						{
							EarthDB.Query rejectedQuery = new EarthDB.Query(false)
									.get("smelting", playerId, SmeltingSlots.class)
									.get("rubies", playerId, Rubies.class);

							SmeltingSlots smeltingSlots = (SmeltingSlots) results1.get("smelting").value();
							SmeltingSlot smeltingSlot = smeltingSlots.slots[slotIndex - 1];
							Rubies rubies = (Rubies) results1.get("rubies").value();

							if (smeltingSlot.activeJob == null)
							{
								return rejectedQuery;
							}
							SmeltingCalculator.State state = SmeltingCalculator.calculateState(request.timestamp, smeltingSlot.activeJob, smeltingSlot.burning, this.catalog);
							if (state.completed())
							{
								return rejectedQuery;
							}
							int remainingTime = (int) (state.totalCompletionTime() - request.timestamp);
							if (remainingTime < 0)
							{
								return rejectedQuery;
							}
							SmeltingCalculator.FinishPrice finishPrice = SmeltingCalculator.calculateFinishPrice(remainingTime);

							if (expectedPurchasePrice.expectedPurchasePrice() < finishPrice.price())
							{
								return rejectedQuery;
							}
							if (!rubies.spend(finishPrice.price()))
							{
								return rejectedQuery;
							}

							SmeltingSlot.ActiveJob activeJob = smeltingSlot.activeJob;
							smeltingSlot.activeJob = new SmeltingSlot.ActiveJob(activeJob.sessionId(), activeJob.recipeId(), activeJob.startTime(), activeJob.input(), activeJob.addedFuel(), activeJob.totalRounds(), activeJob.collectedRounds(), true);

							return new EarthDB.Query(true)
									.update("smelting", playerId, smeltingSlots)
									.update("rubies", playerId, rubies)
									.get("smelting", playerId, SmeltingSlots.class)
									.get("rubies", playerId, Rubies.class);
						})
						.execute(earthDB);
				Rubies rubies = (Rubies) results.get("rubies").value();
				return Response.okFromJson(new EarthApiResponse<>(new SplitRubies(rubies.purchased, rubies.earned), new EarthApiResponse.Updates(results, "smelting")), EarthApiResponse.class);
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
						.get("rubies", playerId, Rubies.class)
						.then(results1 ->
						{
							EarthDB.Query rejectedQuery = new EarthDB.Query(false)
									.get("crafting", playerId, CraftingSlots.class);

							CraftingSlots craftingSlots = (CraftingSlots) results1.get("crafting").value();
							CraftingSlot craftingSlot = craftingSlots.slots[slotIndex - 1];
							Rubies rubies = (Rubies) results1.get("rubies").value();

							if (!craftingSlot.locked)
							{
								return rejectedQuery;
							}
							int unlockPrice = CraftingCalculator.calculateUnlockPrice(slotIndex);

							if (expectedPurchasePrice.expectedPurchasePrice() != unlockPrice)
							{
								return rejectedQuery;
							}
							if (!rubies.spend(unlockPrice))
							{
								return rejectedQuery;
							}

							craftingSlot.locked = false;

							return new EarthDB.Query(true)
									.update("crafting", playerId, craftingSlots)
									.update("rubies", playerId, rubies)
									.get("crafting", playerId, CraftingSlots.class);
						})
						.execute(earthDB);
				return Response.okFromJson(new EarthApiResponse<>(new HashMap<>(), new EarthApiResponse.Updates(results, "crafting")), EarthApiResponse.class);
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
						.get("rubies", playerId, Rubies.class)
						.then(results1 ->
						{
							EarthDB.Query rejectedQuery = new EarthDB.Query(false)
									.get("smelting", playerId, SmeltingSlots.class);

							SmeltingSlots smeltingSlots = (SmeltingSlots) results1.get("smelting").value();
							SmeltingSlot smeltingSlot = smeltingSlots.slots[slotIndex - 1];
							Rubies rubies = (Rubies) results1.get("rubies").value();

							if (!smeltingSlot.locked)
							{
								return rejectedQuery;
							}
							int unlockPrice = SmeltingCalculator.calculateUnlockPrice(slotIndex);

							if (expectedPurchasePrice.expectedPurchasePrice() != unlockPrice)
							{
								return rejectedQuery;
							}
							if (!rubies.spend(unlockPrice))
							{
								return rejectedQuery;
							}

							smeltingSlot.locked = false;

							return new EarthDB.Query(true)
									.update("smelting", playerId, smeltingSlots)
									.update("rubies", playerId, rubies)
									.get("smelting", playerId, SmeltingSlots.class);
						})
						.execute(earthDB);
				return Response.okFromJson(new EarthApiResponse<>(new HashMap<>(), new EarthApiResponse.Updates(results, "smelting")), EarthApiResponse.class);
			}
			catch (DatabaseException exception)
			{
				throw new ServerErrorException(exception);
			}
		});
	}

	@NotNull
	private micheal65536.minecraftearth.apiserver.types.workshop.CraftingSlot craftingSlotModelToResponseIncludingLocked(@NotNull CraftingSlot craftingSlotModel, long currentTime, int streamVersion, int slotIndex)
	{
		if (craftingSlotModel.locked)
		{
			return new micheal65536.minecraftearth.apiserver.types.workshop.CraftingSlot(null, null, null, null, 0, 0, 0, null, null, State.LOCKED, null, new UnlockPrice(CraftingCalculator.calculateUnlockPrice(slotIndex), 0), streamVersion);
		}
		else
		{
			return this.craftingSlotModelToResponse(craftingSlotModel, currentTime, streamVersion);
		}
	}

	@NotNull
	private micheal65536.minecraftearth.apiserver.types.workshop.CraftingSlot craftingSlotModelToResponse(@NotNull CraftingSlot craftingSlotModel, long currentTime, int streamVersion)
	{
		if (craftingSlotModel.locked)
		{
			throw new IllegalArgumentException();
		}

		CraftingSlot.ActiveJob activeJob = craftingSlotModel.activeJob;
		if (activeJob != null)
		{
			CraftingCalculator.State state = CraftingCalculator.calculateState(currentTime, activeJob, this.catalog);
			return new micheal65536.minecraftearth.apiserver.types.workshop.CraftingSlot(
					activeJob.sessionId(),
					activeJob.recipeId(),
					new OutputItem(state.output().id(), state.output().count()),
					Arrays.stream(activeJob.input()).map(item -> new micheal65536.minecraftearth.apiserver.types.workshop.InputItem(
							item.id(),
							item.count(),
							Arrays.stream(item.instances()).map(NonStackableItemInstance::instanceId).toArray(String[]::new)
					)).toArray(micheal65536.minecraftearth.apiserver.types.workshop.InputItem[]::new),
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
			return new micheal65536.minecraftearth.apiserver.types.workshop.CraftingSlot(null, null, null, null, 0, 0, 0, null, null, State.EMPTY, null, null, streamVersion);
		}
	}

	@NotNull
	private micheal65536.minecraftearth.apiserver.types.workshop.SmeltingSlot smeltingSlotModelToResponseIncludingLocked(@NotNull SmeltingSlot smeltingSlotModel, long currentTime, int streamVersion, int slotIndex)
	{
		if (smeltingSlotModel.locked)
		{
			return new micheal65536.minecraftearth.apiserver.types.workshop.SmeltingSlot(null, null, null, null, null, null, 0, 0, 0, null, null, State.LOCKED, null, new UnlockPrice(SmeltingCalculator.calculateUnlockPrice(slotIndex), 0), streamVersion);
		}
		else
		{
			return this.smeltingSlotModelToResponse(smeltingSlotModel, currentTime, streamVersion);
		}
	}

	@NotNull
	private micheal65536.minecraftearth.apiserver.types.workshop.SmeltingSlot smeltingSlotModelToResponse(@NotNull SmeltingSlot smeltingSlotModel, long currentTime, int streamVersion)
	{
		if (smeltingSlotModel.locked)
		{
			throw new IllegalArgumentException();
		}

		SmeltingSlot.ActiveJob activeJob = smeltingSlotModel.activeJob;
		if (activeJob != null)
		{
			SmeltingCalculator.State state = SmeltingCalculator.calculateState(currentTime, activeJob, smeltingSlotModel.burning, this.catalog);

			micheal65536.minecraftearth.apiserver.types.workshop.SmeltingSlot.Fuel fuel;
			if (state.remainingAddedFuel() != null && state.remainingAddedFuel().item().count() > 0)
			{
				fuel = new micheal65536.minecraftearth.apiserver.types.workshop.SmeltingSlot.Fuel(
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

			micheal65536.minecraftearth.apiserver.types.workshop.SmeltingSlot.Burning burning = new micheal65536.minecraftearth.apiserver.types.workshop.SmeltingSlot.Burning(
					!state.completed() ? TimeFormatter.formatTime(state.burnStartTime()) : null,
					!state.completed() ? TimeFormatter.formatTime(state.burnEndTime()) : null,
					TimeFormatter.formatDuration((state.remainingHeat() * 1000) / state.currentBurningFuel().heatPerSecond()),
					(float) state.currentBurningFuel().burnDuration() * state.currentBurningFuel().heatPerSecond() - state.remainingHeat(),
					new micheal65536.minecraftearth.apiserver.types.workshop.SmeltingSlot.Fuel(
							new BurnRate(state.currentBurningFuel().burnDuration(), state.currentBurningFuel().heatPerSecond()),
							state.currentBurningFuel().item().id(),
							state.currentBurningFuel().item().count(),
							Arrays.stream(state.currentBurningFuel().item().instances()).map(NonStackableItemInstance::instanceId).toArray(String[]::new)
					)
			);

			return new micheal65536.minecraftearth.apiserver.types.workshop.SmeltingSlot(
					fuel,
					burning,
					activeJob.sessionId(),
					activeJob.recipeId(),
					new OutputItem(state.output().id(), state.output().count()),
					state.input().count() > 0 ? new micheal65536.minecraftearth.apiserver.types.workshop.InputItem[]{new micheal65536.minecraftearth.apiserver.types.workshop.InputItem(state.input().id(), state.input().count(), Arrays.stream(state.input().instances()).map(NonStackableItemInstance::instanceId).toArray(String[]::new))} : new micheal65536.minecraftearth.apiserver.types.workshop.InputItem[0],
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
			micheal65536.minecraftearth.apiserver.types.workshop.SmeltingSlot.Burning burning = burningModel != null ? new micheal65536.minecraftearth.apiserver.types.workshop.SmeltingSlot.Burning(
					null,
					null,
					TimeFormatter.formatDuration((burningModel.remainingHeat() * 1000) / burningModel.fuel().heatPerSecond()),
					(float) burningModel.fuel().burnDuration() * burningModel.fuel().heatPerSecond() * burningModel.fuel().item().count() - burningModel.remainingHeat(),
					new micheal65536.minecraftearth.apiserver.types.workshop.SmeltingSlot.Fuel(
							new BurnRate(burningModel.fuel().burnDuration(), burningModel.fuel().heatPerSecond()),
							burningModel.fuel().item().id(),
							burningModel.fuel().item().count(),
							Arrays.stream(burningModel.fuel().item().instances()).map(NonStackableItemInstance::instanceId).toArray(String[]::new)
					)
			) : null;
			return new micheal65536.minecraftearth.apiserver.types.workshop.SmeltingSlot(null, burning, null, null, null, null, 0, 0, 0, null, null, State.EMPTY, null, null, streamVersion);
		}
	}
}