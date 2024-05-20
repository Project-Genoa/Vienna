package micheal65536.vienna.staticdata;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;

public final class Catalog
{
	public final ItemsCatalog itemsCatalog;
	public final ItemEfficiencyCategoriesCatalog itemEfficiencyCategoriesCatalog;
	public final ItemJournalGroupsCatalog itemJournalGroupsCatalog;
	public final RecipesCatalog recipesCatalog;
	public final NFCBoostsCatalog nfcBoostsCatalog;

	Catalog(@NotNull File dir) throws StaticDataException
	{
		try
		{
			this.itemsCatalog = new ItemsCatalog(new File(dir, "items.json"));
			this.itemEfficiencyCategoriesCatalog = new ItemEfficiencyCategoriesCatalog(new File(dir, "itemEfficiencyCategories.json"));
			this.itemJournalGroupsCatalog = new ItemJournalGroupsCatalog(new File(dir, "itemJournalGroups.json"));
			this.recipesCatalog = new RecipesCatalog(new File(dir, "recipes.json"));
			this.nfcBoostsCatalog = new NFCBoostsCatalog(new File(dir, "nfc.json"));
		}
		catch (StaticDataException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new StaticDataException(exception);
		}
	}

	public static final class ItemsCatalog
	{
		@NotNull
		public final Item[] items;

		private final HashMap<String, Item> itemsById = new HashMap<>();

		private ItemsCatalog(@NotNull File file) throws Exception
		{
			this.items = new Gson().fromJson(new FileReader(file), Item[].class);

			HashSet<String> ids = new HashSet<>();
			HashSet<String> names = new HashSet<>();
			for (Item item : this.items)
			{
				if (!ids.add(item.id))
				{
					throw new StaticDataException("Duplicate item ID %s".formatted(item.id));
				}
				if (!names.add(item.name + "." + item.aux))
				{
					throw new StaticDataException("Duplicate item name/aux %s %d".formatted(item.name, item.aux));
				}
			}

			for (Item item : this.items)
			{
				this.itemsById.put(item.id, item);
			}
		}

		@Nullable
		public Item getItem(@NotNull String id)
		{
			return this.itemsById.getOrDefault(id, null);
		}

