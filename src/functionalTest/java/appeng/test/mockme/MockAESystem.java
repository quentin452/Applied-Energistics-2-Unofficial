package appeng.test.mockme;

import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.crafting.v2.CraftingJobV2;
import appeng.helpers.PatternHelper;
import appeng.me.cache.CraftingGridCache;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

public class MockAESystem {
    public final World world;
    public final MockGrid grid = new MockGrid();
    public final BaseActionSource dummyActionSource = new BaseActionSource();
    public final CraftingGridCache cgCache;
    private boolean dirtyPatterns = false;

    public MockAESystem(World world) {
        this.world = world;
        this.cgCache = grid.getCache(ICraftingGrid.class);
    }

    public CraftingJobV2 makeCraftingJob(ItemStack request) {
        if (dirtyPatterns) {
            dirtyPatterns = false;
            this.cgCache.setMockPatternsFromMethods();
        }
        return new CraftingJobV2(world, grid, dummyActionSource, AEItemStack.create(request), null);
    }

    public PatternBuilder newProcessingPattern() {
        dirtyPatterns = true;
        return new PatternBuilder(false);
    }

    public class PatternBuilder {
        public final boolean isCrafting;
        public final List<ItemStack> inputs = new ArrayList<>(9);
        public final List<ItemStack> outputs = new ArrayList<>(9);
        public boolean canUseSubstitutes = false;
        public boolean canBeSubstitute = false;

        private PatternBuilder(boolean isCrafting) {
            this.isCrafting = isCrafting;
        }

        public PatternBuilder allowUsingSubstitutes() {
            this.canUseSubstitutes = true;
            return this;
        }

        public PatternBuilder allowBeingASubstitute() {
            this.canBeSubstitute = true;
            return this;
        }

        public PatternBuilder addInput(ItemStack stack) {
            inputs.add(stack);
            return this;
        }

        public PatternBuilder addOutput(ItemStack stack) {
            outputs.add(stack);
            return this;
        }

        public void buildAndAdd() {
            final ItemStack encodedPattern = AEApi.instance()
                    .definitions()
                    .items()
                    .encodedPattern()
                    .maybeStack(1)
                    .get();
            final NBTTagCompound patternTags = new NBTTagCompound();
            patternTags.setBoolean("crafting", isCrafting);
            patternTags.setBoolean("substitute", canUseSubstitutes);
            patternTags.setBoolean("beSubstitute", canBeSubstitute);
            patternTags.setBoolean("crafting", isCrafting);
            final NBTTagList ins = new NBTTagList();
            final NBTTagList outs = new NBTTagList();
            for (ItemStack input : inputs) {
                NBTTagCompound nbt = new NBTTagCompound();
                if (input != null) {
                    Platform.writeItemStackToNBT(input, nbt);
                }
                ins.appendTag(nbt);
            }
            patternTags.setTag("in", ins);
            for (ItemStack output : outputs) {
                NBTTagCompound nbt = new NBTTagCompound();
                if (output != null) {
                    Platform.writeItemStackToNBT(output, nbt);
                }
                outs.appendTag(nbt);
            }
            patternTags.setTag("out", outs);
            encodedPattern.setTagCompound(patternTags);
            PatternHelper helper = new PatternHelper(encodedPattern, world);
            cgCache.addCraftingOption(new MockCraftingMedium(), helper);
        }
    }
}
