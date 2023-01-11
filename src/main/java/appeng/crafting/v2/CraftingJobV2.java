package appeng.crafting.v2;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingContext.RequestInProcessing;
import appeng.crafting.v2.CraftingRequest.SubstitutionMode;
import appeng.crafting.v2.resolvers.CraftingTask;
import appeng.crafting.v2.resolvers.CraftingTask.State;
import appeng.hooks.TickHandler;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import java.util.List;
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
    protected boolean finished = false;

    public CraftingJobV2(
            final World world,
            final IGrid meGrid,
            final BaseActionSource actionSource,
            final IAEItemStack what,
            final ICraftingCallback callback) {
        this.context = new CraftingContext(world, meGrid, actionSource);
        this.callback = callback;
        this.originalRequest = new CraftingRequest<>(what, SubstitutionMode.PRECISE_FRESH, IAEItemStack.class, true);
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
            byteCost = 0;
            for (RequestInProcessing<?> request : context.getLiveRequests()) {
                byteCost += request.request.byteCost;
            }
            totalByteCost = byteCost;
        }
        return byteCost;
    }

    @Override
    public void populatePlan(IItemList<IAEItemStack> plan) {
        for (CraftingTask task : context.getResolvedTasks()) {
            task.populatePlan(plan);
        }
    }

    @Override
    public IAEItemStack getOutput() {
        return originalRequest.stack;
    }

    @Override
    public boolean simulateFor(int milli) {
        if (finished) {
            return false;
        }
        final long startTime = System.currentTimeMillis();
        final long finishTime = startTime + milli;
        State state = State.NEEDS_MORE_WORK;
        do {
            state = context.doWork();
            totalByteCost = -1;
        } while (state.needsMoreWork && System.currentTimeMillis() < finishTime);

        if (!state.needsMoreWork) {
            getByteTotal();
            finished = true;
            callback.calculationComplete(this);
        }

        return state.needsMoreWork;
    }

    @Override
    public void run() {
        TickHandler.INSTANCE.registerCraftingSimulation(this.context.world, this);
    }

    @Override
    public boolean supportsCPUCluster(ICraftingCPU cluster) {
        return cluster instanceof CraftingCPUCluster;
    }

    @Override
    public void startCrafting(MECraftingInventory storage, ICraftingCPU rawCluster, BaseActionSource src) {
        if (!finished) {
            throw new IllegalStateException(
                    "Trying to start crafting a not fully calculated job for " + originalRequest.toString());
        }
        CraftingCPUCluster cluster = (CraftingCPUCluster) rawCluster;
        context.actionSource = src;
        List<CraftingTask> resolvedTasks = context.getResolvedTasks();
        for (CraftingTask task : resolvedTasks) {
            task.startOnCpu(context, cluster, storage);
        }
    }
}
