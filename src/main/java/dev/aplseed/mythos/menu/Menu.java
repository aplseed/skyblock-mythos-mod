package dev.aplseed.mythos.menu;

import dev.aplseed.mythos.util.Items;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Plain-text snapshot of an open container menu. Hypixel menus are 9xN chests,
 * so screen positions follow the vanilla chest layout (176 wide, 114 + 18N tall).
 */
public final class Menu {
	public record Entry(Slot slot, ItemStack stack, String name, List<String> lore, String id, boolean playerSlot) {
		public int count() {
			return stack.getCount();
		}

		public boolean has(String text) {
			if (name.contains(text)) return true;
			for (String l : lore) if (l.contains(text)) return true;
			return false;
		}
	}

	public final AbstractContainerScreen<?> screen;
	public final String title;
	public final int rows;
	public final List<Entry> container = new ArrayList<>();
	public final List<Entry> inventory = new ArrayList<>();
	public final int signature;

	public Menu(AbstractContainerScreen<?> screen) {
		this.screen = screen;
		this.title = Items.plain(screen.getTitle());
		this.rows = screen.getMenu() instanceof ChestMenu chest ? chest.getRowCount() : 0;
		int sig = title.hashCode();
		for (Slot slot : screen.getMenu().slots) {
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) continue;
			boolean player = slot.container instanceof Inventory;
			List<String> lore = Items.lore(stack);
			Entry e = new Entry(slot, stack, Items.name(stack), lore, Items.skyblockId(stack), player);
			(player ? inventory : container).add(e);
			sig = 31 * sig + slot.index;
			sig = 31 * sig + e.name.hashCode();
			sig = 31 * sig + stack.getCount();
			sig = 31 * sig + lore.hashCode();
		}
		this.signature = sig;
	}

	public int width() {
		return 176;
	}

	public int height() {
		return rows > 0 ? 114 + rows * 18 : 166;
	}

	public int left() {
		return (screen.width - width()) / 2;
	}

	public int top() {
		return (screen.height - height()) / 2;
	}

	public int slotX(Slot s) {
		return left() + s.x;
	}

	public int slotY(Slot s) {
		return top() + s.y;
	}

	/** The (non-empty) slot under the mouse, container or inventory. */
	public Entry at(int mouseX, int mouseY) {
		for (List<Entry> list : List.of(container, inventory)) {
			for (Entry e : list) {
				int x = slotX(e.slot), y = slotY(e.slot);
				if (mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17) return e;
			}
		}
		return null;
	}
}
