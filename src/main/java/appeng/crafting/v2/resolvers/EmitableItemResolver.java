package appeng.crafting.v2.resolvers;

import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;
import appeng.crafting.v2.CraftingTask;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;

public class EmitableItemResolver implements CraftingRequestResolver<IAEItemStack> {
    public static class EmitItemTask extends CraftingTask {
        public final CraftingRequest<IAEItemStack> request;

        public EmitItemTask(CraftingRequest<IAEItemStack> request) {
            super(CraftingTask.PRIORITY_CRAFTING_EMITTER); // conjure items for calculations out of thin air as a last
            // resort
            this.request = request;
        }

        @Override
        public StepOutput calculateOneStep(CraftingContext context) {
            if (request.remainingToProcess <= 0) {
                return new StepOutput(Collections.emptyList());
            }
            // Assume items will be generated, triggered by the emitter
            request.fulfill(this, request.stack.copy().setStackSize(request.remainingToProcess), context);
            return new StepOutput(Collections.emptyList());
        }
    }

    @Nonnull
    @Override
    public List<CraftingTask> provideCraftingRequestResolvers(
            @Nonnull CraftingRequest<IAEItemStack> request, @Nonnull CraftingContext context) {
        if (context.craftingGrid.canEmitFor(request.stack)) {
            return Collections.singletonList(new EmitItemTask(request));
        } else {
            return Collections.emptyList();
        }
    }
}
