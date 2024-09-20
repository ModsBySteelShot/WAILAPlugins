package tterrag.wailaplugins.plugins;

import java.lang.reflect.Field;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

import org.apache.commons.lang3.ArrayUtils;

import com.enderio.core.common.Lang;
import com.enderio.core.common.util.BlockCoord;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.Loader;
import forestry.api.apiculture.BeeManager;
import forestry.api.apiculture.EnumBeeChromosome;
import forestry.api.apiculture.IBee;
import forestry.api.apiculture.IBeeGenome;
import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.IBeeHousingInventory;
import forestry.api.apiculture.IBeekeepingLogic;
import forestry.api.arboriculture.EnumTreeChromosome;
import forestry.api.arboriculture.ITree;
import forestry.api.arboriculture.ITreeGenome;
import forestry.api.core.ForestryAPI;
import forestry.api.core.IErrorLogic;
import forestry.api.core.IErrorState;
import forestry.api.genetics.IGenome;
import forestry.apiculture.BeekeepingLogic;
import forestry.apiculture.genetics.Bee;
import forestry.apiculture.multiblock.TileAlveary;
import forestry.arboriculture.genetics.Tree;
import forestry.arboriculture.tiles.TileLeaves;
import forestry.arboriculture.tiles.TileTreeContainer;
import forestry.core.access.IOwnable;
import forestry.core.proxy.Proxies;
import forestry.core.tiles.TileEngine;
import forestry.core.tiles.TileForestry;
import forestry.core.utils.StringUtil;
import forestry.plugins.PluginApiculture;
import lombok.SneakyThrows;
import magicbees.tileentity.TileEntityMagicApiary;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaRegistrar;
import mcp.mobius.waila.api.SpecialChars;
import tterrag.wailaplugins.api.Plugin;

@Plugin(name = "Forestry", deps = { "Forestry" })
public class PluginForestry extends PluginBase {

    private static Field _throttle;
    private static Field _maxHeat;
    private static NumberFormat pctFmt = NumberFormat.getPercentInstance();
    private static Lang forLang = new Lang("for");

    @SneakyThrows
    public PluginForestry() {
        try {
            _throttle = BeekeepingLogic.class.getDeclaredField("throttle");
        } catch (NoSuchFieldException e) {
            // forestry update
            _throttle = BeekeepingLogic.class.getDeclaredField("queenWorkCycleThrottle");
        }

        _throttle.setAccessible(true);

        _maxHeat = TileEngine.class.getDeclaredField("maxHeat");
        _maxHeat.setAccessible(true);

        pctFmt.setMinimumFractionDigits(2);
    }

    @Override
    public void load(IWailaRegistrar registrar) {
        super.load(registrar);

        registerBody(TileForestry.class, TileTreeContainer.class, TileAlveary.class);

        registerNBT(TileForestry.class, TileTreeContainer.class, TileAlveary.class);

        if (Loader.isModLoaded("MagicBees")) {
            registerBody(TileEntityMagicApiary.class);
            registerNBT(TileEntityMagicApiary.class);
        }

        addConfig("power");
        addConfig("heat");
        addConfig("sapling");
        addConfig("leaves");
        addConfig("apiary");
    }

