package appeng.crafting.v2.resolvers;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;
import appeng.crafting.v2.CraftingRequest.SubstitutionMode;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.Platform;
import appeng.util.item.HashBasedItemList;
import com.google.common.collect.ImmutableList;
import java.util.*;
import java.util.Map.Entry;
import javax.annotation.Nonnull;
import net.minecraft.world.World;

public class CraftableItemResolver implements CraftingRequestResolver<IAEItemStack> {
    public static class RequestAndPerCraftAmount {
        public final CraftingRequest<IAEItemStack> request;
        public final long perCraftAmount;

        public RequestAndPerCraftAmount(CraftingRequest<IAEItemStack> request, long perCraftAmount) {
            this.request = request;
            this.perCraftAmount = perCraftAmount;
        }
    }

    public static class CraftFromPatternTask extends CraftingTask {
        public final CraftingRequest<IAEItemStack> request;
        public final ICraftingPatternDetails pattern;
        public final boolean allowSimulation;
        // Inputs needed to kickstart recursive crafting
        protected final IAEItemStack[] patternRecursionInputs;
        // With the recursive part subtracted
        protected final IAEItemStack[] patternInputs;
        // With the recursive part subtracted
        protected final IAEItemStack[] patternOutputs;
        protected final IAEItemStack matchingOutput;
        protected final Map<IAEItemStack, RequestAndPerCraftAmount> childRequests = new HashMap<>();
        protected final Map<IAEItemStack, CraftingRequest<IAEItemStack>> childRecursionRequests = new HashMap<>();
        // byproduct injected -> amount per craft
        protected final IdentityHashMap<IAEItemStack, Long> byproducts = new IdentityHashMap<>();
        protected boolean requestedInputs = false;
        protected long totalCraftsDone = 0;

        public CraftFromPatternTask(
                CraftingRequest<IAEItemStack> request,
                ICraftingPatternDetails pattern,
                int priority,
                boolean allowSimulation) {
            super(priority);
            this.request = request;
            this.pattern = pattern;
            this.allowSimulation = allowSimulation;

            HashBasedItemList pInputs = new HashBasedItemList();
            HashBasedItemList pOutputs = new HashBasedItemList();
            HashBasedItemList pRecInputs = new HashBasedItemList();
            Arrays.stream(pattern.getInputs()).filter(Objects::nonNull).forEach(pInputs::add);
            Arrays.stream(pattern.getOutputs()).filter(Objects::nonNull).forEach(pOutputs::add);
            for (IAEItemStack output : pOutputs) {
                IAEItemStack input = pInputs.findPrecise(output);
                if (input != null) {
                    final long netProduced = output.getStackSize() - input.getStackSize();
                    if (netProduced > 0) {
                        pRecInputs.add(input);
                        input.setStackSize(0);
                        output.setStackSize(netProduced);
                    } else {
                        // Ensure recInput.stackSize + input.stackSize == original input.stackSize
                        pRecInputs.add(input.copy().setStackSize(input.getStackSize() + netProduced));
                        input.setStackSize(-netProduced);
                        output.setStackSize(0);
                    }
                }
            }
            this.patternInputs = pInputs.toArray(new IAEItemStack[0]);
            this.patternOutputs = pOutputs.toArray(new IAEItemStack[0]);
            this.patternRecursionInputs = pRecInputs.toArray(new IAEItemStack[0]);
            IAEItemStack foundMatchingOutput = null;
            for (final IAEItemStack patternOutput : patternOutputs) {
                if (isOutputSameAs(patternOutput)) {
                    foundMatchingOutput = patternOutput;
                    break;
                }
            }
            if (foundMatchingOutput == null) {
                state = State.FAILURE;
                throw new IllegalStateException("Invalid pattern crafting step for " + request);
            }
            this.matchingOutput = foundMatchingOutput;
        }

        public boolean isOutputSameAs(IAEItemStack otherStack) {
            if (request.substitutionMode == SubstitutionMode.ACCEPT_FUZZY) {
                return this.request.stack.fuzzyComparison(otherStack, FuzzyMode.IGNORE_ALL);
            } else {
                return this.request.stack.isSameType(otherStack);
            }
        }

