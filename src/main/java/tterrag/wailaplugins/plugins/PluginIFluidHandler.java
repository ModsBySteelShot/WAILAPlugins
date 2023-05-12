package tterrag.wailaplugins.plugins;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.enderio.core.common.util.BlockCoord;

import mcp.mobius.waila.api.ITaggedList;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaRegistrar;
import tterrag.wailaplugins.api.Plugin;

@Plugin(name = "IFluidHandler")
public class PluginIFluidHandler extends PluginBase {

    @Override
    public void load(IWailaRegistrar registrar) {
        super.load(registrar);

        registerBody(IFluidHandler.class);
        registerNBT(IFluidHandler.class);
    }

    @Override
    protected void getBody(ItemStack stack, List<String> currenttip, IWailaDataAccessor accessor) {
        FluidTankInfo[] tankInfos = FluidHandlerHelper.getFluidInfos(accessor.getNBTData());

        FluidHandlerHelper.addFluidInfos((ITaggedList<String, String>) currenttip, tankInfos);
    }

    @Override
    protected void getNBTData(TileEntity te, NBTTagCompound tag, World world, BlockCoord pos) {
        FluidHandlerHelper.setFluidInfos(tag, (IFluidHandler) te);

        te.writeToNBT(tag);
    }
}

final class FluidHandlerHelper {

    public static void setFluidInfos(NBTTagCompound tag, IFluidHandler te) {
        FluidTankInfo[] tankInfos = te.getTankInfo(ForgeDirection.UNKNOWN);

        setFluidInfos(tag, tankInfos);
    }

    public static void setFluidInfos(NBTTagCompound tag, FluidTankInfo... tankInfos) {
        if (tankInfos != null && tankInfos.length > 0) {
            NBTTagList fluidInfoTags = new NBTTagList();

            for (FluidTankInfo tankInfo : tankInfos) {
                NBTTagCompound tankInfoTag = new NBTTagCompound();

                setFluidInfo(tankInfoTag, tankInfo);

                fluidInfoTags.appendTag(tankInfoTag);
            }

            if (fluidInfoTags.tagCount() > 0) {
                tag.setTag("fluidInfos", fluidInfoTags);
            }
        }
    }

    public static void setFluidInfo(NBTTagCompound tag, FluidTankInfo tankInfo) {
        if (tankInfo != null) {
            if (tankInfo.fluid != null) {
                NBTTagCompound fluidTag = new NBTTagCompound();

                tankInfo.fluid.writeToNBT(fluidTag);

                tag.setTag("fluidStack", fluidTag);
            }

            tag.setInteger("tankCapacity", tankInfo.capacity);
        }
    }

    public static FluidTankInfo[] getFluidInfos(NBTTagCompound tag) {
        NBTTagList tagList = tag.getTagList("fluidInfos", NBT.TAG_COMPOUND);

        return IntStream.range(0, tagList.tagCount()).mapToObj(i -> getFluidInfo(tagList.getCompoundTagAt(i)))
                .filter(Objects::nonNull).toArray(FluidTankInfo[]::new);
    }

    public static FluidTankInfo getFluidInfo(NBTTagCompound tag) {
        if (tag.hasKey("fluidStack") && tag.hasKey("tankCapacity")) {
            FluidStack fluidStack = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("fluidStack"));

            return new FluidTankInfo(fluidStack, tag.getInteger("tankCapacity"));
        } else if (tag.hasKey("tankCapacity")) {
            return new FluidTankInfo(null, tag.getInteger("tankCapacity"));
        }

        return null;
    }

    public static void addFluidInfos(ITaggedList<String, String> currenttip, FluidTankInfo... tankInfos) {
        if (tankInfos != null) {
            for (FluidTankInfo tankInfo : tankInfos) {
                addFluidInfo(currenttip, tankInfo);
            }
        }
    }

    public static void addFluidInfo(ITaggedList<String, String> currenttip, FluidTankInfo tankInfo) {
        if (tankInfo != null) {
            if (tankInfo.fluid != null) {
                currenttip.add(
                        String.format(
                                "%d / %d mB %s",
                                tankInfo.fluid.amount,
                                tankInfo.capacity,
                                tankInfo.fluid.getLocalizedName()),
                        "IFluidHandler");
            } else {
                currenttip.add(String.format("0 / %d mB", tankInfo.capacity), "IFluidHandler");
            }
        }
    }
}
