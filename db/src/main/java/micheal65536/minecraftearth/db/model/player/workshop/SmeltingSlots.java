package micheal65536.minecraftearth.db.model.player.workshop;

import org.jetbrains.annotations.NotNull;

public final class SmeltingSlots
{
	@NotNull
	public final SmeltingSlot[] slots;

	public SmeltingSlots()
	{
		this.slots = new SmeltingSlot[]{new SmeltingSlot(), new SmeltingSlot(), new SmeltingSlot()};
	}
}