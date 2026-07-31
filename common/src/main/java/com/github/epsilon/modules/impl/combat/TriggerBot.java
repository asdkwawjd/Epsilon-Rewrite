package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.utils.rotation.RaytraceUtils;
import com.github.epsilon.utils.rotation.Rot2f;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class TriggerBot extends Module {

    public static final TriggerBot INSTANCE = new TriggerBot();

    private final DoubleSetting range = doubleSetting("Range", 3.5, 1.0, 7.0, 0.1);

    private TriggerBot() {
        super("Trigger Bot", Category.COMBAT);
    }

    @EventHandler
    public void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) return;

        if (!isCooldownReady()) return;

        if (mc.player.getDeltaMovement().y > 0.0) return;

        LivingEntity target = getCrosshairTarget();
        if (target == null) return;

        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private boolean isCooldownReady() {
        return mc.player.getAttackStrengthScale(0.0f) >= 1.0f;
    }

    private LivingEntity getCrosshairTarget() {
        Rot2f rotation = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        HitResult hit = RaytraceUtils.raytrace(rotation, range.getValue());

        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living) {
            if (living.isAlive() && living != mc.player) {
                return living;
            }
        }

        return null;
    }

}