    @Override
    @SneakyThrows
    @SuppressWarnings("unused")
    protected void getBody(ItemStack stack, List<String> currenttip, IWailaDataAccessor accessor) {
        TileEntity tile = accessor.getTileEntity();
        EntityPlayer player = accessor.getPlayer();
        MovingObjectPosition pos = accessor.getPosition();
        NBTTagCompound tag = accessor.getNBTData();
        int x = pos.blockX, y = pos.blockY, z = pos.blockZ;

        if (tag.hasKey(ENERGY_STORED) && getConfig("power")) {
            currenttip.add(tag.getInteger(ENERGY_STORED) + " / " + tag.getInteger(MAX_ENERGY_STORED) + " RF");
        }

        if (tag.hasKey(HEAT) && getConfig("heat")) {
            currenttip.add(lang.localize("engineHeat", tag.getInteger(HEAT) / 10D + "\u00B0C"));
        }

        if (tag.hasKey(TREE) && getConfig("sapling")) {
            ITree tree = new Tree(tag.getCompoundTag(TREE));
            addGenomeTooltip(tag, (TileTreeContainer) tile, tree, player, currenttip);
        }

        if (tile instanceof TileLeaves && getConfig("leaves")) {
            if (((TileLeaves) tile).isPollinated()) {
                currenttip.add(lang.localize("pollinated", tag.getString(LEAF_BRED_SPECIES)));
            }
        }

        if (tile instanceof IBeeHousing && getConfig("apiary")) {
            ItemStack queenstack = null;
            ItemStack dronestack = null;
            if (tag.hasKey(QUEEN_STACK)) {
                queenstack = ItemStack.loadItemStackFromNBT(tag.getCompoundTag(QUEEN_STACK));
            }
            if (tag.hasKey(DRONE_STACK)) {
                dronestack = ItemStack.loadItemStackFromNBT(tag.getCompoundTag(DRONE_STACK));
            }

            IBee queen = null;

            if (queenstack != null) {
                queen = new Bee(queenstack.getTagCompound());
                String queenSpecies = getSpeciesName(queen.getGenome(), true);

                currenttip.add(
                        EnumChatFormatting.WHITE + lang.localize(
                                "mainbee",
                                getNameForBeeType(queenstack),
                                EnumChatFormatting.GREEN + queenSpecies));
                if (queen.isAnalyzed()) {
                    addIndentedBeeInfo(queen, currenttip);
                }
            }

            IBee drone = null;

            if (queen != null && queen.getMate() != null) {
                drone = new Bee(queen.getMate());
            } else if (dronestack != null) {
                drone = new Bee(dronestack.getTagCompound());
            }

            if (drone != null) {
                currenttip.add(
                        String.format(
                                EnumChatFormatting.WHITE + lang.localize("secondarybee"),
                                lang.localize("drone"),
                                EnumChatFormatting.GREEN + getSpeciesName(drone.getGenome(), true)));

                if (drone.isAnalyzed()) {
                    addIndentedBeeInfo(drone, currenttip);
                }
            }

            if (tag.hasKey(PROD_MOD) && queen != null) {
                addBeeYieldInfo(queen.getGenome(), tag.getFloat(PROD_MOD), currenttip);
            }

            if (tag.hasKey(ERRORS) || tag.hasKey(BREED_PROGRESS)) {
                int[] ids = tag.getIntArray(ERRORS);
                Set<IErrorState> errs = Sets.newHashSet();
                for (int i : ids) {
                    errs.add(ForestryAPI.errorStateRegistry.getErrorState((short) i));
                }

                if (!errs.isEmpty()) {
                    for (IErrorState err : errs) {
                        currenttip.add(
                                EnumChatFormatting.WHITE + String.format(
                                        lang.localize("breedError"),
                                        EnumChatFormatting.RED + forLang.localize(err.getDescription())));
                    }
                } else {
                    currenttip.add(
                            EnumChatFormatting.WHITE + String.format(
                                    lang.localize("breedProgress"),
                                    EnumChatFormatting.AQUA + pctFmt.format(tag.getDouble(BREED_PROGRESS))));
                }
            }

            if (tag.hasKey(IS_JUBILANT)) {
                if (tag.getBoolean(IS_JUBILANT)) {
                    currenttip.add(EnumChatFormatting.AQUA + lang.localize("isJubilant"));
                } else {
                    currenttip.add(EnumChatFormatting.RED + lang.localize("isNotJubilant"));
                }
            }
        }
    }

    private void addGenomeTooltip(NBTTagCompound tag, TileTreeContainer te, ITree tree, EntityPlayer player,
            List<String> currenttip) {
        UUID owner = UUID.fromString(tag.getString(OWNER));
        // XXX Leaf isAnalyzed is not working properly, wait for forestry fix
        if (owner.equals(player.getGameProfile().getId()) && (tree.isAnalyzed() || te instanceof TileLeaves)) {
            addTreeTooltip(tree, currenttip);
        } else if (tree != null) {
            currenttip.add(
                    EnumChatFormatting.ITALIC
                            + (tree.isAnalyzed() ? lang.localize("notOwner") : lang.localize("notAnalyzed")));
        }
    }

    private void addTreeTooltip(ITree tree, List<String> currenttip) {
        if (Proxies.common.isShiftDown()) tree.addTooltip(currenttip);
        else currenttip.add(getTMI());
    }

