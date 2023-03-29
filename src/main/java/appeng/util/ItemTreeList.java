package appeng.util;

import java.util.*;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;

public class ItemTreeList implements IItemTree {

    private final List<IAEItemStack> view;
    private final List<ItemStack> dsp;
    private TreeSet<IAEItemStack> list;
    private Predicate<IAEItemStack> filter;

    public ItemTreeList(Comparator<IAEItemStack> comparator, Predicate<IAEItemStack> filter) {
        this.dsp = new ArrayList<>();
        this.view = new ArrayList<>();
        this.list = new TreeSet<>(stackComparator(comparator));
        this.filter = filter;
    }

    @Override
    public List<ItemStack> displayList() {
        return dsp;
    }

    @Override
    public List<IAEItemStack> viewList() {
        return view;
    }

    @Override
    public void refresh(Comparator<IAEItemStack> comparator, Predicate<IAEItemStack> filter) {
        refresh(comparator);
        refresh(filter);
    }

    @Override
    public void refresh(Predicate<IAEItemStack> filter) {
        this.filter = filter;
        refresh();
    }

    @Override
    public void refresh(Comparator<IAEItemStack> comparator) {
        ArrayList<IAEItemStack> temp = new ArrayList<>(list);
        list = new TreeSet<>(stackComparator(comparator));
        list.addAll(temp);
        refresh();
    }

    @Override
    public void refresh() {
        view.clear();
        dsp.clear();
        if (filter != null) {
            list.forEach(item -> {
                // filter.test() is pretty expensive, so avoid it when we can.
                if (filter.test(item)) {
                    view.add(item);
                    dsp.add(item.getItemStack());
                }
            });
        } else {
            list.forEach(item -> {
                view.add(item);
                dsp.add(item.getItemStack());
            });
        }

    }

    @Override
    public void update(IAEItemStack stack) {
        this.list.remove(stack);
        this.list.add(stack);
    }

    @Override
    public void clear() {
        this.list.clear();
        this.view.clear();
        this.dsp.clear();
    }

    @Override
    public Iterator<IAEItemStack> iterator() {
        return view.iterator();
    }

    private Comparator<IAEItemStack> stackComparator(Comparator<IAEItemStack> cmp) {
        return (stack1, stack2) -> Platform.isSameItem(stack1.getItemStack(), stack2.getItemStack()) ? 0
                : cmp.compare(stack1, stack2);
    }
}
