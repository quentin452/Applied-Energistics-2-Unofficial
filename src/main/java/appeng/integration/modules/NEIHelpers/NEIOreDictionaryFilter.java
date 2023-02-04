package appeng.integration.modules.NEIHelpers;

import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.implementations.GuiOreFilter;
import appeng.util.prioitylist.OreFilteredList;
import codechicken.nei.SearchField.ISearchProvider;
import codechicken.nei.api.ItemFilter;

public class NEIOreDictionaryFilter implements ISearchProvider {

    public Pattern pattern;

    @Override
    public ItemFilter getFilter(String searchText) {
        if (Minecraft.getMinecraft().currentScreen instanceof GuiOreFilter) {
            Pattern pattern = null;
            try {
                pattern = Pattern.compile(searchText);
            } catch (PatternSyntaxException ignored) {}
            return pattern == null || pattern.toString().length() == 0 ? null : new Filter(pattern);
        } else {
            return null;
        }
    }

    @Override
    public boolean isPrimary() {
        return true;
    }

    public static class Filter implements ItemFilter {

        Pattern pattern;
        Predicate<IAEItemStack> list;

        public Filter(Pattern pattern) {
            this.pattern = pattern;
            this.list = OreFilteredList.makeFilter(pattern.pattern());
        }

        @Override
        public boolean matches(ItemStack itemStack) {
            if (Minecraft.getMinecraft().currentScreen instanceof GuiOreFilter) {
                return list.test(AEApi.instance().storage().createItemStack(itemStack));
            }
            return false;
        }
    }
}