		public record Item(
				@NotNull String id,
				@NotNull String name,
				int aux,
				boolean stackable,
				@NotNull Type type,
				@NotNull Category category,
				@NotNull Rarity rarity,
				@NotNull UseType useType,
				@NotNull UseType alternativeUseType,
				@Nullable BlockInfo blockInfo,
				@Nullable ToolInfo toolInfo,
				@Nullable ConsumeInfo consumeInfo,
				@Nullable FuelInfo fuelInfo,
				@Nullable ProjectileInfo projectileInfo,
				@Nullable MobInfo mobInfo,
				@Nullable BoostInfo boostInfo,
				@Nullable JournalEntry journalEntry,
				@NotNull Experience experience
		)
		{
			public enum Type
			{
				BLOCK,
				ITEM,
				TOOL,
				MOB,
				ENVIRONMENT_BLOCK,
				BOOST,
				ADVENTURE_SCROLL
			}

			public enum Category
			{
				CONSTRUCTION,
				EQUIPMENT,
				ITEMS,
				MOBS,
				NATURE,
				BOOST_ADVENTURE_XP,
				BOOST_CRAFTING,
				BOOST_DEFENSE,
				BOOST_EATING,
				BOOST_HEALTH,
				BOOST_HOARDING,
				BOOST_ITEM_XP,
				BOOST_MINING_SPEED,
				BOOST_RETENTION,
				BOOST_SMELTING,
				BOOST_STRENGTH,
				BOOST_TAPPABLE_RADIUS
			}

			public enum Rarity
			{
				COMMON,
				UNCOMMON,
				RARE,
				EPIC,
				LEGENDARY,
				OOBE
			}

			public enum UseType
			{
				NONE,
				BUILD,
				BUILD_ATTACK,
				INTERACT,
				INTERACT_AND_BUILD,
				DESTROY,
				USE,
				CONSUME
			}

			public record BlockInfo(
					int breakingHealth,
					@Nullable String efficiencyCategory
			)
			{
			}

			public record ToolInfo(
					int blockDamage,
					int mobDamage,
					int maxWear,
					@Nullable String efficiencyCategory
			)
			{
			}

			public record ConsumeInfo(
					int heal,
					@Nullable String returnItemId
			)
			{
			}

			public record FuelInfo(
					int burnTime,
					int heatPerSecond,
					@Nullable String returnItemId
			)
			{
			}

			public record ProjectileInfo(
					int mobDamage
			)
			{
			}

			public record MobInfo(
					int health
			)
			{
			}

			public record BoostInfo(
					@NotNull String name,
					@Nullable Integer level,
					@NotNull Type type,
					boolean canBeRemoved,
					@Nullable Integer duration,
					boolean triggeredOnDeath,
					@NotNull Effect[] effects
			)
			{
				public enum Type
				{
					POTION,
					INVENTORY_ITEM
				}

				public record Effect(
						@NotNull Type type,
						int value,
						@NotNull String[] applicableItemIds,
						@NotNull Activation activation,
						int duration
				)
				{
					public enum Type
					{
						ADVENTURE_XP,
						CRAFTING,
						DEFENSE,
						EATING,
						HEALING,
						HEALTH,
						ITEM_XP,
						MINING_SPEED,
						RETENTION_BACKPACK,
						RETENTION_HOTBAR,
						RETENTION_XP,
						SMELTING,
						STRENGTH,
						TAPPABLE_RADIUS
					}

					public enum Activation
					{
						INSTANT,
						TIMED,
						TRIGGERED
					}
				}
			}

			public record JournalEntry(
					@NotNull String group,
					int order,
					@NotNull Biome biome,
					@NotNull Behavior behavior,
					@Nullable String sound
			)
			{
				public enum Biome
				{
					NONE,
					OVERWORLD,
					NETHER,
					BIRCH_FOREST,
					DESERT,
					FLOWER_FOREST,
					FOREST,
					ICE_PLAINS,
					JUNGLE,
					MESA,
					MUSHROOM_ISLAND,
					OCEAN,
					PLAINS,
					RIVER,
					ROOFED_FOREST,
					SAVANNA,
					SUNFLOWER_PLAINS,
					SWAMP,
					TAIGA,
					WARM_OCEAN
				}

				public enum Behavior
				{
					NONE,
					PASSIVE,
					HOSTILE,
					NEUTRAL
				}
			}

			public record Experience(
					int tappable,
					int encounter,
					int crafting,
					int journal    // TODO: what is this used for?
			)
			{
			}
		}
	}

	public static final class ItemEfficiencyCategoriesCatalog
	{
		@NotNull
		public final EfficiencyCategory[] efficiencyCategories;

		private ItemEfficiencyCategoriesCatalog(@NotNull File file) throws Exception
		{
			this.efficiencyCategories = new Gson().fromJson(new FileReader(file), EfficiencyCategory[].class);

			HashSet<String> names = new HashSet<>();
			for (EfficiencyCategory efficiencyCategory : this.efficiencyCategories)
			{
				if (!names.add(efficiencyCategory.name))
				{
					throw new StaticDataException("Duplicate efficiency category name %s".formatted(efficiencyCategory.name));
				}
			}
		}

		public record EfficiencyCategory(
				@NotNull String name,
				float hand,
				float hoe,
				float axe,
				float shovel,
				float pickaxe_1,
				float pickaxe_2,
				float pickaxe_3,
				float pickaxe_4,
				float pickaxe_5,
				float sword,
				float sheers
		)
		{
		}
	}

	public static final class ItemJournalGroupsCatalog
	{
		@NotNull
		public final JournalGroup[] groups;

		private ItemJournalGroupsCatalog(@NotNull File file) throws Exception
		{
			this.groups = new Gson().fromJson(new FileReader(file), JournalGroup[].class);

			HashSet<String> ids = new HashSet<>();
			HashSet<String> names = new HashSet<>();
			for (JournalGroup journalGroup : this.groups)
			{
				if (!ids.add(journalGroup.id))
				{
					throw new StaticDataException("Duplicate journal group ID %s".formatted(journalGroup.id));
				}
				if (!names.add(journalGroup.name))
				{
					throw new StaticDataException("Duplicate journal group name %s".formatted(journalGroup.name));
				}
			}
		}

