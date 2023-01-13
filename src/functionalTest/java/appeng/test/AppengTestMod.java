package appeng.test;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

// Most of these don't matter as this mod never gets published
@Mod(
        modid = "appeng-tests",
        name = "AE2 Dev Tests",
        version = "1.0",
        dependencies = "required-after:appliedenergistics2")
public class AppengTestMod {
    @EventHandler
    public void onServerStarted(FMLServerStartedEvent startedEv) {
        MinecraftServer.getServer().addChatMessage(new ChatComponentText("Running AE2 unit tests..."));
        runTests();
        MinecraftServer.getServer().addChatMessage(new ChatComponentText("Running AE2 unit tests finished"));
    }

    public void runTests() {
        //
    }
}
