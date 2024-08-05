package micheal65536.vienna.apiserver.routes;

import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.apiserver.routing.Request;
import micheal65536.vienna.apiserver.routing.Response;
import micheal65536.vienna.apiserver.routing.Router;
import micheal65536.vienna.apiserver.types.catalog.BoostMetadata;
import micheal65536.vienna.apiserver.types.catalog.ItemsCatalog;
import micheal65536.vienna.apiserver.types.catalog.JournalCatalog;
import micheal65536.vienna.apiserver.types.catalog.NFCBoost;
import micheal65536.vienna.apiserver.types.catalog.RecipesCatalog;
import micheal65536.vienna.apiserver.types.common.BurnRate;
import micheal65536.vienna.apiserver.types.common.Rarity;
import micheal65536.vienna.apiserver.utils.EarthApiResponse;
import micheal65536.vienna.apiserver.utils.MapBuilder;
import micheal65536.vienna.apiserver.utils.TimeFormatter;
import micheal65536.vienna.staticdata.Catalog;

import java.util.Arrays;
import java.util.HashMap;

public class CatalogRouter extends Router
{
	public CatalogRouter(@NotNull Catalog catalog)
	{
		this.addHandler(new Route.Builder(Request.Method.GET, "/inventory/catalogv3").build(), request -> Response.okFromJson(new EarthApiResponse<>(makeItemsCatalogApiResponse(catalog)), EarthApiResponse.class));

		this.addHandler(new Route.Builder(Request.Method.GET, "/recipes").build(), request -> Response.okFromJson(new EarthApiResponse<>(makeRecipesCatalogApiResponse(catalog)), EarthApiResponse.class));

		this.addHandler(new Route.Builder(Request.Method.GET, "/journal/catalog").build(), request -> Response.okFromJson(new EarthApiResponse<>(makeJournalCatalogApiResponse(catalog)), EarthApiResponse.class));

		this.addHandler(new Route.Builder(Request.Method.GET, "/products/catalog").build(), request -> Response.okFromJson(new EarthApiResponse<>(makeNFCBoostsCatalogApiResponse(catalog)), EarthApiResponse.class));
	}

