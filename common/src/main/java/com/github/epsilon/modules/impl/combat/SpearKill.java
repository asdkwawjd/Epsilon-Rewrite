package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.player.ChatUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

public class SpearKill extends Module {

    public static final SpearKill INSTANCE = new SpearKill();

    private SpearKill() {
        super("Spear Kill", Category.COMBAT);
    }

    private enum State {
        STARTUP,
        TAKEOFF,
        APPROACH,
        RESET
    }

    private final SettingGroup sgGeneral = settingGroup("General");
    private final SettingGroup sgTarget = settingGroup("Target");

    private final DoubleSetting flySpeed = doubleSetting("Fly Speed", 2.0, 1.0, 5.0, 0.1).group(sgGeneral);
    private final IntSetting fireworkInterval = intSetting("Firework Interval", 1000, 500, 3000, 100).group(sgGeneral);
    private final IntSetting chargeTimeMod = intSetting("Charge Time %", 100, 0, 100, 1).group(sgGeneral);
    private final DoubleSetting targetRange = doubleSetting("Target Range", 64, 8, 256, 1).group(sgGeneral);
    private final DoubleSetting attackRange = doubleSetting("Attack Range", 3.0, 1.0, 8.0, 0.5).group(sgGeneral);

    private final BoolSetting players = boolSetting("Players", true).group(sgTarget);
    private final BoolSetting mobs = boolSetting("Mobs", true).group(sgTarget);
    private final BoolSetting animals = boolSetting("Animals", false).group(sgTarget);
    private final BoolSetting villagers = boolSetting("Villagers", false).group(sgTarget);
    private final BoolSetting ignoreFriends = boolSetting("Ignore Friends", true).group(sgTarget);

    private State state = State.STARTUP;
    private Entity target;
    private final TimerUtils fireworkTimer = new TimerUtils();
    private final TimerUtils stateTimer = new TimerUtils();
    private int spearSlot = -1;
    private boolean spearEquipped;

    @Override
    protected void onEnable() {
        state = State.STARTUP;
        target = null;
        spearEquipped = false;
        spearSlot = -1;
        stateTimer.reset();
        fireworkTimer.reset();
        fireworkTimer.setMs(917813L);
    }

    @Override
    protected void onDisable() {
        mc.options.keyUse.setDown(false);
        if (spearEquipped) {
            InvUtils.swapBack();
            spearEquipped = false;
        }
        reEquipChestplate();
        target = null;
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) return;