    private String getTMI() {
        return EnumChatFormatting.ITALIC + "<" + StringUtil.localize("gui.tooltip.tmi") + ">";
    }

    private String getSpeciesName(IGenome genome, boolean active) {
        return active ? genome.getActiveAllele(EnumBeeChromosome.SPECIES).getName()
                : genome.getInactiveAllele(EnumBeeChromosome.SPECIES).getName();
    }

    private String getNameForBeeType(ItemStack bee) {
        return PluginApiculture.items.beeDroneGE == bee.getItem() ? lang.localize("drone")
                : PluginApiculture.items.beePrincessGE == bee.getItem() ? lang.localize("princess")
                        : lang.localize("queen");
    }

    private void addIndentedBeeInfo(IBee bee, List<String> currenttip) {
        if (Proxies.common.isShiftDown()) {
            List<String> tt = new ArrayList<String>();
            bee.addTooltip(tt);
            for (int i = 0; i < tt.size(); i++) {
                tt.set(i, (i == 0 ? ">" : "") + SpecialChars.TAB + tt.get(i));
            }
            currenttip.addAll(tt);
        } else {
            currenttip.add(getTMI());
        }
    }

    private void extendBeeYieldMap(Map<ItemStack, Float> yields, Map<ItemStack, Float> products, boolean isSecondary,
            float speed, float prodMod, float t) {
        for (Map.Entry<ItemStack, Float> product : products.entrySet()) {
            float chance = product.getValue() / (isSecondary ? 2f : 1f);
            float term = Math.min(Bee.getFinalChance(chance, speed, prodMod, t), 1f);
            if (yields.computeIfPresent(product.getKey(), (k, v) -> v + term) == null) {
                yields.put(product.getKey(), term);
            }
        }
    }

    private void addBeeYieldInfo(IBeeGenome genome, float prodMod, List<String> currenttip) {
        EnumChatFormatting color = prodMod > 16f ? EnumChatFormatting.RED : EnumChatFormatting.AQUA;
        float speed = genome.getSpeed();
        float dummyProd = 100f * Math.min(Bee.getFinalChance(0.01f, speed, prodMod, 1f), 1f);
        currenttip.add(
                EnumChatFormatting.WHITE
                        + lang.localize("effectiveProductionMul", color + String.format("b^0.52 * %.3f", dummyProd)));

        if (!Proxies.common.isShiftDown()) {
            currenttip.add(EnumChatFormatting.ITALIC + "<" + lang.localize("revealYield") + ">");
        } else {
            // create a summary of the average yields for all outputs with different average yields
            Map<ItemStack, Float> yields = new HashMap<>();
            extendBeeYieldMap(yields, genome.getPrimary().getProductChances(), false, speed, prodMod, 1f);
            extendBeeYieldMap(yields, genome.getSecondary().getProductChances(), true, speed, prodMod, 1f);
            extendBeeYieldMap(yields, genome.getPrimary().getSpecialtyChances(), false, speed, prodMod, 1f);
            TreeMap<Float, ItemStack[]> productsByYield = new TreeMap<>();
            for (Map.Entry<ItemStack, Float> product : yields.entrySet()) {
                float chance = product.getValue();
                ItemStack item = product.getKey();
                if (productsByYield.computeIfPresent(
                        chance,
                        (_chance, items) -> items.length > 1 ? items : new ItemStack[] { items[0], item }) == null) {
                    productsByYield.put(chance, new ItemStack[] { item });
                }
            }

            for (Map.Entry<Float, ItemStack[]> ent : productsByYield.descendingMap().entrySet()) {
                String name = ent.getValue()[0].getDisplayName();
                boolean isDup = ent.getValue().length > 1;
                String namePart = name + (isDup ? ", ... : " : ": ");
                // items / bee tick * 60 seconds / minute * 60 minutes / hour / (27.5 seconds / bee tick) = items / hour
                String yieldPart = String.format("%.3f ", ent.getKey() * 60 * 60 / 27.5)
                        + lang.localize("yieldPerHour");
                currenttip.add(SpecialChars.TAB + namePart + yieldPart);
            }
        }
    }

