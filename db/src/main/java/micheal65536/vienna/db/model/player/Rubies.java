package micheal65536.vienna.db.model.player;

public final class Rubies
{
	public int purchased;
	public int earned;

	public Rubies()
	{
		this.purchased = 0;
		this.earned = 0;
	}

	public boolean spend(int amount)
	{
		if (amount > this.purchased + this.earned)
		{
			return false;
		}

		// TODO: in what order should purchased/earned rubies be spent?
		if (amount > this.purchased)
		{
			amount -= this.purchased;
			this.purchased = 0;
		}
		else
		{
			this.purchased -= amount;
			amount = 0;
		}
		if (amount > 0)
		{
			this.earned -= amount;
		}
		if (this.purchased < 0 || this.earned < 0)
		{
			throw new AssertionError();
		}
		return true;
	}
}