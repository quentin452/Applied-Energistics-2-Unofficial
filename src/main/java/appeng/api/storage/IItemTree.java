package appeng.api.storage;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;

/**
 * A tree that contains two cached views into its contents. The list is not directly accessible.
 */
public interface IItemTree extends Iterable<IAEItemStack>, IItemList<IAEItemStack> {

    /**
     * ItemStack display list of the items.
     */
    List<ItemStack> displayList();

    /**
     * IAEItemStack view list of the items.
     */
    List<IAEItemStack> viewList();

    /**
     * Resort the entire list using the new comparator and filter.
     */
    void refresh(Comparator<IAEItemStack> comparator, Predicate<IAEItemStack> filter);

    /**
     * Prune the sorted list using the new filter. This causes a view/display update.
     */
    void refresh(Predicate<IAEItemStack> filter);

    /**
     * Resort the entire list using the new comparator with the cached filter. This causes a view/display update.
     */
    void refresh(Comparator<IAEItemStack> comparator);

    /**
     * Refresh the view/display lists. No resorting is done.
     */
    void refresh();

    /**
     * Remove all elements from the backing list. Triggers a refresh.
     */
    void clear();

    /**
     * The total size of the list.
     */
    int fullSize();

    /**
     * The size of the viewable items.
     */
    @Override
    int size();
}
