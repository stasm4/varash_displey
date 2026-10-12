package ua.varash.enhancedcampfire.autotest;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import ua.varash.enhancedcampfire.EnhancedCampfireMod;
import ua.varash.enhancedcampfire.ModRegistry;
import ua.varash.enhancedcampfire.block.EnhancedCampfireBlock;
import ua.varash.enhancedcampfire.block.EnhancedCampfireBlockEntity;
import ua.varash.enhancedcampfire.client.fx.ParticleBudget;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * DEVELOPMENT ONLY (source set "clienttest", run "clientAutotest", never packaged into the mod jar).
 * Drives a real client: creates a flat creative world, builds a scene with the three campfire layouts, checks
 * that models, effect textures, sounds and particles are loaded/alive, takes screenshots at night, by day, in rain
 * and while smouldering, writes autotest-result.txt and quits.
 */
@Mod.EventBusSubscriber(modid = EnhancedCampfireMod.MODID, value = Dist.CLIENT)
public final class CampfireClientAutoTest {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ENABLED = Boolean.getBoolean(EnhancedCampfireMod.MODID + ".autotest");
    private static final List<String> FAILURES = new ArrayList<>();
    private static final List<String> PASSES = new ArrayList<>();

    private static boolean worldRequested;
    private static int ticks = -1;
    private static BlockPos origin = BlockPos.ZERO;

