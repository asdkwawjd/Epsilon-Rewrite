package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.TickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.utils.network.PacketUtils;
import com.github.epsilon.utils.player.ChatUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.util.Mth;

import java.util.Random;

public class Disabler extends Module {

    public static final Disabler INSTANCE = new Disabler();

    private Disabler() {
        super("Disabler", Category.PLAYER);
    }

    private final SettingGroup sgGrimAC = settingGroup("Grim AC");
    private final SettingGroup sgACA = settingGroup("Anti Cheat Addition");
    private final SettingGroup sgThemis = settingGroup("Themis");

    private final BoolSetting logging = boolSetting("Logging", false);
    private final BoolSetting onlyRemoteServer = boolSetting("Only Remote Server", false);

    // Grim Anti Cheat
    private final BoolSetting badPacketsA = boolSetting("Bad Packets A", true).group(sgGrimAC);
    private final BoolSetting aimModulo360 = boolSetting("Aim Modulo 360", true).group(sgGrimAC);
    private final BoolSetting duplicateRotPlace = boolSetting("Duplicate Rot Place", true).group(sgGrimAC);

    // Anti Cheat Addition
    private final BoolSetting acaFastSwitch = boolSetting("Fast Switch", true).group(sgACA);
    private final BoolSetting acaInventoryFrequency = boolSetting("Inventory Frequency", false).group(sgACA);
    private final BoolSetting acaAimStep = boolSetting("Aim Step", true).group(sgACA);
    private final BoolSetting acaPerfectRotation = boolSetting("Perfect Rotation", true).group(sgACA);

    // Themis
    private final BoolSetting themisBlink = boolSetting("Blink", true).group(sgThemis);

    private int lastSlot = -1;

    private long themisBlinkLastSend = System.currentTimeMillis();
    private int themisBlinkCount = 0;

    private float lastYaw = 0.0f;
    private float lastPitch = 0.0f;
    private float currentYaw = 0.0f;
    private float currentPitch = 0.0f;
    private float yawDiff = 0.0f;
    private float pitchDiff = 0.0f;
    private float lastPlacedYawDiff = 0.0f;
    private float lastPlacedPitchDiff = 0.0f;
    private boolean rotated = false;

    private boolean inventoryOpen = false;
    private long inventoryOpenTime = 0L;
    private ServerboundContainerClosePacket storedClosePacket = null;
    private long inventoryCloseDelay = 0L;
    private TimerUtils inventoryTimer = new TimerUtils();

    private final Random random = new Random();

    private static final double[] perfectRotSteps = new double[]{0.0, 5.625, 11.25, 16.875, 22.5, 28.125, 33.75, 39.375, 45.0, 50.625, 56.25, 61.875, 67.5, 73.125, 78.75, 84.375, 90.0};

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        resetState();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (shouldSkip()) {
            resetState();
            return;
        }

