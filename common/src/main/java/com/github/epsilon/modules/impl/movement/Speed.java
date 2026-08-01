package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.MoveEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.ClickSlotUtils;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.player.PlayerUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class Speed extends Module {

    public static final Speed INSTANCE = new Speed();

    private Speed() {
        super("Speed", Category.MOVEMENT);
    }

    private enum Mode {
        Strafe,
        StrafeStrict,
        Grim,
        Vulcan_2_8_6,
        Vulcan,
        Vulcan_288,
        Vulcan_Ground_286
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Strafe);
    private final DoubleSetting collideSpeed = doubleSetting("CollideSpeed", 0.08, 0, 0.08, 0.01, () -> mode.is(Mode.Grim));
    private final BoolSetting strict = boolSetting("Strict", true, () -> mode.is(Mode.Grim));
    private final BoolSetting boat = boolSetting("BoatLongJump", true, () -> mode.is(Mode.Grim));
    private final DoubleSetting boatExpand = doubleSetting("BoatExpand", 0.2, 0, 1, 0.01, () -> mode.is(Mode.Grim));
    private final DoubleSetting boatSpeed = doubleSetting("BoatSpeed", 0.2, -2, 2, 0.01, () -> mode.is(Mode.Grim));
    private final DoubleSetting boatJump = doubleSetting("BoatJump", 0.2, 0, 2, 0.01, () -> mode.is(Mode.Grim));

    private final BoolSetting inWater = boolSetting("InWater", false, () -> !mode.is(Mode.Grim));
    private final BoolSetting inBlock = boolSetting("InBlock", false, () -> !mode.is(Mode.Grim));
    private final BoolSetting airStop = boolSetting("AirStop", true, () -> !mode.is(Mode.Grim));
    private final DoubleSetting lagTime = doubleSetting("LagTime", 500, 0, 1000, 1, () -> !mode.is(Mode.Grim));

    private final BoolSetting jump = boolSetting("Jump", true, () -> mode.is(Mode.Strafe));
    private final DoubleSetting strafeSpeed = doubleSetting("Speed", 0.2873, 0, 1.0, 0.0001, () -> mode.is(Mode.Strafe));
    private final BoolSetting explosions = boolSetting("ExplosionsBoost", false, () -> mode.is(Mode.Strafe));
    private final BoolSetting velocity = boolSetting("VelocityBoost", true, () -> mode.is(Mode.Strafe));
    private final DoubleSetting multiplier = doubleSetting("H-Factor", 1.0, 0.0, 5.0, 0.01, () -> mode.is(Mode.Strafe));
    private final DoubleSetting vertical = doubleSetting("V-Factor", 1.0, 0.0, 5.0, 0.01, () -> mode.is(Mode.Strafe));
    private final IntSetting coolDown = intSetting("Cooldown", 1000, 0, 5000, 1, () -> mode.is(Mode.Strafe));
    private final BoolSetting slow = boolSetting("Slowness", false, () -> mode.is(Mode.Strafe));

    // -- Vulcan_2_8_6 --

    private final DoubleSetting vulcan286Speed = doubleSetting("Vulcan286 Speed", 0.3355, 0.1, 1.0, 0.0001, () -> mode.is(Mode.Vulcan_2_8_6));
    private int vulcanTicks;
    private int vulcanSpeedLvl;
    private boolean vulcanJumped;

    // -- Vulcan (Elytra) --

    private final DoubleSetting vulcanSpeedEF0 = doubleSetting("Speed (No Effect)", 0.8, 0.1, 2.0, 0.01, () -> mode.is(Mode.Vulcan));
    private final DoubleSetting vulcanSpeedEF1 = doubleSetting("Speed (Speed I)", 0.9, 0.1, 2.0, 0.01, () -> mode.is(Mode.Vulcan));
    private final DoubleSetting vulcanSpeedEF2 = doubleSetting("Speed (Speed II)", 1.0, 0.1, 2.0, 0.01, () -> mode.is(Mode.Vulcan));
    private final BoolSetting autoSwapVulcan = boolSetting("Auto Swap Elytra", true, () -> mode.is(Mode.Vulcan));
    private net.minecraft.world.item.Item vulcanSavedChest;

    // -- Vulcan 2.8.8 --

    private int airTicks;
    private boolean lastOnGround;

    private boolean stop;
    private double speed;
    private double distance;

    private int strictTicks;
    private int strafe = 4;
    private int stage;
    private double lastExp;
    private boolean boost;

    private final TimerUtils expTimer = new TimerUtils();
    private final TimerUtils lagTimer = new TimerUtils();

    @Override
    protected void onEnable() {
        if (mc.player != null) {
            speed = getSpeed(false);
            distance = getDistance2D();
        }
        stage = 4;

        if (mode.is(Mode.Vulcan_2_8_6)) {
            vulcanTicks = 0;
            vulcanSpeedLvl = 0;
            vulcanJumped = false;
        }

        if (mode.is(Mode.Vulcan_288) || mode.is(Mode.Vulcan_Ground_286)) {
            airTicks = 0;
            lastOnGround = true;
        }

        if (mode.is(Mode.Vulcan) && autoSwapVulcan.getValue()) {
            vulcanSavedChest = mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem();
            if (!mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
                FindItemResult elytra = InvUtils.find(Items.ELYTRA);
                if (elytra.found()) {
                    equipArmor(Items.ELYTRA, elytra.slot());
                }
            }
        }
    }

    @Override
    protected void onDisable() {
        if (mode.is(Mode.Vulcan) && autoSwapVulcan.getValue() && vulcanSavedChest != Items.AIR) {
            if (mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
                FindItemResult chest = InvUtils.find(vulcanSavedChest);
                if (chest.found()) {
                    equipArmor(vulcanSavedChest, chest.slot());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck()) return;

        if (mode.is(Mode.Strafe)) {
            if (event.getPacket() instanceof ClientboundSetEntityMotionPacket(int id, Vec3 packetMovement)) {
                if (id == mc.player.getId() && this.velocity.getValue()) {
                    double speed = Math.hypot(packetMovement.x, packetMovement.z);

                    this.lastExp = this.expTimer
                            .passedMillise(this.coolDown.getValue())
                            ? speed
                            : (speed - this.lastExp);

                    if (this.lastExp > 0) {
                        this.expTimer.reset();

                        this.speed += this.lastExp * this.multiplier.getValue();
                        this.distance += this.lastExp * this.multiplier.getValue();

                        if (mc.player.getDeltaMovement().y > 0 && this.vertical.getValue() != 0) {
                            setMotionY(mc.player.getDeltaMovement().y * this.vertical.getValue());
                        }
                    }
                }
            } else if (event.getPacket() instanceof ClientboundExplodePacket packet) {
                if (this.explosions.getValue()) {
                    if (mc.player.position().distanceTo(packet.center()) < 15) {
                        Vec3 knockback = packet.playerKnockback().orElse(Vec3.ZERO);
                        double speed = Math.hypot(knockback.x, knockback.z);
                        this.lastExp = this.expTimer.passedMillise(this.coolDown.getValue()) ? speed : (speed - this.lastExp);

                        if (this.lastExp > 0) {
                            this.expTimer.reset();

                            this.speed += this.lastExp * this.multiplier.getValue();
                            this.distance += this.lastExp * this.multiplier.getValue();

                            if (mc.player.getDeltaMovement().y > 0) {
                                setMotionY(mc.player.getDeltaMovement().y * this.vertical.getValue());
                            }
                        }
                    }
                }
            }
        }
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket || event.getPacket() instanceof ClientboundPlayerRotationPacket) {
            lagTimer.reset();
            resetStrafe();
        }
    }

    @EventHandler
    private void onPlayerTickPre(PlayerTickEvent.Pre event) {
        if (mode.is(Mode.Grim)) {
            if (!mc.player.isMoving()) {
                return;
            }

            int collisions = 0;
            AABB box = strict.getValue() ? mc.player.getBoundingBox() : mc.player.getBoundingBox().inflate(1.0);

            for (Entity entity : mc.level.entitiesForRendering()) {
                AABB entityBox = entity.getBoundingBox();
                if (boat.getValue() && mc.player.onGround() && entity instanceof Boat && box.intersects(entityBox.inflate(boatExpand.getValue()))) {
                    double yaw = Math.toRadians(getSprintYaw(mc.player.getYRot()));
                    double boost = boatSpeed.getValue();
                    mc.player.setDeltaMovement(-Math.sin(yaw) * boost, boatJump.getValue(), Math.cos(yaw) * boost);
                    return;
                } else if (entity != mc.player && entity instanceof LivingEntity && !(entity instanceof ArmorStand) && box.intersects(entityBox)) {
                    collisions++;
                }
            }

            double yaw = Math.toRadians(getSprintYaw(mc.player.getYRot()));
            double boost = this.collideSpeed.getValue() * collisions;
            mc.player.push(-Math.sin(yaw) * boost, 0.0, Math.cos(yaw) * boost);
        }

        if (mode.is(Mode.Vulcan_2_8_6)) {
            handleVulcan286();
        }

        if (mode.is(Mode.Vulcan)) {
            handleVulcanElytra();
        }

        if (mode.is(Mode.Vulcan_288)) {
            handleVulcan288();
        }

        if (mode.is(Mode.Vulcan_Ground_286)) {
            handleVulcanGround286();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onPacketSend(PacketEvent.Send event) {
        if (nullCheck()) return;

        if (!(event.getPacket() instanceof ServerboundMovePlayerPacket packet)) return;

        if (mode.is(Mode.Vulcan_288)) {
            if (mc.player.getDeltaMovement().y < 0.0) {
                event.setPacket(makeOnGround(packet, true));
            }
        }

        if (mode.is(Mode.Vulcan_Ground_286)) {
            if (!mc.player.onGround()) return;
            if (!mc.player.isMoving() || mc.options.keyJump.isDown()) return;
            if (!collidesBottomVertical()) return;

            event.setPacket(makeOffsetY(packet, 0.005));
        }
    }

    @EventHandler
    private void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (!nullCheck()) distance = getDistance2D();
    }

    @EventHandler
    private void onMove(MoveEvent event) {
        if (!mc.player.isMoving() && airStop.getValue() && !mode.is(Mode.Grim)) {
            mc.player.setDeltaMovement(0.0, mc.player.getDeltaMovement().y, 0.0);
        }
        if (!this.inWater.getValue() && (mc.player.isUnderWater() || mc.player.isInWater() || mc.player.isInLava())
                || mc.player.isPassenger()
                || mc.player.onClimbable()
                || !inBlock.getValue() && PlayerUtils.isInBlock()
                || mc.player.getAbilities().flying
                || mc.player.isFallFlying()
                || !mc.player.isMoving()) {
            resetStrafe();
            this.stop = true;
            return;
        }
        if (mode.is(Mode.Strafe)) {
            if (this.stop) {
                this.stop = false;
                return;
            }

            if (!lagTimer.passedMillise(this.lagTime.getValue())) {
                return;
            }

            if (this.stage == 1) {
                this.speed = 1.35 * getSpeed(this.slow.getValue(), this.strafeSpeed.getValue()) - 0.01;
            } else if (this.stage == 2 && mc.player.onGround() && (mc.options.keyJump.isDown() || this.jump.getValue())) {
                double yMotion = 0.3999 + getJumpSpeed();
                setMotionY(yMotion);
                event.setY(yMotion);
                event.cancel();
                this.speed = this.speed * (this.boost ? 1.6835 : 1.395);
            } else if (this.stage == 3) {
                this.speed = this.distance - 0.66
                        * (this.distance - getSpeed(this.slow.getValue(), this.strafeSpeed.getValue()));

                this.boost = !this.boost;
            } else {
                if ((canCollide(null,
                        mc.player.getBoundingBox().move(0.0, mc.player.getDeltaMovement().y, 0.0))
                        || mc.player.minorHorizontalCollision)
                        && this.stage > 0) {
                    this.stage = 1;
                }

                this.speed = this.distance - this.distance / 159.0;
            }

            this.speed = Math.min(this.speed, 10);
            this.speed = Math.max(this.speed, getSpeed(this.slow.getValue(), this.strafeSpeed.getValue()));
            Vec2 moveVector = mc.player.input.getMoveVector();
            double n = moveVector.y;
            double n2 = moveVector.x;
            double n3 = mc.player.getYRot();
            if (n == 0.0 && n2 == 0.0) {
                event.setX(0.0);
                event.setZ(0.0);
            }
            event.setX((n * this.speed * -Math.sin(Math.toRadians(n3)) + n2 * this.speed * Math.cos(Math.toRadians(n3))) * 0.99);
            event.setZ((n * this.speed * Math.cos(Math.toRadians(n3)) - n2 * this.speed * -Math.sin(Math.toRadians(n3))) * 0.99);
            event.cancel();

            this.stage++;
            return;
        }
        double speedEffect = 1.0;
        double slowEffect = 1.0;
        if (mc.player.hasEffect(MobEffects.SPEED)) {
            double amplifier = mc.player.getEffect(MobEffects.SPEED).getAmplifier();
            speedEffect = 1 + (0.2 * (amplifier + 1));
        }
        if (mc.player.hasEffect(MobEffects.SLOWNESS)) {
            double amplifier = mc.player.getEffect(MobEffects.SLOWNESS).getAmplifier();
            slowEffect = 1 + (0.2 * (amplifier + 1));
        }
        final double base = 0.2873f * speedEffect / slowEffect;
        float jumpEffect = 0.0f;
        if (mc.player.hasEffect(MobEffects.JUMP_BOOST)) {
            jumpEffect += (mc.player.getEffect(MobEffects.JUMP_BOOST).getAmplifier() + 1) * 0.1f;
        }

        if (mode.getValue() == Mode.StrafeStrict) {
            if (!lagTimer.passedMillise(lagTime.getValue())) {
                return;
            }
            if (strafe == 1) {
                speed = 1.35f * base - 0.01f;
            } else if (strafe == 2) {
                if (mc.player.input.keyPresses.jump() || !mc.player.onGround()) {
                    return;
                }
                float jump = 0.3999999463558197f + jumpEffect;
                event.setY(jump);
                event.cancel();
                setMotionY(jump);
                speed *= 2.149;
            } else if (strafe == 3) {
                double moveSpeed = 0.66 * (distance - base);
                speed = distance - moveSpeed;
            } else {
                if ((!mc.level.noCollision(mc.player, mc.player.getBoundingBox().move(0,
                        mc.player.getDeltaMovement().y, 0)) || mc.player.verticalCollision) && strafe > 0) {
                    strafe = 1;
                }
                speed = distance - distance / 159.0;
            }
            strictTicks++;
            speed = Math.max(speed, base);
            double baseMax = 0.465 * speedEffect / slowEffect;
            double baseMin = 0.44 * speedEffect / slowEffect;
            speed = Math.min(speed, strictTicks > 25 ? baseMax : baseMin);
            if (strictTicks > 50) {
                strictTicks = 0;
            }
            final Vec2 motion = handleStrafeMotion((float) speed);
            event.setX(motion.x);
            event.setZ(motion.y);
            event.cancel();
            strafe++;
        }
    }

    private Vec2 handleStrafeMotion(final float speed) {
        Vec2 moveVector = mc.player.input.getMoveVector();
        float forward = moveVector.y;
        float strafe = moveVector.x;
        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float yaw = Mth.lerp(tickDelta, mc.player.yRotO, mc.player.getYRot());
        if (forward == 0.0f && strafe == 0.0f) {
            return Vec2.ZERO;
        }
        float rx = (float) Math.cos(Math.toRadians(yaw));
        float rz = (float) -Math.sin(Math.toRadians(yaw));
        return new Vec2((forward * speed * rz) + (strafe * speed * rx), (forward * speed * rx) - (strafe * speed * rz));
    }

    private double getSpeed(boolean slowness) {
        double defaultSpeed = 0.2873;
        return getSpeed(slowness, defaultSpeed);
    }

    private double getSpeed(boolean slowness, double defaultSpeed) {
        if (mc.player.hasEffect(MobEffects.SPEED)) {
            int amplifier = mc.player.getEffect(MobEffects.SPEED).getAmplifier();
            defaultSpeed *= 1.0 + 0.2 * (amplifier + 1);
        }

        if (slowness && mc.player.hasEffect(MobEffects.SLOWNESS)) {
            int amplifier = mc.player.getEffect(MobEffects.SLOWNESS).getAmplifier();
            defaultSpeed /= 1.0 + 0.2 * (amplifier + 1);
        }

        if (mc.player.isCrouching()) {
            defaultSpeed /= 5;
        }
        return defaultSpeed;
    }

    private double getJumpSpeed() {
        double defaultSpeed = 0.0;

        if (mc.player.hasEffect(MobEffects.JUMP_BOOST)) {
            int amplifier = mc.player.getEffect(MobEffects.JUMP_BOOST).getAmplifier();
            defaultSpeed += (amplifier + 1) * 0.1;
        }

        return defaultSpeed;
    }

    private boolean canCollide(Entity entity, AABB box) {
        return !mc.level.noBlockCollision(entity, box);
    }

    private void setMotionY(double y) {
        Vec3 movement = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(movement.x, y, movement.z);
    }

    private double getDistance2D() {
        double xDist = mc.player.getX() - mc.player.xo;
        double zDist = mc.player.getZ() - mc.player.zo;
        return Math.sqrt(xDist * xDist + zDist * zDist);
    }

    private float getSprintYaw(float yaw) {
        if (mc.options.keyUp.isDown() && !mc.options.keyDown.isDown()) {
            if (mc.options.keyLeft.isDown() && !mc.options.keyRight.isDown()) {
                yaw -= 45f;
            } else if (mc.options.keyRight.isDown() && !mc.options.keyLeft.isDown()) {
                yaw += 45f;
            }
        } else if (mc.options.keyDown.isDown() && !mc.options.keyUp.isDown()) {
            yaw += 180f;
            if (mc.options.keyLeft.isDown() && !mc.options.keyRight.isDown()) {
                yaw += 45f;
            } else if (mc.options.keyRight.isDown() && !mc.options.keyLeft.isDown()) {
                yaw -= 45f;
            }
        } else if (mc.options.keyLeft.isDown() && !mc.options.keyRight.isDown()) {
            yaw -= 90f;
        } else if (mc.options.keyRight.isDown() && !mc.options.keyLeft.isDown()) {
            yaw += 90f;
        }
        return Mth.wrapDegrees(yaw);
    }

    private void resetStrafe() {
        strafe = 4;
        strictTicks = 0;
        speed = 0.0f;
        distance = 0.0;
    }

    // -- Vulcan 2.8.6 --

    private void handleVulcan286() {
        if (!mc.player.isMoving()) return;

        if (mc.player.onGround() && mc.options.keyJump.isDown()) {
            vulcanTicks = 0;
            vulcanSpeedLvl = 0;
            vulcanJumped = true;
            if (mc.player.hasEffect(MobEffects.SPEED)) {
                vulcanSpeedLvl = mc.player.getEffect(MobEffects.SPEED).getAmplifier() + 1;
            }
        }

        if (vulcanJumped) {
            vulcanTicks++;
            if (vulcanTicks == 1) {
                strafeToSpeed(vulcan286Speed.getValue() * (1 + vulcanSpeedLvl * 0.3819));
            }
            if (vulcanTicks == 2 && mc.player.isSprinting()) {
                strafeToSpeed(0.3284 * (1 + vulcanSpeedLvl * 0.355));
            }
            if (vulcanTicks == 4) {
                Vec3 vel = mc.player.position();
                mc.player.setPosRaw(vel.x, vel.y - 0.376, vel.z);
            }
            if (vulcanTicks == 6) {
                if (mc.player.flyDist > 0.298) {
                    strafeToSpeed(0.298);
                }
                vulcanJumped = false;
            }
        }
    }

    // -- Vulcan (Elytra) --

    private void handleVulcanElytra() {
        if (!mc.player.isFallFlying()) return;
        if (!mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) return;

        double speed;
        if (mc.player.hasEffect(MobEffects.SPEED)) {
            int amp = mc.player.getEffect(MobEffects.SPEED).getAmplifier();
            if (amp >= 1) speed = vulcanSpeedEF2.getValue();
            else speed = vulcanSpeedEF1.getValue();
        } else {
            speed = vulcanSpeedEF0.getValue();
        }

        strafeToSpeed(speed);
    }

    private void strafeToSpeed(double speed) {
        float yaw = mc.player.getYRot();
        Vec2 moveVec = mc.player.input.getMoveVector();
        float forward = moveVec.y;
        float strafe = moveVec.x;

        if (forward != 0.0f) {
            if (strafe > 0.0f) yaw += ((forward > 0.0f) ? -45 : 45);
            else if (strafe < 0.0f) yaw += ((forward > 0.0f) ? 45 : -45);
            strafe = 0.0f;
            forward = forward > 0.0f ? 1.0f : -1.0f;
        }

        double rad = Math.toRadians(yaw + 90.0f);
        mc.player.setDeltaMovement(
            forward * speed * Math.cos(rad) + strafe * speed * Math.sin(rad),
            mc.player.getDeltaMovement().y,
            forward * speed * Math.sin(rad) - strafe * speed * Math.cos(rad)
        );
    }

    private void equipArmor(net.minecraft.world.item.Item item, int slot) {
        int invSlot = slot < 9 ? 36 + slot : slot;
        ClickSlotUtils.click(invSlot);
        ClickSlotUtils.click(6);
        ClickSlotUtils.click(invSlot);
        mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.inventoryMenu.containerId));
    }

    // -- Vulcan 2.8.8 --

    private void handleVulcan288() {
        if (!canSpeed()) return;

        updateAirTicks();
        boolean hasSpeed = getSpeedAmplifier() > 0.0;

        if (mc.player.onGround()) {
            if (!mc.player.isMoving()) return;
            autoJump();
            mc.player.setDeltaMovement(withStrafe(mc.player.getDeltaMovement(), hasSpeed ? 0.771 : 0.5));
            return;
        }

        if (airTicks == 1) {
            mc.player.setDeltaMovement(withStrafe(mc.player.getDeltaMovement(), hasSpeed ? 0.605 : 0.31));
        } else if (airTicks == 2) {
            Vec3 vel = withStrafe(mc.player.getDeltaMovement(), hasSpeed ? 0.57 : 0.29);
            mc.player.setDeltaMovement(vel.x, hasSpeed ? -0.5 : -0.37, vel.z);
        } else if (airTicks == 3) {
            mc.player.setDeltaMovement(withStrafe(mc.player.getDeltaMovement(), hasSpeed ? 0.595 : 0.27));
        } else if (airTicks == 4) {
            mc.player.setDeltaMovement(withStrafe(mc.player.getDeltaMovement(), hasSpeed ? 0.595 : 0.28));
        }

        if (hasSpeed && mc.player.fallDistance > 0.0f) {
            Vec3 vel = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(vel.x * 1.055, vel.y, vel.z * 1.055);
        }
    }

    // -- Vulcan Ground 2.8.6 --

    private void handleVulcanGround286() {
        if (!canSpeed()) return;
        if (!mc.player.isMoving() || mc.options.keyJump.isDown()) return;
        if (!collidesBottomVertical()) return;

        boolean hasSpeed = getSpeedAmplifier() > 0.0;
        boolean movingSideways = mc.player.input.getMoveVector().x != 0.0f;
        double strafe = hasSpeed ? 0.59 : movingSideways ? 0.41 : 0.42;

        Vec3 vel = withStrafe(mc.player.getDeltaMovement(), strafe);
        mc.player.setDeltaMovement(vel.x, 0.005, vel.z);
    }

    // -- Helpers --

    private void updateAirTicks() {
        boolean onGround = mc.player.onGround();
        if (onGround) {
            airTicks = 0;
        } else if (lastOnGround) {
            airTicks = 1;
        } else {
            airTicks++;
        }
        lastOnGround = onGround;
    }

    private boolean canSpeed() {
        return !mc.player.isShiftKeyDown()
                && !mc.player.isInWater()
                && !mc.player.isInLava()
                && !mc.player.onClimbable()
                && !mc.player.isPassenger()
                && !mc.player.isFallFlying();
    }

    private void autoJump() {
        if (mc.player.getDeltaMovement().y > 0.01) return;
        mc.player.jumpFromGround();
    }

    private double getSpeedAmplifier() {
        var effect = mc.player.getEffect(MobEffects.SPEED);
        return effect == null ? 0.0 : effect.getAmplifier();
    }

    private Vec3 withStrafe(Vec3 currentVel, double speed) {
        Vec2 input = mc.player.input.getMoveVector();
        if (input.x == 0.0f && input.y == 0.0f) return currentVel;

        float yaw = mc.player.getYRot();
        float forward = input.y;
        float strafe = input.x;

        if (forward != 0.0f) {
            if (strafe > 0.0f) yaw += forward > 0.0f ? -45 : 45;
            else if (strafe < 0.0f) yaw += forward > 0.0f ? 45 : -45;
            strafe = 0.0f;
            forward = forward > 0.0f ? 1.0f : -1.0f;
        }

        double rad = Math.toRadians(yaw + 90.0f);
        return new Vec3(
                forward * speed * Math.cos(rad) + strafe * speed * Math.sin(rad),
                currentVel.y,
                forward * speed * Math.sin(rad) - strafe * speed * Math.cos(rad)
        );
    }

    private boolean collidesBottomVertical() {
        for (var shape : mc.level.getBlockCollisions(mc.player,
                mc.player.getBoundingBox().move(0.0, -0.005, 0.0))) {
            if (!shape.isEmpty()) return true;
        }
        return false;
    }

    private ServerboundMovePlayerPacket makeOnGround(ServerboundMovePlayerPacket packet, boolean onGround) {
        if (packet instanceof ServerboundMovePlayerPacket.Pos) {
            return new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), onGround, mc.player.horizontalCollision);
        }
        if (packet instanceof ServerboundMovePlayerPacket.PosRot) {
            return new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot(), onGround, mc.player.horizontalCollision);
        }
        if (packet instanceof ServerboundMovePlayerPacket.Rot) {
            return new ServerboundMovePlayerPacket.Rot(mc.player.getYRot(), mc.player.getXRot(), onGround, mc.player.horizontalCollision);
        }
        if (packet instanceof ServerboundMovePlayerPacket.StatusOnly) {
            return new ServerboundMovePlayerPacket.StatusOnly(onGround, mc.player.horizontalCollision);
        }
        return packet;
    }

    private ServerboundMovePlayerPacket makeOffsetY(ServerboundMovePlayerPacket packet, double offset) {
        if (packet instanceof ServerboundMovePlayerPacket.Pos) {
            return new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + offset, mc.player.getZ(), mc.player.onGround(), mc.player.horizontalCollision);
        }
        if (packet instanceof ServerboundMovePlayerPacket.PosRot) {
            return new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY() + offset, mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot(), mc.player.onGround(), mc.player.horizontalCollision);
        }
        return packet;
    }

}
