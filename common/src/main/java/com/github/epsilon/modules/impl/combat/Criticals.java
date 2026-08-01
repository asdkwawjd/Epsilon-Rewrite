package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.AttackEntityEvent;
import com.github.epsilon.events.impl.JumpEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.network.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class Criticals extends Module {

    public static final Criticals INSTANCE = new Criticals();

    private Criticals() {
        super("Criticals", Category.COMBAT);
    }

    private enum Mode {
        PURE,
        LEGIT,
        VULCAN_297
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.PURE);

    // VULCAN_297 状态
    private static final int JUMP_RECOVERY_TICKS = 8;
    private static final int LANDING_RECOVERY_TICKS = 2;
    private int jumpRecoveryTicks;
    private int landingRecoveryTicks;
    private boolean wasAirborne;

    @Override
    protected void onEnable() {
        resetVulcan297State();
    }

    @Override
    protected void onDisable() {
        resetVulcan297State();
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Post event) {
        if (!mode.is(Mode.VULCAN_297)) return;
        updateVulcan297State();
    }

    @EventHandler
    private void onJump(JumpEvent event) {
        if (!mode.is(Mode.VULCAN_297)) return;
        jumpRecoveryTicks = JUMP_RECOVERY_TICKS;
        landingRecoveryTicks = LANDING_RECOVERY_TICKS;
        wasAirborne = true;
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (nullCheck()) return;

        if (mode.is(Mode.VULCAN_297)) {
            if (!canPerformVulcan297Critical()) return;
            performVulcan297Critical();
            return;
        }

        if (mode.is(Mode.LEGIT)) return;

        // Pure 模式：经典发包暴击
        PacketUtils.sendSilently(new ServerboundMovePlayerPacket.Pos(
                mc.player.getX(), mc.player.getY() + 0.0625, mc.player.getZ(), false, mc.player.horizontalCollision));
        PacketUtils.sendSilently(new ServerboundMovePlayerPacket.Pos(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), false, mc.player.horizontalCollision));
    }

    // ==================== VULCAN_297 ====================

    private void updateVulcan297State() {
        if (nullCheck()) {
            resetVulcan297State();
            return;
        }

        if (jumpRecoveryTicks > 0) {
            jumpRecoveryTicks--;
        }

        if (!mc.player.onGround()) {
            wasAirborne = true;
            landingRecoveryTicks = LANDING_RECOVERY_TICKS;
            return;
        }

        if (wasAirborne && landingRecoveryTicks > 0) {
            landingRecoveryTicks--;
            return;
        }

        wasAirborne = false;
        landingRecoveryTicks = 0;
    }

    private boolean canPerformVulcan297Critical() {
        if (!mc.player.onGround()) return false;
        if (jumpRecoveryTicks > 0) return false;
        return landingRecoveryTicks <= 0;
    }

    private void performVulcan297Critical() {
        sendPositionPacket(0.021, false);
        sendPositionPacket(0.011, false);
    }

    private void sendPositionPacket(double yOffset, boolean onGround) {
        PacketUtils.sendSilently(new ServerboundMovePlayerPacket.PosRot(
                mc.player.getX(),
                mc.player.getY() + yOffset,
                mc.player.getZ(),
                mc.player.getYRot(),
                mc.player.getXRot(),
                onGround,
                mc.player.horizontalCollision
        ));
    }

    private void resetVulcan297State() {
        jumpRecoveryTicks = 0;
        landingRecoveryTicks = 0;
        wasAirborne = false;
    }
}
