package appeng.crafting.v2.resolvers;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;
import appeng.crafting.v2.CraftingTask;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;

public class ExtractItemResolver implements CraftingRequestResolver<IAEItemStack> {
    public static class ExtractItemTask extends CraftingTask {
        public final CraftingRequest<IAEItemStack> request;

        public ExtractItemTask(CraftingRequest<IAEItemStack> request) {
            super(CraftingTask.PRIORITY_EXTRACT); // always try to extract items first
            this.request = request;
        }

        @Override
        public StepOutput calculateOneStep(CraftingContext context) {
            state = State.SUCCESS;
            if (request.remainingToProcess <= 0) {
                return new StepOutput(Collections.emptyList());
            }
            // Extract exact
            IAEItemStack exactMatching = context.itemModel.getItemList().findPrecise(request.stack);
            if (exactMatching != null) {
                final long requestSize = Math.min(request.remainingToProcess, exactMatching.getStackSize());
                final IAEItemStack extracted = context.itemModel.extractItems(
                        exactMatching.copy().setStackSize(requestSize), Actionable.MODULATE, context.actionSource);
                request.fulfill(this, extracted, context);
            }
            // Extract fuzzy
            if (request.remainingToProcess > 0
                    && request.substitutionMode == CraftingRequest.SubstitutionMode.ACCEPT_FUZZY) {
                Collection<IAEItemStack> fuzzyMatching =
                        context.itemModel.getItemList().findFuzzy(request.stack, FuzzyMode.IGNORE_ALL);
                for (final IAEItemStack candidate : fuzzyMatching) {
                    if (candidate == null) {
                        continue;
                    }
                    if (request.acceptableSubstituteFn.test(candidate)) {
                        final long requestSize = Math.min(request.remainingToProcess, candidate.getStackSize());
                        final IAEItemStack extracted = context.itemModel.extractItems(
                                candidate.copy().setStackSize(requestSize), Actionable.MODULATE, context.actionSource);
                        request.fulfill(this, extracted, context);
                    }
                }
            }
            return new StepOutput(Collections.emptyList());
        }
    }

    @Nonnull
    @Override
    public List<CraftingTask> provideCraftingRequestResolvers(
            @Nonnull CraftingRequest<IAEItemStack> request, @Nonnull CraftingContext context) {
        if (request.substitutionMode == CraftingRequest.SubstitutionMode.PRECISE_FRESH) {
            return Collections.emptyList();
        } else {
            return Collections.singletonList(new ExtractItemTask(request));
        }
    }
}