        public boolean isValidSubstitute(IAEItemStack reference, IAEItemStack stack, World world) {
            if (!pattern.isCraftable()) {
                return true;
            }
            IAEItemStack[] rawInputs = pattern.getInputs();
            for (int slot = 0; slot < rawInputs.length; slot++) {
                if (rawInputs[slot] != null && rawInputs[slot].isSameType(reference)) {
                    return pattern.isValidItemForSlot(slot, stack.getItemStack(), world);
                }
            }
            return true;
        }

        @Override
        public StepOutput calculateOneStep(CraftingContext context) {
            if (request.remainingToProcess <= 0) {
                state = State.SUCCESS;
                return new StepOutput(Collections.emptyList());
            }
            final boolean canUseSubstitutes = pattern.canSubstitute();
            final SubstitutionMode childMode =
                    canUseSubstitutes ? SubstitutionMode.ACCEPT_FUZZY : SubstitutionMode.PRECISE;
            final long toCraft = Platform.ceilDiv(request.remainingToProcess, matchingOutput.getStackSize());

            if (requestedInputs) {
                // Calculate how many full recipes we could fulfill
                long maxCraftable = toCraft;
                for (CraftingRequest<IAEItemStack> recInputChild : childRecursionRequests.values()) {
                    if (recInputChild.remainingToProcess > 0) {
                        // If we can't resolve an input to the recursive process, we can't craft anything at all
                        maxCraftable = 0;
                    }
                }
                for (RequestAndPerCraftAmount inputChildPair : childRequests.values()) {
                    final CraftingRequest<IAEItemStack> inputChild = inputChildPair.request;
                    final long costPerRecipe = inputChild.stack.getStackSize() / toCraft;
                    final long available = inputChild.stack.getStackSize() - inputChild.remainingToProcess;
                    final long fullRecipes = available / costPerRecipe;
                    maxCraftable = Math.min(maxCraftable, fullRecipes);
                }
                // Fulfill those recipes
                request.fulfill(
                        this,
                        matchingOutput
                                .copy()
                                .setStackSize(Math.multiplyExact(maxCraftable, matchingOutput.getStackSize())),
                        context);
                for (IAEItemStack output : patternOutputs) {
                    // add byproducts to the system
                    if (output != matchingOutput) {
                        final IAEItemStack injected =
                                output.copy().setStackSize(Math.multiplyExact(maxCraftable, output.getStackSize()));
                        context.itemModel.injectItems(injected, Actionable.MODULATE, context.actionSource);
                        this.byproducts.put(injected.copy(), output.getStackSize());
                    }
                }
                this.totalCraftsDone = maxCraftable;
                if (maxCraftable != toCraft) {
                    // Need to refund some items as not everything could be crafted.
                    for (RequestAndPerCraftAmount inputChildPair : childRequests.values()) {
                        final CraftingRequest<IAEItemStack> inputChild = inputChildPair.request;
                        final long actuallyNeeded = Math.multiplyExact(inputChild.stack.getStackSize(), maxCraftable);
                        final long produced =
                                inputChild.stack.getStackSize() - Math.max(inputChild.remainingToProcess, 0);
                        if (produced > actuallyNeeded) {
                            if (maxCraftable == 0) {
                                inputChild.fullRefund(context);
                            } else {
                                inputChild.partialRefund(context, produced - actuallyNeeded);
                            }
                        }
                    }
                    // If we couldn't craft even a single recipe, refund recursive inputs too
                    if (maxCraftable == 0) {
                        for (CraftingRequest<IAEItemStack> recChild : childRecursionRequests.values()) {
                            recChild.fullRefund(context);
                        }
                    }
                }
                state = State.SUCCESS;
                return new StepOutput(Collections.emptyList());
            } else {
                ArrayList<CraftingRequest<IAEItemStack>> newChildren = new ArrayList<>(patternInputs.length);
                if (patternRecursionInputs.length > 0) {
                    for (IAEItemStack recInput : patternRecursionInputs) {
                        CraftingRequest<IAEItemStack> req = new CraftingRequest<>(
                                recInput.copy(),
                                childMode,
                                IAEItemStack.class,
                                allowSimulation,
                                stack -> this.isValidSubstitute(recInput, stack, context.world));
                        newChildren.add(req);
                        childRecursionRequests.put(recInput, req);
                    }
                    state = State.NEEDS_MORE_WORK;
                }
                for (IAEItemStack input : patternInputs) {
                    final long amount = Math.multiplyExact(input.getStackSize(), toCraft);
                    CraftingRequest<IAEItemStack> req = new CraftingRequest<>(
                            input.copy().setStackSize(amount),
                            childMode,
                            IAEItemStack.class,
                            allowSimulation,
                            stack -> this.isValidSubstitute(input, stack, context.world));
                    newChildren.add(req);
                    childRequests.put(input, new RequestAndPerCraftAmount(req, input.getStackSize()));
                }
                requestedInputs = true;
                state = State.NEEDS_MORE_WORK;
                return new StepOutput(Collections.unmodifiableList(newChildren));
            }
        }

