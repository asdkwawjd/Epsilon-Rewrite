package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;

public class AutoSprint extends Module {

    public static final AutoSprint INSTANCE = new AutoSprint();

    private AutoSprint() {
        super("Auto Sprint", Category.MOVEMENT);
    }

    public final BoolSetting keepSprint = boolSetting("Keep Sprint", false);
    public final DoubleSetting motion = doubleSetting("Motion", 1.0, 0.0, 1.0, 0.1, keepSprint::getValue);

    @Override
    protected void onDisable() {
        if (mc.options.keySprint.isDown()) {
            mc.options.keySprint.setDown(false);
        }
    }

    @EventHandler
    private void onTick(PlayerTickEvent event) {
        mc.options.keySprint.setDown(true);
    }

}
