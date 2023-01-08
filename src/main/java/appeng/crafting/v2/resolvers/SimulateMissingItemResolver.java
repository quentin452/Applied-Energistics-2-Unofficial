package appeng.crafting.v2.resolvers;

import appeng.api.storage.data.IAEStack;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;
import appeng.crafting.v2.CraftingTask;
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
            if (request.remainingToProcess <= 0) {
                return new StepOutput(Collections.emptyList());
            }
            // Simulate items existing
            request.wasSimulated = true;
            context.wasSimulated = true;
            request.fulfill(this, request.stack.copy().setStackSize(request.remainingToProcess), context);
            return new StepOutput(Collections.emptyList());
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
