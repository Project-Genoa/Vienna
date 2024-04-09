package micheal65536.vienna.buildplate.launcher;

import com.github.steveice10.opennbt.tag.builtin.ByteArrayTag;
import com.github.steveice10.opennbt.tag.builtin.ByteTag;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.DoubleTag;
import com.github.steveice10.opennbt.tag.builtin.FloatTag;
import com.github.steveice10.opennbt.tag.builtin.IntArrayTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.LongArrayTag;
import com.github.steveice10.opennbt.tag.builtin.LongTag;
import com.github.steveice10.opennbt.tag.builtin.ShortTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;

final class NbtBuilder
{
	public static final class Compound
	{
		private final LinkedList<Tag> tags = new LinkedList<>();

		public Compound()
		{
			// empty
		}

		@NotNull
		public CompoundTag build(@NotNull String name)
		{
			CompoundTag tag = new CompoundTag(name);
			this.tags.forEach(tag::put);
			return tag;
		}

		@NotNull
		public Compound put(@NotNull String name, int value)
		{
			IntTag tag = new IntTag(name);
			tag.setValue(value);
			this.tags.add(tag);
			return this;
		}

		@NotNull
		public Compound put(@NotNull String name, byte value)
		{
			ByteTag tag = new ByteTag(name);
			tag.setValue(value);
			this.tags.add(tag);
			return this;
		}

		@NotNull
		public Compound put(@NotNull String name, short value)
		{
			ShortTag tag = new ShortTag(name);
			tag.setValue(value);
			this.tags.add(tag);
			return this;
		}

		@NotNull
		public Compound put(@NotNull String name, long value)
		{
			LongTag tag = new LongTag(name);
			tag.setValue(value);
			this.tags.add(tag);
			return this;
		}

		@NotNull
		public Compound put(@NotNull String name, float value)
		{
			FloatTag tag = new FloatTag(name);
			tag.setValue(value);
			this.tags.add(tag);
			return this;
		}

		@NotNull
		public Compound put(@NotNull String name, double value)
		{
			DoubleTag tag = new DoubleTag(name);
			tag.setValue(value);
			this.tags.add(tag);
			return this;
		}

		@NotNull
		public Compound put(@NotNull String name, @NotNull String value)
		{
			StringTag tag = new StringTag(name);
			tag.setValue(value);
			this.tags.add(tag);
			return this;
		}

		@NotNull
		public Compound put(@NotNull String name, int[] value)
		{
			IntArrayTag tag = new IntArrayTag(name);
			tag.setValue(value);
			this.tags.add(tag);
			return this;
		}

		@NotNull
		public Compound put(@NotNull String name, byte[] value)
		{
			ByteArrayTag tag = new ByteArrayTag(name);
			tag.setValue(value);
			this.tags.add(tag);
			return this;
		}

		@NotNull
		public Compound put(@NotNull String name, long[] value)
		{
			LongArrayTag tag = new LongArrayTag(name);
			tag.setValue(value);
			this.tags.add(tag);
			return this;
		}

		@NotNull
		public Compound put(@NotNull String name, @NotNull Compound value)
		{
			CompoundTag tag = value.build(name);
			this.tags.add(tag);
			return this;
		}

		@NotNull
		public Compound put(@NotNull String name, @NotNull List value)
		{
			ListTag tag = value.build(name);
			this.tags.add(tag);
			return this;
		}
	}

	public static final class List
	{
		private final Class<? extends Tag> type;
		private final LinkedList<Tag> tags = new LinkedList<>();

		public List(@NotNull Class<? extends Tag> type)
		{
			this.type = type;
		}

		@NotNull
		public ListTag build(@NotNull String name)
		{
			ListTag tag = new ListTag(name, this.type);
			this.tags.forEach(tag::add);
			return tag;
		}

		@NotNull
		public List add(int value)
		{
			IntTag tag = new IntTag("");
			tag.setValue(value);
			this.tags.add(tag);
			return this;
		}

		@NotNull
		public List add(byte value)
		{
			ByteTag tag = new ByteTag("");
			tag.setValue(value);
			this.tags.add(tag);
			return this;
		}

		@NotNull
		public List add(short value)
		{
			ShortTag tag = new ShortTag("");
			tag.setValue(value);
			this.tags.add(tag);
			return this;
		}

		@NotNull
		public List add(long value)
		{
			LongTag tag = new LongTag("");
			tag.setValue(value);
			this.tags.add(tag);
			return this;
		}

		@NotNull
		public List add(float value)
		{
			FloatTag tag = new FloatTag("");
			tag.setValue(value);
			this.tags.add(tag);
			return this;
		}

		@NotNull
		public List add(double value)
		{
			DoubleTag tag = new DoubleTag("");
			tag.setValue(value);
			this.tags.add(tag);
			return this;
		}

		@NotNull
		public List add(@NotNull String value)
		{
			StringTag tag = new StringTag("");
			tag.setValue(value);
			this.tags.add(tag);
			return this;
		}

		@NotNull
		public List add(int[] value)
		{
			IntArrayTag tag = new IntArrayTag("");
			tag.setValue(value);
			this.tags.add(tag);
			return this;
		}

		@NotNull
		public List add(byte[] value)
		{
			ByteArrayTag tag = new ByteArrayTag("");
			tag.setValue(value);
			this.tags.add(tag);
			return this;
		}

		@NotNull
		public List add(long[] value)
		{
			LongArrayTag tag = new LongArrayTag("");
			tag.setValue(value);
			this.tags.add(tag);
			return this;
		}

		@NotNull
		public List add(@NotNull Compound value)
		{
			CompoundTag tag = value.build("");
			this.tags.add(tag);
			return this;
		}

		@NotNull
		public List add(@NotNull List value)
		{
			ListTag tag = value.build("");
			this.tags.add(tag);
			return this;
		}
	}
}