        switch (state) {
            case STARTUP -> handleStartup();
            case TAKEOFF -> handleTakeoff();
            case APPROACH -> handleApproach();
            case RESET -> handleReset();
        }
    }

    // ==================== StartUp ====================

    private void handleStartup() {
        boolean wearingElytra = mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);

        if (!wearingElytra) {
            FindItemResult elytra = InvUtils.find(Items.ELYTRA);
            if (!elytra.found()) {
                ChatUtils.addChatMessage("没有找到鞘翅，关闭模块");
                toggle();
                return;
            }
            InvUtils.swap(elytra.slot(), false);
            stateTimer.reset();
        }

        if (!wearingElytra && stateTimer.passedMillise(50)) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }
        if (stateTimer.passedMillise(150)) {
            if (!mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
                ChatUtils.addChatMessage("鞘翅装备失败，关闭模块");
                toggle();
                return;
            }
            if (!InvUtils.find(Items.FIREWORK_ROCKET).found()) {
                ChatUtils.addChatMessage("没有找到烟花火箭，关闭模块");
                toggle();
                return;
            }
            if (!findSpear().found()) {
                ChatUtils.addChatMessage("没有找到长矛，关闭模块");
                toggle();
                return;
            }
            state = State.TAKEOFF;
            stateTimer.reset();
        }
    }

    // ==================== TakeOff ====================

    private void handleTakeoff() {
        if (mc.player.onGround()) {
            mc.player.input.makeJump();
            return;
        }

        if (!mc.player.isFallFlying()) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(
                    mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            mc.player.startFallFlying();
            return;
        }

        target = findTarget();
        if (target == null) {
            if (fireworkTimer.every(fireworkInterval.getValue())) {
                useFirework();
            }
            return;
        }

        if (fireworkTimer.every(fireworkInterval.getValue())) {
            useFirework();
        }

        equipSpear();
        state = State.APPROACH;
        stateTimer.reset();
    }

    // ==================== Approach ====================

    private void handleApproach() {
        if (!mc.player.isFallFlying()) {
            mc.options.keyUse.setDown(false);
            state = State.TAKEOFF;
            return;
        }

        if (target == null || !target.isAlive() || mc.player.distanceTo(target) > targetRange.getValue() * 2) {
            target = findTarget();
        }

        if (target == null) {
            mc.options.keyUse.setDown(false);
            if (fireworkTimer.every(fireworkInterval.getValue())) {
                useFirework();
            }
            return;
        }

        // 每帧保持右键按住，确保长矛持续蓄力
        mc.options.keyUse.setDown(true);

        // 静默旋转（视角不动但服务器端朝目标旋转）
        Managers.ROTATION.setRotations(RotationUtils.calculate(target), 180, Priority.High);

        // 烟花助推
        if (fireworkTimer.every(fireworkInterval.getValue())) {
            useFirework();
        }

        // 水平飞向目标（尽量保持同一高度）
        double targetY = target.getY() + target.getBbHeight() / 2.0;
        Vec3 toTarget = new Vec3(
                target.getX() - mc.player.getX(),
                targetY - mc.player.getY(),
                target.getZ() - mc.player.getZ()
        ).normalize();
        mc.player.setDeltaMovement(toTarget.scale(flySpeed.getValue()));
        mc.player.setSprinting(true);

        // 进入攻击范围 + 蓄力完成 → 释放长矛
        double dist = mc.player.distanceTo(target);
        int usedTicks = mc.player.getTicksUsingItem();
        int readyTicks = getSpearReadyTicks();

        if (dist <= attackRange.getValue() && usedTicks >= readyTicks) {
            mc.gameMode.releaseUsingItem(mc.player);
            mc.options.keyUse.setDown(false);
            InvUtils.swapBack();
            spearEquipped = false;
            state = State.RESET;
            stateTimer.reset();
        }
    }

    // ==================== Reset ====================

    private void handleReset() {
        if (stateTimer.passedMillise(800)) {
            target = findTarget();
            if (target != null) {
                equipSpear();
                state = State.APPROACH;
            } else {
                state = State.TAKEOFF;
            }
            stateTimer.reset();
            fireworkTimer.reset();
            fireworkTimer.setMs(917813L);
        }
    }

    // ==================== Equipment ====================

    private void equipSpear() {
        if (spearEquipped) return;
        FindItemResult spear = findSpear();
        if (!spear.found()) return;
        spearSlot = spear.slot();
        InvUtils.swap(spearSlot, true);
        mc.options.keyUse.setDown(true);
        spearEquipped = true;
    }

    private void useFirework() {
        if (mc.player.getOffhandItem().is(Items.FIREWORK_ROCKET)) {
            mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
            return;
        }
        FindItemResult rocket = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (!rocket.found()) {
            rocket = InvUtils.find(Items.FIREWORK_ROCKET);
            if (!rocket.found()) return;
        }
        InteractionHand hand = rocket.getHand();
        InvUtils.swap(rocket.slot(), true);
        mc.gameMode.useItem(mc.player, hand);
        InvUtils.swapBack();
    }

    private void reEquipChestplate() {
        FindItemResult chestplate = InvUtils.findInHotbar(stack -> {
            if (stack.isEmpty()) return false;
            var equippable = stack.get(DataComponents.EQUIPPABLE);
            return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
        });
        if (chestplate.found()) {
            InvUtils.swap(chestplate.slot(), false);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }
    }

    // ==================== Spear ====================

    private FindItemResult findSpear() {
        return InvUtils.find(this::isSpear);
    }

    private boolean isSpear(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.WOODEN_SPEAR || item == Items.STONE_SPEAR || item == Items.COPPER_SPEAR
                || item == Items.IRON_SPEAR || item == Items.GOLDEN_SPEAR || item == Items.DIAMOND_SPEAR
                || item == Items.NETHERITE_SPEAR;
    }

    private int getSpearReadyTicks() {
        Item item = mc.player.getUseItem().getItem();
        int base;
        if (item == Items.NETHERITE_SPEAR) base = 7;
        else if (item == Items.DIAMOND_SPEAR) base = 9;
        else if (item == Items.IRON_SPEAR) base = 11;
        else if (item == Items.COPPER_SPEAR) base = 12;
        else if (item == Items.STONE_SPEAR || item == Items.GOLDEN_SPEAR) base = 13;
        else base = 14;
        return Math.round(base * (chargeTimeMod.getValue() / 100.0f));
    }

    // ==================== Target ====================

    private Entity findTarget() {
        if (nullCheck()) return null;

        if (mc.hitResult instanceof EntityHitResult hit) {
            if (isValidTarget(hit.getEntity())) return hit.getEntity();
        }

        double range = targetRange.getValue();
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0F);

        HitResult blockHit = mc.level.clip(new ClipContext(eye, eye.add(look.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        double rayLen = blockHit.getType() == HitResult.Type.MISS ? range
                : eye.distanceTo(blockHit.getLocation());

        List<Entity> candidates = mc.level.getEntities(mc.player,
                mc.player.getBoundingBox().expandTowards(look.scale(rayLen)).inflate(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && e != mc.player);
        candidates.sort(Comparator.comparingDouble(e -> eye.distanceToSqr(e.getBoundingBox().getCenter())));

        for (Entity e : candidates) {
            if (eye.distanceTo(e.getBoundingBox().getCenter()) > range) break;
            if (!isValidTarget(e)) continue;
            if (!canSee(e)) continue;
            return e;
        }
        return null;
    }

    private boolean canSee(Entity target) {
        if (nullCheck()) return false;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 tc = target.getBoundingBox().getCenter();
        HitResult result = mc.level.clip(new ClipContext(eye, tc,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return result.getType() == HitResult.Type.MISS
                || eye.distanceTo(result.getLocation()) >= eye.distanceTo(tc) - 0.5;
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null) return false;

        if (entity instanceof Player player && ignoreFriends.getValue()
                && Managers.FRIEND.isFriend(player)) {
            return false;
        }

        if (entity instanceof Player) return players.getValue();
        if (entity instanceof Monster) return mobs.getValue();
        if (entity instanceof Animal) return animals.getValue();
        if (entity instanceof Villager) return villagers.getValue();
        return true;
    }
}
