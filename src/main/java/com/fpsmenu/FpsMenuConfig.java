package com.fpsmenu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Настройки самого мода (остальное хранится в обычном options.txt игры). */
public final class FpsMenuConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static FpsMenuConfig instance = new FpsMenuConfig();

    public int fov = 70;               // 30..150 (выше 110 работает через миксин)
    public int accent = 0;             // индекс цвета акцента
    public boolean animations = true;
    public float animSpeed = 1.0f;     // 0.5 .. 2.0
    public boolean fpsCounter = false;
    public boolean freedPKey = false;  // освободили ли клавишу P от "Социальных взаимодействий"

    public static FpsMenuConfig get() {
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("fpsmenu.json");
    }

    public static void load() {
        Path p = path();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p)) {
                FpsMenuConfig c = GSON.fromJson(r, FpsMenuConfig.class);
                if (c != null) instance = c;
            } catch (Exception ignored) {
                // битый файл — используем значения по умолчанию
            }
        }
        instance.sanitize();
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(instance));
        } catch (Exception ignored) {
        }
    }

    public void resetMenuSettings() {
        accent = 0;
        animations = true;
        animSpeed = 1.0f;
        fpsCounter = false;
    }

    private void sanitize() {
        fov = Math.max(30, Math.min(150, fov));
        accent = Math.floorMod(accent, 5);
        animSpeed = Math.max(0.5f, Math.min(2.0f, animSpeed));
    }
}
