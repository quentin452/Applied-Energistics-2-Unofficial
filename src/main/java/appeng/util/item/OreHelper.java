/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.util.item;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;

import appeng.api.storage.data.IAEItemStack;

public class OreHelper {

    public static final OreHelper INSTANCE = new OreHelper();

    /**
     * A local cache to speed up OreDictionary lookups.
     */
    private final LoadingCache<String, List<ItemStack>> oreDictCache = CacheBuilder.newBuilder()
            .build(new CacheLoader<>() {

                @Override
                public List<ItemStack> load(final String oreName) {
                    return OreDictionary.getOres(oreName);
                }
            });

    private final Map<ItemRef, OreReference> references = new HashMap<>();

    /**
     * Test if the passed {@link ItemStack} is an ore.
     *
     * @param itemStack the itemstack to test
     * @return true if an ore entry exists, false otherwise
     */
    public OreReference isOre(final ItemStack itemStack) {
        final ItemRef ir = new ItemRef(itemStack);

        if (!this.references.containsKey(ir)) {
            final OreReference ref = new OreReference();
            final Collection<Integer> ores = ref.getOres();
            final Collection<String> set = ref.getEquivalents();

            for (final int id : OreDictionary.getOreIDs(itemStack)) {
                ores.add(id);
                set.add(OreDictionary.getOreName(id));
            }

            if (!set.isEmpty()) {
                this.references.put(ir, ref);
            } else {
                this.references.put(ir, null);
            }
        }

        return this.references.get(ir);
    }

    boolean sameOre(final AEItemStack aeItemStack, final IAEItemStack is) {
        if (is instanceof AEItemStack) {
            return this.sameOre(aeItemStack.getDefinition().getIsOre(), ((AEItemStack) is).getDefinition().getIsOre());
        }

        return this.sameOre(aeItemStack, is.getItemStack());
    }

    public boolean sameOre(final OreReference a, final OreReference b) {
        if (a == null || b == null) {
            return false;
        }

        if (a == b) {
            return true;
        }

        return !Sets.intersection(a.getOres(), b.getOres()).isEmpty();
    }

    boolean sameOre(final AEItemStack aeItemStack, final ItemStack o) {
        final OreReference a = aeItemStack.getDefinition().getIsOre();

        HashSet<Integer> set = new HashSet<>();
        for (final int id : OreDictionary.getOreIDs(o)) {
            set.add(id);
        }

        return !Sets.intersection(a.getOres(), set).isEmpty();
    }

    List<ItemStack> getCachedOres(final String oreName) {
        return this.oreDictCache.getUnchecked(oreName);
    }

    private static class ItemRef {

        private final Item ref;
        private final int damage;
        private final int hash;

        ItemRef(final ItemStack stack) {
            this.ref = stack.getItem();

            if (stack.getItem().isDamageable()) {
                this.damage = 0; // IGNORED
            } else {
                this.damage = stack.getItemDamage(); // might be important...
            }

            this.hash = this.ref.hashCode() ^ this.damage;
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == null) {
                return false;
            }
            if (this.getClass() != obj.getClass()) {
                return false;
            }
            final ItemRef other = (ItemRef) obj;
            return this.damage == other.damage && this.ref == other.ref;
        }

        @Override
        public String toString() {
            return "ItemRef [ref=" + this.ref
                    .getUnlocalizedName() + ", damage=" + this.damage + ", hash=" + this.hash + ']';
        }
    }
}
