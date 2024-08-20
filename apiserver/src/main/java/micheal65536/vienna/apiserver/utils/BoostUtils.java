package micheal65536.vienna.apiserver.utils;

import org.jetbrains.annotations.NotNull;

import micheal65536.vienna.apiserver.types.common.Effect;
import micheal65536.vienna.db.model.player.Boosts;
import micheal65536.vienna.staticdata.Catalog;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;

public final class BoostUtils
{
	@NotNull
	public static Catalog.ItemsCatalog.Item.BoostInfo.Effect[] getActiveEffects(@NotNull Boosts boosts, long currentTime, @NotNull Catalog.ItemsCatalog itemsCatalog)
	{
		LinkedHashMap<String, Catalog.ItemsCatalog.Item.BoostInfo> activeBoostsInfo = new LinkedHashMap<>();
		for (Boosts.ActiveBoost activeBoost : boosts.activeBoosts)
		{
			if (activeBoost == null)
			{
				continue;
			}
			if (activeBoost.startTime() + activeBoost.duration() < currentTime)
			{
				continue;
			}
			Catalog.ItemsCatalog.Item item = itemsCatalog.getItem(activeBoost.itemId());
			if (item == null || item.boostInfo() == null)
			{
				continue;
			}

			Catalog.ItemsCatalog.Item.BoostInfo existingBoostInfo = activeBoostsInfo.getOrDefault(item.boostInfo().name(), null);
			if (existingBoostInfo != null && existingBoostInfo.level() > item.boostInfo().level())
			{
				continue;
			}

			activeBoostsInfo.put(item.boostInfo().name(), item.boostInfo());
		}

		LinkedList<Catalog.ItemsCatalog.Item.BoostInfo.Effect> effects = new LinkedList<>();
		for (Catalog.ItemsCatalog.Item.BoostInfo boostInfo : activeBoostsInfo.values())
		{
			Arrays.stream(boostInfo.effects())
					.filter(effect -> switch (effect.activation())
					{
						case INSTANT -> false;
						case TRIGGERED -> true;
						case TIMED -> true;    // already filtered for expiry time above
					})
					.forEach(effects::add);
		}
		return effects.toArray(Catalog.ItemsCatalog.Item.BoostInfo.Effect[]::new);
	}

	public record StatModiferValues(
			int maxPlayerHealthMultiplier,
			int attackMultiplier,
			int defenseMultiplier,
			int foodMultiplier,
			int miningSpeedMultiplier,
			int craftingSpeedMultiplier,
			int smeltingSpeedMultiplier,
			int tappableInteractionRadiusExtraMeters,
			boolean keepHotbar,
			boolean keepInventory,
			boolean keepXp
	)
	{
	}

	@NotNull
	public static StatModiferValues getActiveStatModifiers(@NotNull Boosts boosts, long currentTime, @NotNull Catalog.ItemsCatalog itemsCatalog)
	{
		int maxPlayerHealth = 0;
		int attackMultiplier = 0;
		int defenseMultiplier = 0;
		int foodMultiplier = 0;
		int miningSpeedMultiplier = 0;
		int craftingMultiplier = 0;
		int smeltingMultiplier = 0;
		int tappableInteractionRadius = 0;
		boolean keepHotbar = false;
		boolean keepInventory = false;
		boolean keepXp = false;

		for (Catalog.ItemsCatalog.Item.BoostInfo.Effect effect : BoostUtils.getActiveEffects(boosts, currentTime, itemsCatalog))
		{
			switch (effect.type())
			{
				case HEALTH -> maxPlayerHealth += effect.value();
				case STRENGTH -> attackMultiplier += effect.value();
				case DEFENSE -> defenseMultiplier += effect.value();
				case EATING -> foodMultiplier += effect.value();
				case MINING_SPEED -> miningSpeedMultiplier += effect.value();
				case CRAFTING -> craftingMultiplier += effect.value();
				case SMELTING -> smeltingMultiplier += effect.value();
				case TAPPABLE_RADIUS -> tappableInteractionRadius += effect.value();
				case RETENTION_HOTBAR -> keepHotbar = true;
				case RETENTION_BACKPACK -> keepInventory = true;
				case RETENTION_XP -> keepXp = true;
			}
		}

		return new StatModiferValues(
				maxPlayerHealth,
				attackMultiplier,
				defenseMultiplier,
				foodMultiplier,
				miningSpeedMultiplier,
				craftingMultiplier,
				smeltingMultiplier,
				tappableInteractionRadius,
				keepHotbar,
				keepInventory,
				keepXp
		);
	}

	public static int getMaxPlayerHealth(@NotNull Boosts boosts, long currentTime, @NotNull Catalog.ItemsCatalog itemsCatalog)
	{
		return 20 + (20 * BoostUtils.getActiveStatModifiers(boosts, currentTime, itemsCatalog).maxPlayerHealthMultiplier()) / 100;
	}

	@NotNull
	public static Effect boostEffectToApiResponse(@NotNull Catalog.ItemsCatalog.Item.BoostInfo.Effect effect, long boostDuration)
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

		return new Effect(
				effectTypeString,
				effect.activation() == Catalog.ItemsCatalog.Item.BoostInfo.Effect.Activation.TIMED ? TimeFormatter.formatDuration(boostDuration) : null,
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
	}
}