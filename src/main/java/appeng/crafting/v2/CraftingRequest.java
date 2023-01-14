package appeng.crafting.v2;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.crafting.v2.resolvers.CraftingTask;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.apache.commons.lang3.tuple.MutablePair;

/**
 * A single requested stack (item or fluid) to craft, e.g. 32x Torches
 *
 * @param <StackType> Should be {@link IAEItemStack} or {@link appeng.api.storage.data.IAEFluidStack}
 */
public class CraftingRequest<StackType extends IAEStack<StackType>> {
    public enum SubstitutionMode {
        /**
         * No substitution, do not use items from the AE system - used for user-started requests
         */
        PRECISE_FRESH,
        /**
         * Use precisely the item requested
         */
        PRECISE,
        /**
         * Allow fuzzy matching of ingredients, the request will have a {@link CraftingRequest#acceptableSubstituteFn} predicate to determine if the given fuzzy match item is valid
         */
        ACCEPT_FUZZY
    }

    public final Class<StackType> stackTypeClass;
    /**
     * An item/fluid + count representing how many need to be crafted
     */
    public final StackType stack;

    public final SubstitutionMode substitutionMode;
    public final Predicate<StackType> acceptableSubstituteFn;
    // (task, amount fulfilled by task)
    public final List<MutablePair<CraftingTask, Long>> usedResolvers = new ArrayList<>();
    /**
     * Whether this request and its children can be fulfilled by simulations
     */
    public final boolean allowSimulation;
    /**
     * The number of yet-unresolved elements from the stack (items/mB) that need to be crafted.
     */
    public volatile long remainingToProcess;

    private volatile long byteCost = 0;
    private volatile long untransformedByteCost = 0;
    /**
     * If the item had to be simulated (there was not enough ingredients in the system to fulfill this request in any way)
     */
    public volatile boolean wasSimulated = false;

    /**
     * @param stack                  The item/fluid and stack to request
     * @param substitutionMode       Whether and how to allow substitutions when resolving this request
     * @param stackTypeClass         Pass in {@code StackType.class}, needed for resolving types at runtime
     * @param acceptableSubstituteFn A predicate testing if a given item (in fuzzy mode) can fulfill the request
     */
    public CraftingRequest(
            StackType stack,
            SubstitutionMode substitutionMode,
            Class<StackType> stackTypeClass,
            boolean allowSimulation,
            Predicate<StackType> acceptableSubstituteFn) {
        this.stackTypeClass = stackTypeClass;
        this.stack = stack;
        this.substitutionMode = substitutionMode;
        this.acceptableSubstituteFn = acceptableSubstituteFn;
        this.remainingToProcess = stack.getStackSize();
        this.allowSimulation = allowSimulation;
        if (!(stackTypeClass == IAEItemStack.class || stackTypeClass == IAEFluidStack.class)) {
            throw new IllegalArgumentException(
                    "Invalid stack type for a crafting request: " + stackTypeClass.getName());
        }
    }

    /**
     * @param request          The item/fluid and stack to request
     * @param substitutionMode Whether and how to allow substitutions when resolving this request
     * @param stackTypeClass   Pass in {@code StackType.class}, needed for resolving types at runtime
     */
    public CraftingRequest(
            StackType request,
            SubstitutionMode substitutionMode,
            Class<StackType> stackTypeClass,
            boolean allowSimulation) {
        this(request, substitutionMode, stackTypeClass, allowSimulation, stack -> true);
        if (substitutionMode == SubstitutionMode.ACCEPT_FUZZY) {
            throw new IllegalArgumentException("Fuzzy requests must have a substitution-valid predicate");
        }
    }

    /**
     * The cost in bytes to process this task so far
     */
    public long getByteCost() {
        return byteCost;
    }

    @Override
    public String toString() {
        return "CraftingRequest{request=" + stack + ", substitutionMode=" + substitutionMode + ", remainingToProcess="
                + remainingToProcess + ", byteCost=" + byteCost + ", wasSimulated=" + wasSimulated + '}';
    }

    /**
     * Reduces the items needed to fulfill this request, and adds any leftovers into the item cache of the context.
     */
    public void fulfill(CraftingTask origin, StackType input, CraftingContext context) {
        if (input == null || input.getStackSize() == 0) {
            return;
        }
        if (input.getStackSize() < 0) {
            throw new IllegalArgumentException(
                    "Can't fulfill crafting request with a negative amount of " + input + " : " + this);
        }
        if (this.remainingToProcess < input.getStackSize()) {
            throw new IllegalArgumentException(
                    "Can't fulfill crafting request with too many of " + input + " : " + this);
        }
        this.untransformedByteCost += input.getStackSize();
        this.byteCost = CraftingCalculations.adjustByteCost(this, untransformedByteCost);
        this.remainingToProcess -= input.getStackSize();
        this.usedResolvers.add(MutablePair.of(origin, input.getStackSize()));
    }

    /**
     * Reduces the amount of items needed by {@code amount}, propagating any necessary refunds via the resolver crafting tasks.
     */
    public void partialRefund(CraftingContext context, long amount) {
        long remainingTaskAmount = amount;
        for (MutablePair<CraftingTask, Long> task : usedResolvers) {
            if (remainingTaskAmount <= 0) {
                break;
            }
            if (task.getRight() <= 0) {
                continue;
            }
            final long taskRefunded =
                    task.getLeft().partialRefund(context, Math.min(remainingTaskAmount, task.getRight()));
            remainingTaskAmount -= taskRefunded;
            task.setRight(task.getRight() - taskRefunded);
        }
        if (remainingTaskAmount < 0) {
            throw new IllegalStateException("Refunds resulted in a negative amount of an item for request " + this);
        }
        if (remainingTaskAmount != 0) {
            throw new IllegalStateException("Partial refunds could not cover all resolved items for request " + this);
        }
        final long processed = this.stack.getStackSize() - this.remainingToProcess;
        this.stack.setStackSize(this.stack.getStackSize() - amount);
        this.remainingToProcess = this.stack.getStackSize() - processed;
        this.untransformedByteCost -= amount;
        this.byteCost = CraftingCalculations.adjustByteCost(this, untransformedByteCost);
        if (this.remainingToProcess < 0) {
            throw new IllegalArgumentException("Refunded more items than were resolved for request " + this);
        }
    }

    public void fullRefund(CraftingContext context) {
        for (MutablePair<CraftingTask, Long> task : usedResolvers) {
            task.getLeft().fullRefund(context);
        }
        this.remainingToProcess = 0;
        this.untransformedByteCost = 0;
        this.byteCost = CraftingCalculations.adjustByteCost(this, untransformedByteCost);
        this.stack.setStackSize(0);
        this.usedResolvers.clear();
    }
}