		public record JournalGroup(
				@NotNull String id,
				@NotNull String name,
				@NotNull ParentCollection parentCollection,
				int order,
				@Nullable String defaultSound
		)
		{
			public enum ParentCollection
			{
				BLOCKS,
				ITEMS_CRAFTED,
				ITEMS_SMELTED,
				MOBS
			}
		}
	}

	public static final class RecipesCatalog
	{
		@NotNull
		public final CraftingRecipe[] crafting;
		@NotNull
		public final SmeltingRecipe[] smelting;

		private final HashMap<String, CraftingRecipe> craftingRecipesById = new HashMap<>();
		private final HashMap<String, SmeltingRecipe> smeltingRecipesById = new HashMap<>();

		private RecipesCatalog(@NotNull File file) throws Exception
		{
			record RecipesCatalogFile(
					@NotNull CraftingRecipe[] crafting,
					@NotNull SmeltingRecipe[] smelting
			)
			{
			}
			RecipesCatalogFile recipesCatalogFile = new Gson().fromJson(new FileReader(file), RecipesCatalogFile.class);
			this.crafting = recipesCatalogFile.crafting;
			this.smelting = recipesCatalogFile.smelting;

			HashSet<String> craftingIds = new HashSet<>();
			HashSet<String> smeltingIds = new HashSet<>();
			for (CraftingRecipe craftingRecipe : this.crafting)
			{
				if (!craftingIds.add(craftingRecipe.id))
				{
					throw new StaticDataException("Duplicate crafting recipe ID %s".formatted(craftingRecipe.id));
				}
			}
			for (SmeltingRecipe smeltingRecipe : this.smelting)
			{
				if (!smeltingIds.add(smeltingRecipe.id))
				{
					throw new StaticDataException("Duplicate smelting recipe ID %s".formatted(smeltingRecipe.id));
				}
			}

			for (CraftingRecipe craftingRecipe : this.crafting)
			{
				this.craftingRecipesById.put(craftingRecipe.id, craftingRecipe);
			}
			for (SmeltingRecipe smeltingRecipe : this.smelting)
			{
				this.smeltingRecipesById.put(smeltingRecipe.id, smeltingRecipe);
			}
		}

		@Nullable
		public CraftingRecipe getCraftingRecipe(@NotNull String id)
		{
			return this.craftingRecipesById.getOrDefault(id, null);
		}

		@Nullable
		public SmeltingRecipe getSmeltingRecipe(@NotNull String id)
		{
			return this.smeltingRecipesById.getOrDefault(id, null);
		}

		public record CraftingRecipe(
				@NotNull String id,
				int duration,
				@NotNull Category category,
				@NotNull Ingredient[] ingredients,
				@NotNull Output output,
				@NotNull ReturnItem[] returnItems
		)
		{
			public enum Category
			{
				CONSTRUCTION,
				EQUIPMENT,
				ITEMS,
				NATURE
			}

			public record Ingredient(
					int count,
					@NotNull String[] possibleItemIds
			)
			{
			}

			public record Output(
					@NotNull String itemId,
					int count
			)
			{
			}

			public record ReturnItem(
					@NotNull String itemId,
					int count
			)
			{
			}
		}

		public record SmeltingRecipe(
				@NotNull String id,
				int heatRequired,
				@NotNull String input,
				@NotNull String output,
				@Nullable String returnItemId
		)
		{
		}
	}

	public static final class NFCBoostsCatalog
	{
		private NFCBoostsCatalog(@NotNull File file) throws Exception
		{
			record NFCBoostsCatalogFile(
					//
			)
			{
			}

			NFCBoostsCatalogFile nfcBoostsCatalogFile = new Gson().fromJson(new FileReader(file), NFCBoostsCatalogFile.class);

			// TODO
		}
	}
}