	@NotNull
	private static ItemsCatalog makeItemsCatalogApiResponse(@NotNull Catalog catalog)
	{
		ItemsCatalog.Item[] items = Arrays.stream(catalog.itemsCatalog.items).map(item ->
		{
			String categoryString = switch (item.category())
			{
				case CONSTRUCTION -> "Construction";
				case EQUIPMENT -> "Equipment";
				case ITEMS -> "Items";
				case MOBS -> "Mobs";
				case NATURE -> "Nature";
				case BOOST_ADVENTURE_XP -> "adventurexp";
				case BOOST_CRAFTING -> "crafting";
				case BOOST_DEFENSE -> "defense";
				case BOOST_EATING -> "eating";
				case BOOST_HEALTH -> "maxplayerhealth";
				case BOOST_HOARDING -> "hoarding";
				case BOOST_ITEM_XP -> "itemxp";
				case BOOST_MINING_SPEED -> "miningspeed";
				case BOOST_RETENTION -> "retention";
				case BOOST_SMELTING -> "smelting";
				case BOOST_STRENGTH -> "strength";
				case BOOST_TAPPABLE_RADIUS -> "tappableRadius";
			};

			String typeString = switch (item.type())
			{
				case BLOCK -> "Block";
				case ITEM -> "Item";
				case TOOL -> "Tool";
				case MOB -> "Mob";
				case ENVIRONMENT_BLOCK -> "EnvironmentBlock";
				case BOOST -> "Boost";
				case ADVENTURE_SCROLL -> "AdventureScroll";
			};

			String useTypeString = switch (item.useType())
			{
				case NONE -> "None";
				case BUILD -> "Build";
				case BUILD_ATTACK -> "BuildAttack";
				case INTERACT -> "Interact";
				case INTERACT_AND_BUILD -> "InteractAndBuild";
				case DESTROY -> "Destroy";
				case USE -> "Use";
				case CONSUME -> "Consume";
			};
			String alternativeUseTypeString = switch (item.alternativeUseType())
			{
				case NONE -> "None";
				case BUILD -> "Build";
				case BUILD_ATTACK -> "BuildAttack";
				case INTERACT -> "Interact";
				case INTERACT_AND_BUILD -> "InteractAndBuild";
				case DESTROY -> "Destroy";
				case USE -> "Use";
				case CONSUME -> "Consume";
			};

			int health;
			if (item.blockInfo() != null)
			{
				health = item.blockInfo().breakingHealth();
			}
			else if (item.toolInfo() != null)
			{
				health = item.toolInfo().maxWear();
			}
			else if (item.mobInfo() != null)
			{
				health = item.mobInfo().health();
			}
			else
			{
				health = 0;
			}

			int blockDamage;
			if (item.toolInfo() != null)
			{
				blockDamage = item.toolInfo().blockDamage();
			}
			else
			{
				blockDamage = 0;
			}

			int mobDamage;
			if (item.toolInfo() != null)
			{
				mobDamage = item.toolInfo().mobDamage();
			}
			else if (item.projectileInfo() != null)
			{
				mobDamage = item.projectileInfo().mobDamage();
			}
			else
			{
				mobDamage = 0;
			}

			ItemsCatalog.Item.ItemData.BlockMetadata blockMetadata;
			if (item.blockInfo() != null)
			{
				blockMetadata = new ItemsCatalog.Item.ItemData.BlockMetadata(item.blockInfo().breakingHealth(), item.blockInfo().efficiencyCategory());
			}
			else if (item.mobInfo() != null)
			{
				blockMetadata = new ItemsCatalog.Item.ItemData.BlockMetadata(item.mobInfo().health(), "instant");
			}
			else
			{
				blockMetadata = null;
			}

			BoostMetadata boostMetadata;
			if (item.boostInfo() != null)
			{
				String boostTypeString = switch (item.boostInfo().type())
				{
					case POTION -> "Potion";
					case INVENTORY_ITEM -> "InventoryItem";
				};

				String boostAttributeString = switch (item.boostInfo().effects()[0].type())
				{
					case ADVENTURE_XP -> "ItemExperiencePoints";
					case CRAFTING -> "Crafting";
					case DEFENSE -> "Defense";
					case EATING -> "Eating";
					case HEALING -> "Healing";
					case HEALTH -> "MaximumPlayerHealth";
					case ITEM_XP -> "ItemExperiencePoints";
					case MINING_SPEED -> "MiningSpeed";
					case RETENTION_BACKPACK, RETENTION_HOTBAR, RETENTION_XP -> "Retention";
					case SMELTING -> "Smelting";
					case STRENGTH -> "Strength";
					case TAPPABLE_RADIUS -> "TappableInteractionRadius";
				};

				boostMetadata = new BoostMetadata(
						item.boostInfo().name(),
						boostTypeString,
						boostAttributeString,
						false,
						item.boostInfo().canBeRemoved(),
						item.boostInfo().duration() != null ? TimeFormatter.formatDuration(item.boostInfo().duration()) : null,
						true,
						item.boostInfo().level(),
						Arrays.stream(item.boostInfo().effects()).map(effect ->
						{
							String effectTypeString = switch (effect.type())
							{
								case ADVENTURE_XP -> "ItemExperiencePoints";
								case CRAFTING -> "CraftingSpeed";
								case DEFENSE -> "PlayerDefense";
								case EATING -> "FoodHealth";
								case HEALING -> "Health";
								case HEALTH -> "MaximumPlayerHealth";
								case ITEM_XP -> "ItemExperiencePoints";
								case MINING_SPEED -> "BlockDamage";
								case RETENTION_BACKPACK -> "RetainBackpack";
								case RETENTION_HOTBAR -> "RetainHotbar";
								case RETENTION_XP -> "RetainExperiencePoints";
								case SMELTING -> "SmeltingFuelIntensity";
								case STRENGTH -> "AttackDamage";
								case TAPPABLE_RADIUS -> "TappableInteractionRadius";
							};

							String activationString = switch (effect.activation())
							{
								case INSTANT -> "Instant";
								case TIMED -> "Timed";
								case TRIGGERED -> "Triggered";
							};

							return new BoostMetadata.Effect(
									effectTypeString,
									effect.activation() == Catalog.ItemsCatalog.Item.BoostInfo.Effect.Activation.TIMED ? TimeFormatter.formatDuration(effect.duration()) : null,
									effect.type() == Catalog.ItemsCatalog.Item.BoostInfo.Effect.Type.RETENTION_BACKPACK || effect.type() == Catalog.ItemsCatalog.Item.BoostInfo.Effect.Type.RETENTION_HOTBAR || effect.type() == Catalog.ItemsCatalog.Item.BoostInfo.Effect.Type.RETENTION_XP ? null : effect.value(),
									switch (effect.type())
									{
										case HEALING, TAPPABLE_RADIUS -> "Increment";
										case ADVENTURE_XP, CRAFTING, DEFENSE, EATING, HEALTH, ITEM_XP, MINING_SPEED, SMELTING, STRENGTH -> "Percentage";
										case RETENTION_BACKPACK, RETENTION_HOTBAR, RETENTION_XP -> null;
									},
									effect.type() == Catalog.ItemsCatalog.Item.BoostInfo.Effect.Type.CRAFTING || effect.type() == Catalog.ItemsCatalog.Item.BoostInfo.Effect.Type.SMELTING ? "UtilityBlock" : "Player",
									effect.applicableItemIds(),
									switch (effect.type())
									{
										case ITEM_XP -> new String[]{"Tappable"};
										case ADVENTURE_XP -> new String[]{"Encounter"};
										default -> new String[0];
									},
									activationString,
									effect.type() == Catalog.ItemsCatalog.Item.BoostInfo.Effect.Type.EATING ? "Health" : null
							);
						}).toArray(BoostMetadata.Effect[]::new),
						item.boostInfo().triggeredOnDeath() ? "Death" : null,
						null
				);
			}
			else
			{
				boostMetadata = null;
			}

			ItemsCatalog.Item.ItemData.JournalMetadata journalMetadata;
			if (item.journalEntry() != null)
			{
				String behaviorString = switch (item.journalEntry().behavior())
				{
					case NONE -> "None";
					case PASSIVE -> "Passive";
					case HOSTILE -> "Hostile";
					case NEUTRAL -> "Neutral";
				};

				String biomeString = switch (item.journalEntry().biome())
				{
					case NONE -> "None";
					case OVERWORLD -> "Overworld";
					case NETHER -> "Hell";
					case BIRCH_FOREST -> "BirchForest";
					case DESERT -> "Desert";
					case FLOWER_FOREST -> "FlowerForest";
					case FOREST -> "Forest";
					case ICE_PLAINS -> "IcePlains";
					case JUNGLE -> "Jungle";
					case MESA -> "Mesa";
					case MUSHROOM_ISLAND -> "MushroomIsland";
					case OCEAN -> "Ocean";
					case PLAINS -> "Plains";
					case RIVER -> "River";
					case ROOFED_FOREST -> "RoofedForest";
					case SAVANNA -> "Savanna";
					case SUNFLOWER_PLAINS -> "SunFlowerPlains";
					case SWAMP -> "Swampland";
					case TAIGA -> "Taiga";
					case WARM_OCEAN -> "WarmOcean";
				};

				journalMetadata = new ItemsCatalog.Item.ItemData.JournalMetadata(
						item.journalEntry().group(),
						item.experience().journal(),
						item.journalEntry().order(),
						behaviorString,
						biomeString
				);
			}
			else
			{
				journalMetadata = null;
			}

			return new ItemsCatalog.Item(
					item.id(),
					new ItemsCatalog.Item.ItemData(
							item.name(),
							item.aux(),
							typeString,
							useTypeString,
							0,
							item.consumeInfo() != null ? item.consumeInfo().heal() : null,
							0,
							mobDamage,
							blockDamage,
							health,
							blockMetadata,
							new ItemsCatalog.Item.ItemData.ItemMetadata(
									useTypeString,
									alternativeUseTypeString,
									mobDamage,
									blockDamage,
									null,
									0,
									item.consumeInfo() != null ? item.consumeInfo().heal() : 0,
									item.toolInfo() != null ? item.toolInfo().efficiencyCategory() : null,
									health
							),
							boostMetadata,
							journalMetadata,
							item.journalEntry() != null && item.journalEntry().sound() != null ? new ItemsCatalog.Item.ItemData.AudioMetadata(
									new MapBuilder<String, String>().put("journal", item.journalEntry().sound()).getMap(),
									item.journalEntry().sound()
							) : null,
							new HashMap<String, Object>()
					),
					categoryString,
					Rarity.valueOf(item.rarity().name()),
					1,
					item.stackable(),
					item.fuelInfo() != null ? new BurnRate(item.fuelInfo().burnTime(), item.fuelInfo().heatPerSecond()) : null,
					item.fuelInfo() != null && item.fuelInfo().returnItemId() != null ? new ItemsCatalog.Item.ReturnItem[]{new ItemsCatalog.Item.ReturnItem(item.fuelInfo().returnItemId(), 1)} : new ItemsCatalog.Item.ReturnItem[0],
					item.consumeInfo() != null && item.consumeInfo().returnItemId() != null ? new ItemsCatalog.Item.ReturnItem[]{new ItemsCatalog.Item.ReturnItem(item.consumeInfo().returnItemId(), 1)} : new ItemsCatalog.Item.ReturnItem[0],
					item.experience().tappable(),
					new MapBuilder<String, Integer>().put("tappable", item.experience().tappable()).put("encounter", item.experience().encounter()).put("crafting", item.experience().crafting()).getMap(),
					false
			);
		}).toArray(ItemsCatalog.Item[]::new);

		HashMap<String, ItemsCatalog.EfficiencyCategory> efficiencyCategories = new HashMap<>();
		for (Catalog.ItemEfficiencyCategoriesCatalog.EfficiencyCategory efficiencyCategory : catalog.itemEfficiencyCategoriesCatalog.efficiencyCategories)
		{
			efficiencyCategories.put(efficiencyCategory.name(), new ItemsCatalog.EfficiencyCategory(
					new ItemsCatalog.EfficiencyCategory.EfficiencyMap(
							efficiencyCategory.hand(),
							efficiencyCategory.hoe(),
							efficiencyCategory.axe(),
							efficiencyCategory.shovel(),
							efficiencyCategory.pickaxe_1(),
							efficiencyCategory.pickaxe_2(),
							efficiencyCategory.pickaxe_3(),
							efficiencyCategory.pickaxe_4(),
							efficiencyCategory.pickaxe_5(),
							efficiencyCategory.sword(),
							efficiencyCategory.sheers()
					)
			));
		}

		return new ItemsCatalog(items, efficiencyCategories);
	}

