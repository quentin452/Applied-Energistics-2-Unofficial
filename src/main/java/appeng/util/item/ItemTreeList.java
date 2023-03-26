package appeng.util.item;

import java.util.*;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.IItemTree;
import appeng.api.storage.data.IAEItemStack;

public class ItemTreeList implements IItemTree {

    private final List<IAEItemStack> view;
    private final List<ItemStack> dsp;
    private TreeMap<IAEItemStack, IAEItemStack> tree;
    private Predicate<IAEItemStack> filter;

    public ItemTreeList(Comparator<IAEItemStack> comparator, Predicate<IAEItemStack> filter) {
        this.dsp = new ArrayList<>();
        this.view = new ArrayList<>();
        this.tree = new TreeMap<>(stackComparator(comparator));
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
        ArrayList<IAEItemStack> temp = new ArrayList<>(tree.keySet());
        tree = new TreeMap<>(stackComparator(comparator));
        temp.forEach(stack -> tree.put(stack, stack));
        refresh();
    }

    @Override
    public void refresh() {
        view.clear();
        dsp.clear();
        if (filter != null) {
            tree.forEach((item, __) -> {
                // filter.test() is pretty expensive, so avoid it when we can.
                if (filter.test(item)) {
                    view.add(item);
                    dsp.add(item.getItemStack());
                }
            });
        } else {
            tree.forEach((item, __) -> {
                view.add(item);
                dsp.add(item.getItemStack());
            });
        }

    }

    @Override
    public void clear() {
        this.tree.clear();
        this.view.clear();
        this.dsp.clear();
    }

    @Override
    public int fullSize() {
        return this.tree.size();
    }

    @Override
    public void addStorage(IAEItemStack option) {
        if (option == null) return;
        IAEItemStack stack = this.tree.putIfAbsent(option, option);
        if (stack != null) {
            stack.incStackSize(option.getStackSize());
        }
    }

    @Override
    public void addCrafting(IAEItemStack option) {
        if (option == null) return;
        IAEItemStack stack = this.tree.get(option);
        if (stack != null) {
            stack.setCraftable(true);
        } else {
            final IAEItemStack copy = option.copy();
            copy.setStackSize(0).setCraftable(true);
            this.tree.put(copy, copy);
        }
    }

    @Override
    public void addRequestable(IAEItemStack option) {
        if (option == null) return;
        final IAEItemStack st = this.tree.get(option);
        if (st != null) {
            st.setCountRequestable(st.getCountRequestable() + option.getCountRequestable());
        } else {
            final IAEItemStack copy = option.copy();
            copy.setStackSize(0).setCraftable(false).setCountRequestable(option.getCountRequestable());
            this.tree.put(copy, copy);
        }
    }

    @Override
    public IAEItemStack getFirstItem() {
        return this.tree.firstKey();
    }

    @Override
    public int size() {
        return this.view.size();
    }

    @Override
    public Iterator<IAEItemStack> iterator() {
        return view.iterator();
    }

    @Override
    public void resetStatus() {
        for (final IAEItemStack i : this.tree.keySet()) {
            i.reset();
        }
    }

    @Override
    public void add(IAEItemStack option) {
        if (option == null) return;
        IAEItemStack stack = this.tree.putIfAbsent(option, option);
        if (stack != null) {
            stack.add(option);
        }
    }

    @Override
    public IAEItemStack findPrecise(IAEItemStack i) {
        return this.tree.get(i);
    }

    @Override
    public Collection<IAEItemStack> findFuzzy(IAEItemStack input, FuzzyMode fuzzy) {
        if (input == null) {
            return Collections.emptyList();
        }

        final AEItemStack ais = (AEItemStack) input;

        if (ais.isOre()) {
            final OreReference or = ais.getDefinition().getIsOre();

            if (or.getAEEquivalents().size() == 1) {
                final IAEItemStack is = or.getAEEquivalents().get(0);

                return this
                        .findFuzzyDamage((AEItemStack) is, fuzzy, is.getItemDamage() == OreDictionary.WILDCARD_VALUE);
            } else {
                final Collection<IAEItemStack> output = new LinkedList<IAEItemStack>();

                for (final IAEItemStack is : or.getAEEquivalents()) {
                    output.addAll(
                            this.findFuzzyDamage(
                                    (AEItemStack) is,
                                    fuzzy,
                                    is.getItemDamage() == OreDictionary.WILDCARD_VALUE));
                }

                return output;
            }
        }
        return this.findFuzzyDamage(ais, fuzzy, false);
    }

    @Override
    public boolean isEmpty() {
        return this.tree.isEmpty();
    }

    private Comparator<IAEItemStack> stackComparator(Comparator<IAEItemStack> cmp) {
        return (stack1, stack2) -> stack1.isSameType(stack2) ? 0 : cmp.compare(stack1, stack2);
    }

    private Collection<IAEItemStack> findFuzzyDamage(final AEItemStack filter, final FuzzyMode fuzzy,
            final boolean ignoreMeta) {
        final IAEItemStack low = filter.getLow(fuzzy, ignoreMeta);
        final IAEItemStack high = filter.getHigh(fuzzy, ignoreMeta);

        return this.tree.subMap(low, true, high, true).descendingMap().values();
    }
}
