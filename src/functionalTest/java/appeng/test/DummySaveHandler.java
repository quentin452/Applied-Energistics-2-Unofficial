package appeng.test;

import java.io.File;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.storage.IPlayerFileData;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;

public class DummySaveHandler implements ISaveHandler {
    @Override
    public WorldInfo loadWorldInfo() {
        return null;
    }

    @Override
    public void checkSessionLock() throws MinecraftException {}

    @Override
    public IChunkLoader getChunkLoader(WorldProvider worldProvider) {
        return null;
    }

    @Override
    public void saveWorldInfoWithPlayer(WorldInfo worldInfo, NBTTagCompound nbtTagCompound) {}

    @Override
    public void saveWorldInfo(WorldInfo worldInfo) {}

    @Override
    public IPlayerFileData getSaveHandler() {
        return null;
    }

    @Override
    public void flush() {}

    @Override
    public File getWorldDirectory() {
        return null;
    }

    @Override
    public File getMapFileFromName(String s) {
        return null;
    }

    @Override
    public String getWorldDirectoryName() {
        return "dummy";
    }
}
