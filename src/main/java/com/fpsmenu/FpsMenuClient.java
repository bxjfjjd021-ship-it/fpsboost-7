package com.fpsmenu;

import com.fpsmenu.ui.FpsMenuScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class FpsMenuClient implements ClientModInitializer {
    public static KeyBinding OPEN_KEY;

    @Override
    public void onInitializeClient() {
        FpsMenuConfig.load();

        OPEN_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.fpsmenu.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "key.categories.fpsmenu"));

        // При старте: применяем FOV и освобождаем P (в ваниле на ней "Социальные взаимодействия")
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            FpsMenuConfig cfg = FpsMenuConfig.get();
            GameOptions o = client.options;
            if (cfg.fov > 110) {
                o.getFov().setValue(110);
            } else {
                cfg.fov = o.getFov().getValue();
            }
            if (!cfg.freedPKey) {
                cfg.freedPKey = true;
                KeyBinding social = client.options.socialInteractionsKey;
                if (social.isDefault()) {
                    social.setBoundKey(InputUtil.UNKNOWN_KEY);
                    KeyBinding.updateKeysByCode();
                    client.options.write();
                }
                FpsMenuConfig.save();
            }
        });

        // Клавиша P
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_KEY.wasPressed()) {
                if (client.currentScreen == null && client.world != null) {
                    client.setScreen(new FpsMenuScreen(null));
                }
            }
        });

        // Кнопка в меню паузы и на титульном экране (удобно на телефоне, без клавиатуры)
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof GameMenuScreen || screen instanceof TitleScreen) {
                ButtonWidget b = ButtonWidget.builder(Text.literal("FPS меню"),
                                btn -> client.setScreen(new FpsMenuScreen(screen)))
                        .dimensions(6, 6, 80, 20)
                        .build();
                Screens.getButtons(screen).add(b);
            }
        });

        // Счётчик FPS на экране
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            FpsMenuConfig c = FpsMenuConfig.get();
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!c.fpsCounter || mc.options.hudHidden) return;
            int fps = mc.getCurrentFps();
            String s = fps + " FPS";
            int w = mc.textRenderer.getWidth(s);
            int col = fps >= 100 ? 0xFF55FF7A : (fps >= 60 ? 0xFFFFE066 : 0xFFFF5C5C);
            ctx.fill(3, 3, 3 + w + 8, 17, 0x90000000);
            ctx.drawText(mc.textRenderer, s, 7, 7, col, true);
        });
    }
}
