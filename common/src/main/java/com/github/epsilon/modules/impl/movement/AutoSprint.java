package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.TickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;

public class AutoSprint extends Module {

    public static final AutoSprint INSTANCE = new AutoSprint();

    private AutoSprint() {
        super("Auto Sprint", Category.MOVEMENT);
    }

    private enum Mode {
        Legit,
        Smart
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Legit);

    private final BoolSetting stopWhileUsing = boolSetting("Stop While Using", true, () -> mode.is(Mode.Smart));

    public final BoolSetting keepSprint = boolSetting("Keep Sprint", false);
    public final DoubleSetting motion = doubleSetting("Motion", 1.0, 0.0, 1.0, 0.1, keepSprint::getValue);

    @Override
    protected void onDisable() {
        if (mode.is(Mode.Legit)) {
            mc.options.keySprint.setDown(false);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        if (mode.is(Mode.Legit)) {
            mc.options.keySprint.setDown(true);
        } else {
            mc.player.setSprinting(
                    mc.player.input.hasForwardImpulse()
                            && mc.player.getFoodData().getFoodLevel() > 6
                            && !mc.player.horizontalCollision
                            && (!mc.player.isUsingItem() || !stopWhileUsing.getValue())
            );
        }
    }

}