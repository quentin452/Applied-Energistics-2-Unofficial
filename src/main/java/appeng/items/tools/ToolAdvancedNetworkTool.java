package appeng.items.tools;

import java.util.EnumSet;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

import com.google.common.base.Optional;

import appeng.api.implementations.guiobjects.IGuiItem;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.implementations.items.IAEWrench;
import appeng.api.networking.IGridHost;
import appeng.client.ClientHelper;
import appeng.core.features.AEFeature;
import appeng.integration.IntegrationType;
import appeng.items.AEBaseItem;
import appeng.items.contents.NetworkToolViewer;
import appeng.transformer.annotations.Integration.Interface;
import appeng.transformer.annotations.Integration.InterfaceList;
import appeng.util.Platform;
import buildcraft.api.tools.IToolWrench;
import cofh.api.item.IToolHammer;

@InterfaceList(
        value = { @Interface(iface = "cofh.api.item.IToolHammer", iname = IntegrationType.CoFHWrench),
                @Interface(iface = "buildcraft.api.tools.IToolWrench", iname = IntegrationType.BuildCraftCore) })
public class ToolAdvancedNetworkTool extends AEBaseItem implements IGuiItem, IAEWrench, IToolWrench, IToolHammer {

    public ToolAdvancedNetworkTool() {
        super(Optional.absent());

        this.setFeature(EnumSet.of(AEFeature.AdvancedNetworkTool));
        this.setMaxStackSize(1);
        this.setHarvestLevel("wrench", 0);
    }

    @Override
    public ItemStack onItemRightClick(final ItemStack it, final World w, final EntityPlayer p) {
        if (Platform.isClient()) {
            final MovingObjectPosition mop = ClientHelper.proxy.getMOP();

            if (mop == null) {
                this.onItemUseFirst(it, p, w, 0, 0, 0, -1, 0, 0, 0);
            } else {
                final int i = mop.blockX;
                final int j = mop.blockY;
                final int k = mop.blockZ;

                if (w.getBlock(i, j, k).isAir(w, i, j, k)) {
                    this.onItemUseFirst(it, p, w, 0, 0, 0, -1, 0, 0, 0);
                }
            }
        }

        return it;
    }

    @Override
    public boolean onItemUseFirst(final ItemStack is, final EntityPlayer player, final World world, final int x,
            final int y, final int z, final int side, final float hitX, final float hitY, final float hitZ) {
        // TODO

        return true;
    }

    @Override
    public boolean doesSneakBypassUse(final World world, final int x, final int y, final int z,
            final EntityPlayer player) {
        return true;
    }

    @Override
    public boolean canWrench(final ItemStack is, final EntityPlayer player, final int x, final int y, final int z) {
        return true;
    }

    @Override
    public boolean canWrench(final EntityPlayer player, final int x, final int y, final int z) {
        return true;
    }

    @Override
    public void wrenchUsed(final EntityPlayer player, final int x, final int y, final int z) {
        player.swingItem();
    }

    @Override
    public boolean isUsable(ItemStack itemStack, EntityLivingBase entityLivingBase, int x, int y, int z) {
        return true;
    }

    @Override
    public void toolUsed(ItemStack itemStack, EntityLivingBase entity, int x, int y, int z) {
        entity.swingItem();
    }

    @Override
    public IGuiItemObject getGuiObject(final ItemStack is, final World world, final int x, final int y, final int z) {
        final TileEntity te = world.getTileEntity(x, y, z);
        return new NetworkToolViewer(is, (IGridHost) (te instanceof IGridHost ? te : null));
    }

}