	@NotNull
	private static RecipesCatalog makeRecipesCatalogApiResponse(@NotNull Catalog catalog)
	{
		RecipesCatalog.CraftingRecipe[] crafting = Arrays.stream(catalog.recipesCatalog.crafting).map(recipe ->
		{
			String categoryString = switch (recipe.category())
			{
				case CONSTRUCTION -> "Construction";
				case EQUIPMENT -> "Equipment";
				case ITEMS -> "Items";
				case NATURE -> "Nature";
			};

			return new RecipesCatalog.CraftingRecipe(
					recipe.id(),
					categoryString,
					TimeFormatter.formatDuration(recipe.duration() * 1000),
					Arrays.stream(recipe.ingredients()).map(ingredient -> new RecipesCatalog.CraftingRecipe.Ingredient(ingredient.possibleItemIds(), ingredient.count())).toArray(RecipesCatalog.CraftingRecipe.Ingredient[]::new),
					new RecipesCatalog.CraftingRecipe.Output(recipe.output().itemId(), recipe.output().count()),
					Arrays.stream(recipe.returnItems()).map(returnItem -> new RecipesCatalog.CraftingRecipe.ReturnItem(returnItem.itemId(), returnItem.count())).toArray(RecipesCatalog.CraftingRecipe.ReturnItem[]::new),
					false
			);
		}).toArray(RecipesCatalog.CraftingRecipe[]::new);

		RecipesCatalog.SmeltingRecipe[] smelting = Arrays.stream(catalog.recipesCatalog.smelting).map(recipe ->
		{
			return new RecipesCatalog.SmeltingRecipe(
					recipe.id(),
					recipe.heatRequired(),
					recipe.input(),
					new RecipesCatalog.SmeltingRecipe.Output(recipe.output(), 1),
					recipe.returnItemId() != null ? new RecipesCatalog.SmeltingRecipe.ReturnItem[]{new RecipesCatalog.SmeltingRecipe.ReturnItem(recipe.returnItemId(), 1)} : new RecipesCatalog.SmeltingRecipe.ReturnItem[0],
					false
			);
		}).toArray(RecipesCatalog.SmeltingRecipe[]::new);

		return new RecipesCatalog(crafting, smelting);
	}

	@NotNull
	private static JournalCatalog makeJournalCatalogApiResponse(@NotNull Catalog catalog)
	{
		HashMap<String, JournalCatalog.Item> items = new HashMap<>();
		for (Catalog.ItemJournalGroupsCatalog.JournalGroup group : catalog.itemJournalGroupsCatalog.groups)
		{
			String parentCollectionString = switch (group.parentCollection())
			{
				case BLOCKS -> "Blocks";
				case ITEMS_CRAFTED -> "ItemsCrafted";
				case ITEMS_SMELTED -> "ItemsSmelted";
				case MOBS -> "Mobs";
			};

			items.put(group.name(), new JournalCatalog.Item(
					group.id(),
					parentCollectionString,
					group.order(),
					group.order(),
					group.defaultSound(),
					false,
					"200526.173531"
			));
		}
		return new JournalCatalog(items);
	}

	@NotNull
	private static NFCBoost[] makeNFCBoostsCatalogApiResponse(@NotNull Catalog catalog)
	{
		// TODO
		return new NFCBoost[0];
	}
}