package appeng.test;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.v2.CraftingJobV2;
import appeng.test.mockme.MockAESystem;
import appeng.util.item.AEItemStack;
import appeng.util.item.ItemList;
import java.io.File;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.*;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraftforge.common.DimensionManager;
import org.junit.jupiter.api.Test;

public class CraftingV2Tests {
    final World dummyWorld;
    final int SIMPLE_SIMULATION_TIMEOUT_MS = 100;

    public CraftingV2Tests() {
        if (!DimensionManager.isDimensionRegistered(256)) {
            DimensionManager.registerProviderType(256, WorldProviderSurface.class, false);
            DimensionManager.registerDimension(256, 256);
        }
        dummyWorld =
                new WorldServer(
                        MinecraftServer.getServer(),
                        new DummySaveHandler(),
                        "DummyTestWorld",
                        256,
                        new WorldSettings(256, GameType.SURVIVAL, false, false, WorldType.DEFAULT),
                        MinecraftServer.getServer().theProfiler) {
                    @Override
                    public File getChunkSaveLocation() {
                        return new File("dummy-ignoreme");
                    }
                };
    }

    private void simulateJobAndCheck(CraftingJobV2 job, int timeoutMs) {
        job.simulateFor(SIMPLE_SIMULATION_TIMEOUT_MS);

        assertTrue(job.isDone());
        assertFalse(job.isCancelled());
    }

    private void assertJobPlanEquals(CraftingJobV2 job, IAEItemStack... stacks) {
        assertTrue(job.isDone());
        ItemList plan = new ItemList();
        job.populatePlan(plan);
        for (IAEItemStack stack : stacks) {
            IAEItemStack matching = plan.findPrecise(stack);
            assertNotNull(matching, stack::toString);
            assertEquals(stack.getStackSize(), matching.getStackSize(), () -> "Stack size of " + stack);
            assertEquals(
                    stack.getCountRequestable(), matching.getCountRequestable(), () -> "Requestable count of " + stack);
        }
    }

    @Test
    void noPatternSimulation() {
        MockAESystem aeSystem = new MockAESystem(dummyWorld);
        final CraftingJobV2 job = aeSystem.makeCraftingJob(new ItemStack(Items.stick, 13));
        simulateJobAndCheck(job, SIMPLE_SIMULATION_TIMEOUT_MS);
        assertTrue(job.isSimulation());
        assertEquals(job.getOutput(), AEItemStack.create(new ItemStack(Items.stick, 13)));
        assertJobPlanEquals(job, AEItemStack.create(new ItemStack(Items.stick, 13)));
    }
}
