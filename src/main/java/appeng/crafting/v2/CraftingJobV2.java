package appeng.crafting.v2;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.v2.CraftingRequest.SubstitutionMode;
import appeng.crafting.v2.CraftingTask.State;
import java.util.*;
import net.minecraft.world.World;

/**
 * A new, self-contained implementation of the crafting calculator.
 * Does an iterative search on the crafting recipe tree.
 */
public class CraftingJobV2 implements ICraftingJob {

    protected volatile long totalByteCost = -1; // -1 means it needs to be recalculated

    protected CraftingContext context;
    protected final CraftingRequest<IAEItemStack> originalRequest;
    protected ICraftingCallback callback;

    public CraftingJobV2(
            final World world,
            final IGrid meGrid,
            final BaseActionSource actionSource,
            final IAEItemStack what,
            final ICraftingCallback callback) {
        this.context = new CraftingContext(world, meGrid, actionSource);
        this.callback = callback;
        this.originalRequest = new CraftingRequest<>(what, SubstitutionMode.PRECISE, IAEItemStack.class);
        this.context.addRequest(this.originalRequest);
        this.context.itemModel.ignore(what);
    }

    @Override
    public boolean isSimulation() {
        return context.wasSimulated;
    }

    @Override
    public long getByteTotal() {
        long byteCost = totalByteCost;
        if (byteCost < 0) {
            // TODO
            totalByteCost = byteCost;
        }
        return byteCost;
    }

    @Override
    public void populatePlan(IItemList<IAEItemStack> plan) {}

    @Override
    public IAEItemStack getOutput() {
        return originalRequest.stack;
    }

    @Override
    public boolean simulateFor(int milli) {
        final long startTime = System.currentTimeMillis();
        final long finishTime = startTime + milli;
        State state = State.NEEDS_MORE_WORK;
        do {
            state = context.doWork();
        } while (state.needsMoreWork && System.currentTimeMillis() < finishTime);
        return state.needsMoreWork;
    }
}
