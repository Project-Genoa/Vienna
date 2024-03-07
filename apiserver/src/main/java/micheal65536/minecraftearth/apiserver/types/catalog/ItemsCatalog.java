package micheal65536.minecraftearth.apiserver.types.catalog;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import micheal65536.minecraftearth.apiserver.types.common.BurnRate;

import java.util.HashMap;
import java.util.Map;

public record ItemsCatalog(
		@NotNull Item[] items,
		@NotNull HashMap<String, EfficiencyCategory> efficiencyCategories
)
{
	public record Item(
			@NotNull String id,
			@NotNull ItemData item,
			@NotNull String category,
			@NotNull String rarity,
			int fragmentsRequired,
			boolean stacks,
			@Nullable BurnRate burnRate,
			@NotNull ReturnItem[] fuelReturnItems,
			@NotNull ReturnItem[] consumeReturnItems,
			@Nullable Integer experience,
			@NotNull HashMap<String, Integer> experiencePoints,
			boolean deprecated
	)
	{
		public record ItemData(
				@NotNull String name,
				@Nullable Integer aux,
				@NotNull String type,
				@NotNull String useType,
				@Nullable Integer tapSpeed,
				@Nullable Integer heal,
				@Nullable Integer nutrition,
				@Nullable Integer mobDamage,
				@Nullable Integer blockDamage,
				@Nullable Integer health,
				@Nullable BlockMetadata blockMetadata,
				@Nullable ItemMetadata itemMetadata,
				@Nullable BoostMetadata boostMetadata,
				@Nullable JournalMetadata journalMetadata,
				@Nullable AudioMetadata audioMetadata,
				@NotNull Map clientProperties
		)
		{
			public record BlockMetadata(
					@Nullable Integer health,
					@Nullable String efficiencyCategory
			)
			{
			}

			public record ItemMetadata(
					@NotNull String useType,
					@NotNull String alternativeUseType,
					@Nullable Integer mobDamage,
					@Nullable Integer blockDamage,
					@Nullable Integer weakDamage,
					@Nullable Integer nutrition,
					@Nullable Integer heal,
					@Nullable String efficiencyType,
					@Nullable Integer maxHealth
			)
			{
			}

			public record JournalMetadata(
					@NotNull String groupKey,
					int experience,
					int order,
					@NotNull String behavior,
					@NotNull String biome
			)
			{
			}

			public record AudioMetadata(
					@NotNull HashMap<String, String> sounds,
					@NotNull String defaultSound
			)
			{
			}
		}

		public record ReturnItem(
				@NotNull String id,
				int amount
		)
		{
		}
	}

	public record EfficiencyCategory(
			@NotNull EfficiencyMap efficiencyMap
	)
	{
		public record EfficiencyMap(
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
}