        if (this.storedClosePacket != null && this.inventoryTimer.passedMillise(this.inventoryCloseDelay)) {
            PacketUtils.sendSilently(this.storedClosePacket);
            this.log("InventoryFrequency: Released stored close packet");
            this.storedClosePacket = null;
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (shouldSkip()) {
            resetState();
            return;
        }

        if (event.getPacket() instanceof ServerboundSetCarriedItemPacket packet) {
            int slot = packet.getSlot();
            if (badPacketsA.getValue() && slot == lastSlot && slot != -1) {
                event.setCancelled(true);
                log("BadPacketsA: Cancelled duplicate slot packet: " + slot);
                return;
            }
            if (this.acaFastSwitch.getValue() && lastSlot != -1 && slot != lastSlot) {
                sendIntermediateSlots(lastSlot, slot);
            }
            log("Processed slot switch: " + lastSlot + " -> " + slot);
            lastSlot = slot;
        }

        if (acaInventoryFrequency.getValue() && event.getPacket() instanceof ServerboundContainerClosePacket closePacket) {
            if (this.inventoryOpen) {
                long now = System.currentTimeMillis();
                long openDuration = now - this.inventoryOpenTime;
                if (openDuration <= 150L) {
                    event.setCancelled(true);
                    this.storedClosePacket = closePacket;
                    this.inventoryCloseDelay = 151L - openDuration;
                    this.inventoryTimer.reset();
                    this.log("InventoryFrequency: Storing close packet, will send after " + this.inventoryCloseDelay + "ms");
                    this.inventoryOpen = false;
                    return;
                }
                this.inventoryOpen = false;
                this.log("InventoryFrequency: Allowed close packet after " + openDuration + "ms");
            }
        }

        if (this.themisBlink.getValue()) {
            if (System.currentTimeMillis() - this.themisBlinkLastSend > 200L) {
                if (this.themisBlinkCount == 0) {
                    PacketUtils.sendSilently(new ServerboundPongPacket(0));
                }
                this.themisBlinkLastSend = System.currentTimeMillis();
                this.themisBlinkCount = 0;
            }
            if (event.getPacket() instanceof ServerboundMovePlayerPacket.StatusOnly || event.getPacket() instanceof ServerboundPongPacket) {
                ++this.themisBlinkCount;
            }
        }

        if (aimModulo360.getValue()) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacket packet && packet.hasRotation()) {
                float yaw = packet.yRot;
                if (yaw < 360.0f && yaw > -360.0f) {
                    packet.yRot = yaw + 720.0f;
                    log("Disabled AimModulo360");
                }
            }
        }

        if (this.duplicateRotPlace.getValue()) {
            if (event.getPacket() instanceof ServerboundMovePlayerPacket movePacket) {
                if (movePacket.hasRotation()) {
                    float prevYaw = this.currentYaw;
                    float prevPitch = this.currentPitch;
                    this.currentYaw = movePacket.yRot;
                    this.currentPitch = movePacket.xRot;
                    this.yawDiff = Math.abs(this.currentYaw - prevYaw);
                    this.pitchDiff = Math.abs(this.currentPitch - prevPitch);
                    this.rotated = true;
                    float yawDelta;
                    if (this.yawDiff > 2.0f && (double) (yawDelta = Math.abs(this.yawDiff - this.lastPlacedYawDiff)) < 1.0E-4) {
                        float jitter = 0.001f + this.random.nextFloat() * 0.009f;
                        float newYaw = this.currentYaw - jitter;
                        movePacket.yRot = newYaw;
                        this.log("DuplicateRotPlace: Modified yaw from " + this.currentYaw + " to " + newYaw + " (yawDiff: " + yawDelta + ")");
                    }
                    float pitchDelta;
                    if (this.pitchDiff > 2.0f && (double) (pitchDelta = Math.abs(this.pitchDiff - this.lastPlacedPitchDiff)) < 1.0E-4) {
                        float jitter = 0.001f + this.random.nextFloat() * 0.009f;
                        float newPitch = Mth.clamp(this.currentPitch - jitter, -90.0f, 90.0f);
                        movePacket.xRot = newPitch;
                        this.log("DuplicateRotPlace: Modified pitch from " + this.currentPitch + " to " + newPitch + " (pitchDiff: " + pitchDelta + ")");
                    }
                }
            } else if (event.getPacket() instanceof ServerboundUseItemOnPacket && this.rotated) {
                this.lastPlacedYawDiff = this.yawDiff;
                this.lastPlacedPitchDiff = this.pitchDiff;
                this.rotated = false;
            }
        }

        if ((this.acaAimStep.getValue() || this.acaPerfectRotation.getValue()) && event.getPacket() instanceof ServerboundMovePlayerPacket movePacket2) {
            float[] fArray;
            float yawAim = movePacket2.yRot;
            float pitchAim = movePacket2.xRot;
            boolean modified = false;
            if (this.acaAimStep.getValue() && this.isAimStepRotation(yawAim, pitchAim)) {
                float[] fArray2 = this.applyAimStep(yawAim, pitchAim);
                yawAim = fArray2[0];
                pitchAim = fArray2[1];
                modified = true;
            }
            if (this.acaPerfectRotation.getValue() && ((fArray = this.applyPerfectRotation(yawAim, pitchAim))[0] != yawAim || fArray[1] != pitchAim)) {
                yawAim = fArray[0];
                pitchAim = fArray[1];
                modified = true;
                this.log("PerfectRotation: Modified rotation");
            }
            if (modified) {
                movePacket2.yRot = yawAim;
                movePacket2.xRot = Mth.clamp(pitchAim, -90.0f, 90.0f);
            }
            this.lastYaw = movePacket2.yRot;
            this.lastPitch = movePacket2.xRot;
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundLoginPacket) {
            resetState();
            return;
        }

        if (shouldSkip()) {
            resetState();
            return;
        }

        if (event.getPacket() instanceof ClientboundOpenScreenPacket) {
            this.inventoryOpenTime = System.currentTimeMillis();
            this.inventoryOpen = true;
            this.log("Inventory opened at: " + this.inventoryOpenTime);
        }
    }

    private void sendIntermediateSlots(int fromSlot, int toSlot) {
        int distance = Math.abs(fromSlot - toSlot);
        if (distance > 1 && !isWrapAroundSlot(fromSlot, toSlot)) {
            int step = fromSlot > toSlot ? -1 : 1;
            for (int slot = fromSlot + step; slot != toSlot; slot += step) {
                if (slot < 0 || slot > 8) continue;
                PacketUtils.sendSilently(new ServerboundSetCarriedItemPacket(slot));
                this.log("Sent intermediate slot: " + slot);
            }
        }
    }

    private boolean isWrapAroundSlot(int fromSlot, int toSlot) {
        return fromSlot == 0 && toSlot == 8 || fromSlot == 8 && toSlot == 0;
    }

    private void resetState() {
        this.lastSlot = -1;
        this.inventoryOpenTime = 0L;
        this.inventoryOpen = false;
        this.storedClosePacket = null;
        this.inventoryCloseDelay = 0L;
        this.themisBlinkLastSend = System.currentTimeMillis();
        this.themisBlinkCount = 0;
        this.lastYaw = 0.0f;
        this.lastPitch = 0.0f;
        this.currentYaw = 0.0f;
        this.currentPitch = 0.0f;
        this.yawDiff = 0.0f;
        this.pitchDiff = 0.0f;
        this.lastPlacedYawDiff = 0.0f;
        this.lastPlacedPitchDiff = 0.0f;
        this.rotated = false;
        this.inventoryTimer.reset();
    }

    private boolean shouldSkip() {
        return nullCheck()
                || this.onlyRemoteServer.getValue() && mc.isSingleplayer()
                || mc.player.isSpectator()
                || !mc.player.isAlive()
                || mc.player.isDeadOrDying()
                || mc.screen instanceof ProgressScreen;
    }

    private boolean isAimStepRotation(float yaw, float pitch) {
        if (this.lastYaw == 0.0f && this.lastPitch == 0.0f) {
            return false;
        }
        double yawDelta = Math.abs(Mth.wrapDegrees(yaw - this.lastYaw));
        double pitchDelta = Math.abs(pitch - this.lastPitch);
        boolean yawStuck = yawDelta < 1.0E-5 && pitchDelta > 1.0;
        boolean pitchStuck = pitchDelta < 1.0E-5 && yawDelta > 1.0;
        return yawStuck || pitchStuck;
    }

    private float[] applyAimStep(float yaw, float pitch) {
        double yawDelta = Math.abs(Mth.wrapDegrees(yaw - this.lastYaw));
        double pitchDelta = Math.abs(pitch - this.lastPitch);
        float newYaw = yaw;
        float newPitch = pitch;
        if (yawDelta < 1.0E-5 && pitchDelta > 1.0) {
            newYaw = this.lastYaw + (float) (this.random.nextGaussian() * 0.001);
        }
        if (pitchDelta < 1.0E-5 && yawDelta > 1.0) {
            newPitch = this.lastPitch + (float) (this.random.nextGaussian() * 0.001);
        }
        return new float[]{newYaw, newPitch};
    }

    private float[] applyPerfectRotation(float yaw, float pitch) {
        double jitter;
        if (this.lastYaw == 0.0f && this.lastPitch == 0.0f) {
            return new float[]{yaw, pitch};
        }
        double yawDelta = Math.abs(Mth.wrapDegrees(yaw - this.lastYaw));
        double pitchDelta = Math.abs(pitch - this.lastPitch);
        float newYaw = yaw;
        float newPitch = pitch;
        if (!this.isNearZeroOrMultiple(yawDelta) && this.isKnownRotationStep(yawDelta)) {
            jitter = this.random.nextGaussian() * 0.005;
            newYaw = yaw + (float) jitter;
        }
        if (!this.isNearZeroOrMultiple(pitchDelta) && this.isKnownRotationStep(pitchDelta)) {
            jitter = this.random.nextGaussian() * 0.005;
            newPitch = pitch + (float) jitter;
        }
        return new float[]{newYaw, newPitch};
    }

    private boolean isNearZeroOrMultiple(double value) {
        return Math.abs(value) <= 1.0E-10 || this.isMultipleOf(360.0, value);
    }

    private boolean isMultipleOf(double base, double value) {
        if (base == 0.0) {
            return Math.abs(value) <= 1.0E-10;
        }
        double ratio = value / base;
        return Math.abs(ratio - (double) Math.round(ratio)) <= 1.0E-10;
    }

    private boolean isKnownRotationStep(double value) {
        if (Double.isInfinite(value) || Double.isNaN(value)) {
            return false;
        }
        for (double step : perfectRotSteps) {
            if (!this.isMultipleOf(step, value)) continue;
            return true;
        }
        return false;
    }

    private void log(String message) {
        if (logging.getValue()) ChatUtils.addChatMessage("[Disabler] " + message);
    }

}
