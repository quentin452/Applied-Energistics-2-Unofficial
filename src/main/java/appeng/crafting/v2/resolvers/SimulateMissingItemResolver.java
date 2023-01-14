package appeng.crafting.v2.resolvers;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;

public class SimulateMissingItemResolver<StackType extends IAEStack<StackType>>
        implements CraftingRequestResolver<StackType> {
    public static class ConjureItemTask<StackType extends IAEStack<StackType>> extends CraftingTask {
        public final CraftingRequest<StackType> request;

        public ConjureItemTask(CraftingRequest<StackType> request) {
            super(CraftingTask.PRIORITY_SIMULATE); // conjure items for calculations out of thin air as a last resort
            this.request = request;
        }

        @Override
        public StepOutput calculateOneStep(CraftingContext context) {
            state = State.SUCCESS;
            if (request.remainingToProcess <= 0) {
                return new StepOutput(Collections.emptyList());
            }
            // Simulate items existing
            request.wasSimulated = true;
            context.wasSimulated = true;
            request.fulfill(this, request.stack.copy().setStackSize(request.remainingToProcess), context);
            return new StepOutput(Collections.emptyList());
        }

        @Override
        public long partialRefund(CraftingContext context, long amount) {
            // no-op: items were simulated, so there's nothing to refund
            return amount;
        }

        @Override
        public void fullRefund(CraftingContext context) {
            // no-op: items were simulated, so there's nothing to refund
        }

        @Override
        public void populatePlan(IItemList<IAEItemStack> targetPlan) {
            if (request.stack instanceof IAEItemStack) {
                targetPlan.add((IAEItemStack) request.stack.copy());
            }
        }

        @Override
        public void startOnCpu(
                CraftingContext context, CraftingCPUCluster cpuCluster, MECraftingInventory craftingInv) {
            throw new IllegalStateException("Trying to start crafting a schedule with simulated items");
        }

        @Override
        public String toString() {
            return "ConjureItemTask{" + "request=" + request + ", priority=" + priority + ", state=" + state + '}';
        }
    }

    @Nonnull
    @Override
    public List<CraftingTask> provideCraftingRequestResolvers(
            @Nonnull CraftingRequest<StackType> request, @Nonnull CraftingContext context) {
        if (request.allowSimulation) {
            return Collections.singletonList(new ConjureItemTask<StackType>(request));
        } else {
            return Collections.emptyList();
        }
    }
}