    private CampfireClientAutoTest() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!ENABLED || worldRequested || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        worldRequested = true;
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> {
            LOG.info("[autotest] creating flat test world");
            LevelSettings settings = new LevelSettings("autotest-campfire", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                    new GameRules(), WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("autotest-campfire-" + System.currentTimeMillis(), settings,
                    new WorldOptions(20261012L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                            .createWorldDimensions());
        });
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || mc.level == null || server == null) {
            return;
        }
        ticks++;
        try {
            step(mc, server);
        } catch (Throwable t) {
            LOG.error("[autotest] crashed", t);
            fail("exception: " + t);
            finish(mc);
        }
    }

    private static void step(Minecraft mc, MinecraftServer server) {
        switch (ticks) {
            case 40 -> server.execute(() -> buildScene(server));
            case 60 -> {
                mc.options.hideGui = true;
                view(server, 0.5, 1.0, 6.5, 180.0F, 18.0F);
            }
            case 70 -> checkResources(mc);
            case 200 -> {
                check(ParticleBudget.load() > 0.0F, "campfire particles are alive");
                check(mc.level.getBlockEntity(origin) instanceof EnhancedCampfireBlockEntity, "client block entity present");
                shot(mc, "campfire_night_overview");
            }
            case 205 -> view(server, 0.5, 0.6, 3.2, 180.0F, 30.0F);
            case 280 -> shot(mc, "campfire_night_closeup");
            case 285 -> view(server, -3.5, 0.2, 2.5, 235.0F, 12.0F);
            case 360 -> shot(mc, "campfire_night_side");
            case 365 -> server.execute(() -> {
                server.overworld().setDayTime(6000L);
                view(server, 0.5, 1.0, 6.5, 180.0F, 18.0F);
            });
            case 440 -> shot(mc, "campfire_day");
            case 445 -> server.execute(() -> {
                server.overworld().setDayTime(18000L);
                server.overworld().setWeatherParameters(0, 6000, true, false);
                view(server, 0.5, 0.6, 3.6, 180.0F, 25.0F);
            });
            case 560 -> shot(mc, "campfire_rain_night");
            case 565 -> server.execute(() -> {
                server.overworld().setWeatherParameters(6000, 0, false, false);
                ServerLevel level = server.overworld();
                BlockState lit = level.getBlockState(origin);
                level.setBlock(origin, lit.setValue(CampfireBlock.LIT, Boolean.FALSE), 3);
                view(server, 0.5, 0.4, 2.8, 180.0F, 35.0F);
            });
            case 640 -> {
                BlockState state = mc.level.getBlockState(origin);
                check(!state.getValue(CampfireBlock.LIT) && state.getValue(EnhancedCampfireBlock.SMOULDERING),
                        "extinguished campfire smoulders on the client");
                shot(mc, "campfire_smouldering");
            }
            case 660 -> finish(mc);
            default -> {
            }
        }
    }

    private static void buildScene(MinecraftServer server) {
        ServerLevel level = server.overworld();
        server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        server.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.setDayTime(18000L);
        level.setWeatherParameters(6000, 0, false, false);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
        origin = new BlockPos(0, y, 0);
        BlockState base = ModRegistry.CAMPFIRE.get().defaultBlockState();
        for (int v = 0; v < 3; v++) {
            BlockPos pos = origin.offset((v - 1) * 3, 0, 0);
            level.setBlock(pos, base.setValue(EnhancedCampfireBlock.VARIANT, (v + 1) % 3), 3);
        }
        // middle one: layout 0 with food cooking on it
        level.setBlock(origin, base.setValue(EnhancedCampfireBlock.VARIANT, 0), 3);
        if (level.getBlockEntity(origin) instanceof EnhancedCampfireBlockEntity campfire) {
            campfire.placeFood(null, new ItemStack(Items.BEEF), 6000);
            campfire.placeFood(null, new ItemStack(Items.SALMON), 6000);
        }
        // a few props so the light pool has something to fall on
        level.setBlock(origin.offset(-2, 0, 2), Blocks.OAK_LOG.defaultBlockState(), 3);
        level.setBlock(origin.offset(2, 0, -2), Blocks.SPRUCE_LOG.defaultBlockState(), 3);
        level.setBlock(origin.offset(3, 0, 2), Blocks.HAY_BLOCK.defaultBlockState(), 3);
        LOG.info("[autotest] scene built at {}", origin);
    }

    private static void view(MinecraftServer server, double dx, double dy, double dz, float yaw, float pitch) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.connection.teleport(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz, yaw, pitch);
        });
    }

    private static void checkResources(Minecraft mc) {
        BakedModel missing = mc.getModelManager().getMissingModel();
        for (BlockState state : ModRegistry.CAMPFIRE.get().getStateDefinition().getPossibleStates()) {
            if (mc.getBlockRenderer().getBlockModel(state) == missing) {
                fail("missing block model for " + state);
                return;
            }
        }
        pass("all " + ModRegistry.CAMPFIRE.get().getStateDefinition().getPossibleStates().size() + " block states have models");
        for (String tex : new String[]{"flame_sheet", "flame_sheet_add", "glow_radial"}) {
            ResourceLocation loc = new ResourceLocation(EnhancedCampfireMod.MODID, "textures/effect/" + tex + ".png");
            check(mc.getResourceManager().getResource(loc).isPresent(), "effect texture " + tex);
        }
        for (String sound : new String[]{"crackle", "pop", "hiss", "roar"}) {
            ResourceLocation loc = new ResourceLocation(EnhancedCampfireMod.MODID, "block.enhanced_campfire." + sound);
            check(mc.getSoundManager().getSoundEvent(loc) != null, "sound event " + sound);
        }
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> LOG.info("[autotest] {}", msg.getString()));
        pass("screenshot " + name);
    }

    private static void check(boolean ok, String what) {
        if (ok) {
            pass(what);
        } else {
            fail(what);
        }
    }

    private static void pass(String what) {
        PASSES.add(what);
        LOG.info("[autotest] PASS {}", what);
    }

    private static void fail(String what) {
        FAILURES.add(what);
        LOG.error("[autotest] FAIL {}", what);
    }

    private static void finish(Minecraft mc) {
        StringBuilder sb = new StringBuilder(FAILURES.isEmpty() ? "RESULT: PASS\n" : "RESULT: FAIL\n");
        PASSES.forEach(p -> sb.append("PASS ").append(p).append('\n'));
        FAILURES.forEach(f -> sb.append("FAIL ").append(f).append('\n'));
        try {
            Files.writeString(Path.of(mc.gameDirectory.getAbsolutePath(), "autotest-result.txt"), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("[autotest] could not write result", e);
        }
        LOG.info("[autotest] done:\n{}", sb);
        mc.stop();
    }
}