        @Override
        public void partialRefund(CraftingContext context, long amount) {
            if (amount >= totalCraftsDone) {
                fullRefund(context);
                return;
            }
            totalCraftsDone -= amount;
            amount = amount / matchingOutput.getStackSize();
            if (amount == 0) {
                return;
            }
            for (RequestAndPerCraftAmount subrequest : childRequests.values()) {
                subrequest.request.partialRefund(context, subrequest.perCraftAmount * amount);
            }
            for (Entry<IAEItemStack, Long> entry : byproducts.entrySet()) {
                final IAEItemStack byproductStack = entry.getKey();
                final long perCraft = entry.getValue();
                context.itemModel.extractItems(
                        byproductStack.copy().setStackSize(perCraft * amount),
                        Actionable.MODULATE,
                        context.actionSource);
                entry.getKey().setStackSize(byproductStack.getStackSize() - amount * perCraft);
            }
        }

        @Override
        public void fullRefund(CraftingContext context) {
            totalCraftsDone = 0;
            childRequests.values().forEach(req -> req.request.fullRefund(context));
            childRequests.clear();
            childRecursionRequests.values().forEach(req -> req.fullRefund(context));
            childRecursionRequests.clear();
        }

        @Override
        public void populatePlan(IItemList<IAEItemStack> targetPlan) {
            if (totalCraftsDone == 0) {
                return;
            }
            for (IAEItemStack output : patternOutputs) {
                targetPlan.addRequestable(output.copy().setCountRequestable(output.getStackSize() * totalCraftsDone));
            }
        }

        @Override
        public void startOnCpu(
                CraftingContext context, CraftingCPUCluster cpuCluster, MECraftingInventory craftingInv) {
            cpuCluster.addCrafting(pattern, totalCraftsDone);
        }

        @Override
        public String toString() {
            return "CraftFromPatternTask{" + "request="
                    + request + ", pattern="
                    + pattern + ", allowSimulation="
                    + allowSimulation + ", matchingOutput="
                    + matchingOutput + ", requestedInputs="
                    + requestedInputs + ", totalCraftsDone="
                    + totalCraftsDone + ", priority="
                    + priority + ", state="
                    + state + '}';
        }
    }

    @Nonnull
    @Override
    public List<CraftingTask> provideCraftingRequestResolvers(
            @Nonnull CraftingRequest<IAEItemStack> request, @Nonnull CraftingContext context) {
        ImmutableList.Builder<CraftingTask> tasks = new ImmutableList.Builder<>();
        final List<ICraftingPatternDetails> patterns = new ArrayList<>(context.getPrecisePatternsFor(request.stack));
        patterns.sort(Comparator.comparing(ICraftingPatternDetails::getPriority).reversed());
        // If fuzzy patterns are allowed,
        if (request.substitutionMode == SubstitutionMode.ACCEPT_FUZZY) {
            final List<ICraftingPatternDetails> fuzzyPatterns =
                    new ArrayList<>(context.getFuzzyPatternsFor(request.stack));
            fuzzyPatterns.sort(
                    Comparator.comparing(ICraftingPatternDetails::getPriority).reversed());
            patterns.addAll(fuzzyPatterns);
        }
        int priority = CraftingTask.PRIORITY_CRAFT_OFFSET + patterns.size() - 1;
        for (ICraftingPatternDetails pattern : patterns) {
            tasks.add(new CraftFromPatternTask(request, pattern, priority, false));
            priority--;
        }
        // Fallback: use highest priority pattern to simulate if nothing else works
        if (!patterns.isEmpty()) {
            tasks.add(new CraftFromPatternTask(request, patterns.get(0), CraftingTask.PRIORITY_SIMULATE_CRAFT, true));
        }
        return tasks.build();
    }
}
