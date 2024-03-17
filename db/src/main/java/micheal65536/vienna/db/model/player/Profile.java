package micheal65536.vienna.db.model.player;

import org.jetbrains.annotations.NotNull;

public final class Profile
{
	public int health;
	public int experience;
	public int level;
	@NotNull
	public final Rubies rubies;

	public Profile()
	{
		this.health = 20;
		this.experience = 0;
		this.level = 1;
		this.rubies = new Rubies();
	}
}