    public static final String LEAF_BRED_SPECIES = "leafBredSpecies";
    public static final String QUEEN_STACK = "queenStack";
    public static final String DRONE_STACK = "droneStack";
    public static final String PROD_MOD = "dummyProduction";
    public static final String ERRORS = "errors";
    public static final String IS_JUBILANT = "isJubilant";
    public static final String BREED_PROGRESS = "breedProgress";
    public static final String TREE = "treeData";
    public static final String ENERGY_STORED = "rfStored";
    public static final String MAX_ENERGY_STORED = "maxRfStored";
    public static final String HEAT = "engineHeat";
    public static final String OWNER = "tileOwner";

    @Override
    @SneakyThrows
    protected void getNBTData(TileEntity te, NBTTagCompound tag, World world, BlockCoord pos) {
        if (te instanceof TileLeaves) {
            ITreeGenome mate = ((TileLeaves) te).getTree().getMate();
            if (mate != null) {
                tag.setString(LEAF_BRED_SPECIES, mate.getActiveAllele(EnumTreeChromosome.SPECIES).getName());
            }
        }
        if (te instanceof IBeeHousing) {
            IBeeHousing housing = (IBeeHousing) te;
            IBeekeepingLogic logic = housing.getBeekeepingLogic();
            IBeeHousingInventory inv = housing.getBeeInventory();
            IErrorLogic errs = housing.getErrorLogic();

            if (logic != null) {
                ItemStack queen = inv.getQueen();
                ItemStack drone = inv.getDrone();
                if (queen != null) {
                    NBTTagCompound queenTag = new NBTTagCompound();
                    queen.writeToNBT(queenTag);
                    tag.setTag(QUEEN_STACK, queenTag);
                }
                if (drone != null) {
                    NBTTagCompound droneTag = new NBTTagCompound();
                    drone.writeToNBT(droneTag);
                    tag.setTag(DRONE_STACK, droneTag);
                }
                Set<IErrorState> errors = errs.getErrorStates();
                List<Integer> ids = Lists.newArrayList();
                for (IErrorState error : errors) {
                    ids.add((int) error.getID());
                }
                tag.setIntArray(ERRORS, ArrayUtils.toPrimitive(ids.toArray(new Integer[0])));

                if (queen != null && PluginApiculture.items.beeQueenGE == queen.getItem()) {
                    Bee queenBee = new Bee(queen.getTagCompound());
                    float throttle = _throttle.getInt(logic);
                    float maxAge = queenBee.getMaxHealth();
                    float age = Math.abs(queenBee.getHealth() - maxAge); // inverts the progress

                    // determines the amount of percentage points between each breed tick
                    float step = (1 / maxAge);

                    // interpolates between 0 and step
                    float progress = step * (throttle / PluginApiculture.ticksPerBeeWorkCycle);

                    tag.setDouble(BREED_PROGRESS, (age / maxAge) + progress);

                    IBeeGenome genome = queenBee.getGenome();
                    float prodMod = BeeManager.beeRoot.createBeeHousingModifier(housing)
                            .getProductionModifier(genome, 0f);
                    prodMod += BeeManager.beeRoot.getBeekeepingMode(housing.getWorld()).getBeeModifier()
                            .getProductionModifier(genome, prodMod);

                    tag.setFloat(PROD_MOD, prodMod);

                    tag.setBoolean(
                            IS_JUBILANT,
                            genome.getPrimary().isJubilant(genome, housing)
                                    && genome.getSecondary().isJubilant(genome, housing));
                }
            }
        }
        if (te instanceof TileTreeContainer) {
            ITree tree = ((TileTreeContainer) te).getTree();
            NBTTagCompound treeTag = new NBTTagCompound();
            tree.writeToNBT(treeTag);
            tag.setTag(TREE, treeTag);
        }
        if (te instanceof TileEngine) {
            tag.setInteger(ENERGY_STORED, ((TileEngine) te).getEnergyManager().getTotalEnergyStored());
            tag.setInteger(MAX_ENERGY_STORED, ((TileEngine) te).getEnergyManager().getMaxEnergyStored());
            tag.setInteger(HEAT, ((TileEngine) te).getHeat());
        }
        if (te instanceof IOwnable) {
            GameProfile owner = ((IOwnable) te).getOwner();
            tag.setString(OWNER, owner.getId().toString());
        }
    }
}
