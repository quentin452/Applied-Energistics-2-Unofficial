package appeng.crafting.v2;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.core.AELog;
import appeng.crafting.v2.resolvers.*;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.Level;

/**
 * You can register additional crafting handlers here
 */
public class CraftingCalculations {
    private static final ListMultimap<Class<? extends IAEStack<?>>, CraftingRequestResolver<?>> providers =
            ArrayListMultimap.create(2, 8);

    public static <StackType extends IAEStack<StackType>> void registerProvider(
            CraftingRequestResolver<StackType> provider, Class<StackType> stackTypeClass) {
        providers.put(stackTypeClass, provider);
    }

    @SuppressWarnings("unchecked")
    public static <StackType extends IAEStack<StackType>> List<CraftingTask> tryResolveCraftingRequest(
            CraftingRequest<StackType> request, CraftingContext context) {
        final ArrayList<CraftingTask> allTasks = new ArrayList<>(4);
        for (final CraftingRequestResolver<?> unsafeProvider : Multimaps.filterKeys(
                        providers, key -> key.isAssignableFrom(request.stackTypeClass))
                .values()) {
            try {
                // Safety: Filtered by type using Multimaps.filterKeys
                final CraftingRequestResolver<StackType> provider = (CraftingRequestResolver<StackType>) unsafeProvider;
                final List<CraftingTask> tasks = provider.provideCraftingRequestResolvers(request, context);
                allTasks.addAll(tasks);
            } catch (Exception t) {
                AELog.log(
                        Level.DEBUG,
                        t,
                        "Error encountered when trying to generate the list of CraftingTasks for crafting {}",
                        request.toString());
            }
        }
        allTasks.sort(CraftingTask.PRIORITY_COMPARATOR);
        return Collections.unmodifiableList(allTasks);
    }

    static {
        registerProvider(new ExtractItemResolver(), IAEItemStack.class);
        registerProvider(new SimulateMissingItemResolver<>(), IAEItemStack.class);
        registerProvider(new SimulateMissingItemResolver<>(), IAEFluidStack.class);
        registerProvider(new EmitableItemResolver(), IAEItemStack.class);
        registerProvider(new CraftableItemResolver(), IAEItemStack.class);
    }
}
