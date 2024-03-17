package micheal65536.vienna.db.model.player.workshop;

import org.jetbrains.annotations.NotNull;

public final class CraftingSlots
{
	@NotNull
	public final CraftingSlot[] slots;

	public CraftingSlots()
	{
		this.slots = new CraftingSlot[]{new CraftingSlot(), new CraftingSlot(), new CraftingSlot()};
	}
}