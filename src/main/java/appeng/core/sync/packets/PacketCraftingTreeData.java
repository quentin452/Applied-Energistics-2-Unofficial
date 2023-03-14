package appeng.core.sync.packets;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;

import org.apache.commons.io.IOUtils;

import appeng.client.gui.implementations.GuiCraftConfirm;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.features.AEFeature;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.crafting.v2.CraftingJobV2;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;

public class PacketCraftingTreeData extends AppEngPacket {

    // Store data for later deserialization when we get access to the World object
    private ByteBuf receivedData = null;

    @SuppressWarnings("unused")
    public PacketCraftingTreeData(final ByteBuf stream) throws IOException {
        if (FMLCommonHandler.instance().getSide() != Side.CLIENT) {
            throw new UnsupportedOperationException("A client tried to use a client-only packet on the server.");
        }
        receivedData = stream.order(ByteOrder.LITTLE_ENDIAN).slice();
    }

    public PacketCraftingTreeData(final CraftingJobV2 job) {
        final ByteBuf jobData = job.serialize();
        final ByteBuf output = Unpooled.buffer(jobData.readableBytes() + 4);
        output.writeInt(this.getPacketID());
        try (final ByteBufOutputStream bbos = new ByteBufOutputStream(output);
                final GZIPOutputStream gzos = new GZIPOutputStream(bbos);
                final ByteBufInputStream bbis = new ByteBufInputStream(jobData)) {
            IOUtils.copy(bbis, gzos);
            gzos.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (AEConfig.instance.isFeatureEnabled(AEFeature.DebugLogging)) {
            AELog.info(
                    "Crafting tree packet raw size %d, compressed %d",
                    jobData.writerIndex(),
                    output.readableBytes());
        }
        this.configureWrite(output);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        if (receivedData == null) {
            return;
        }
        final ByteBuf decompressedData = Unpooled.buffer().order(ByteOrder.LITTLE_ENDIAN);
        try (final ByteBufOutputStream bbos = new ByteBufOutputStream(decompressedData);
                final ByteBufInputStream bbis = new ByteBufInputStream(receivedData);
                final GZIPInputStream gzis = new GZIPInputStream(bbis)) {
            IOUtils.copy(gzis, bbos);
            bbos.flush();
        } catch (IOException e) {
            AELog.error(e, "Could not decompress the serialized crafting tree.");
            return;
        }
        final CraftingJobV2 deserialized;
        try {
            deserialized = CraftingJobV2.deserialize(player.worldObj, decompressedData);
        } catch (Exception e) {
            AELog.error(e, "Could not deserialize crafting tree sent by the server.");
            return;
        }
        final GuiScreen gs = Minecraft.getMinecraft().currentScreen;
        if (gs instanceof GuiCraftConfirm) {
            ((GuiCraftConfirm) gs).setJobTree(deserialized);
        }
    }
}
