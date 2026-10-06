package com.fpsmenu.ui;

import com.fpsmenu.FpsMenuClient;
import com.fpsmenu.FpsMenuConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Мод-меню с анимациями: плавное появление, подсветка при наведении, скользящий индикатор вкладок,
 * переключатели и слайдеры с анимацией, прокрутка свайпом (удобно на телефоне).
 */
public class FpsMenuScreen extends Screen {

    private static final int[] ACCENTS = {0x4C8DFF, 0xA66BFF, 0x3DDC84, 0xFF5C7A, 0xFFA23A};
    private static final String[] ACCENT_NAMES = {"Синий", "Фиолетовый", "Зелёный", "Красный", "Оранжевый"};
    private static final String[] TAB_NAMES = {"FPS", "Камера", "Графика", "Меню"};
    private static final int TAB_STEP = 27;

    private final Screen parent;
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final FpsMenuConfig cfg = FpsMenuConfig.get();
    private final List<List<W>> tabs = new ArrayList<>();

    // раскладка
    private int px, py, pw, ph;
    private int sbX, sbY, sbW = 80;
    private int cX, cY, cW, cH;

    // анимации
    private float open, alpha, curScale = 1f, time;
    private boolean closing;
    private long lastNanos = System.nanoTime();
    private int tab;
    private float tabInd, tabFade = 1f;
    private final float[] tabHover = new float[TAB_NAMES.length];
    private float closeHover;
    private float scroll, scrollTarget, maxScroll;
    private float accR, accG, accB;
    private int acc;
    private String toast = "";
    private float toastT;

    // ввод
    private W pressed;
    private boolean pressInContent, dragScroll, sliderActive;
    private double dragDist;

    public FpsMenuScreen(Screen parent) {
        super(Text.literal("FPS Menu"));
        this.parent = parent;
        int a = ACCENTS[Math.floorMod(cfg.accent, ACCENTS.length)];
        accR = (a >> 16) & 0xFF;
        accG = (a >> 8) & 0xFF;
        accB = a & 0xFF;
        acc = a;
        syncFov();
        buildTabs();
    }

    // ------------------------------------------------------------------ настройки

    private void syncFov() {
        int v = mc.options.getFov().getValue();
        if (cfg.fov <= 110 || v != 110) cfg.fov = v;
    }

    private void preset(int p) {
        GameOptions o = mc.options;
        String name;
        if (p == 0) {
            o.getMaxFps().setValue(120);
            o.getEnableVsync().setValue(false);
            o.getViewDistance().setValue(6);
            o.getSimulationDistance().setValue(5);
            o.getEntityDistanceScaling().setValue(0.5);
            o.getGraphicsMode().setValue(GraphicsMode.FAST);
            o.getParticles().setValue(ParticlesMode.MINIMAL);
            o.getCloudRenderMode().setValue(CloudRenderMode.OFF);
            o.getEntityShadows().setValue(false);
            name = "Макс. FPS";
        } else if (p == 1) {
            o.getMaxFps().setValue(120);
            o.getEnableVsync().setValue(false);
            o.getViewDistance().setValue(10);
            o.getSimulationDistance().setValue(8);
            o.getEntityDistanceScaling().setValue(1.0);
            o.getGraphicsMode().setValue(GraphicsMode.FAST);
            o.getParticles().setValue(ParticlesMode.DECREASED);
            o.getCloudRenderMode().setValue(CloudRenderMode.FAST);
            o.getEntityShadows().setValue(true);
            name = "Баланс";
        } else {
            o.getMaxFps().setValue(90);
            o.getEnableVsync().setValue(false);
            o.getViewDistance().setValue(16);
            o.getSimulationDistance().setValue(12);
            o.getEntityDistanceScaling().setValue(1.5);
            o.getGraphicsMode().setValue(GraphicsMode.FANCY);
            o.getParticles().setValue(ParticlesMode.ALL);
            o.getCloudRenderMode().setValue(CloudRenderMode.FANCY);
            o.getEntityShadows().setValue(true);
            name = "Красота";
        }
        showToast("Пресет: " + name);
    }

    private void showToast(String s) {
        toast = s;
        toastT = 2.2f;
    }

    private String graphicsName() {
        GraphicsMode g = mc.options.getGraphicsMode().getValue();
        return g == GraphicsMode.FAST ? "Быстро" : (g == GraphicsMode.FANCY ? "Красиво" : "Сверх");
    }

    private String particlesName() {
        ParticlesMode p = mc.options.getParticles().getValue();
        return p == ParticlesMode.ALL ? "Все" : (p == ParticlesMode.DECREASED ? "Меньше" : "Минимум");
    }

    private String cloudsName() {
        CloudRenderMode c = mc.options.getCloudRenderMode().getValue();
        return c == CloudRenderMode.OFF ? "Выкл" : (c == CloudRenderMode.FAST ? "Быстрые" : "Красивые");
    }

    private void buildTabs() {
        GameOptions o = mc.options;

        // --- FPS ---
        List<W> fps = new ArrayList<>();
        fps.add(new Header("БЫСТРЫЕ ПРЕСЕТЫ"));
        fps.add(new Button("Макс. FPS (120+)", () -> preset(0)));
        fps.add(new Button("Баланс", () -> preset(1)));
        fps.add(new Button("Красота", () -> preset(2)));
        fps.add(new Header("ПРОИЗВОДИТЕЛЬНОСТЬ"));
        fps.add(new Slider("Лимит FPS", 30, 260, 10, true,
                () -> o.getMaxFps().getValue(),
                v -> o.getMaxFps().setValue(v),
                v -> v >= 260 ? "Без лимита" : v + " FPS"));
        fps.add(new Toggle("Верт. синхронизация",
                () -> o.getEnableVsync().getValue(),
                v -> o.getEnableVsync().setValue(v)));
        fps.add(new Slider("Дальность прорисовки", 2, 32, 1, false,
                () -> o.getViewDistance().getValue(),
                v -> o.getViewDistance().setValue(v),
                v -> v + " чанк."));
        fps.add(new Slider("Дальность симуляции", 5, 32, 1, false,
                () -> o.getSimulationDistance().getValue(),
                v -> o.getSimulationDistance().setValue(v),
                v -> v + " чанк."));
        fps.add(new Slider("Дальность сущностей", 50, 300, 25, true,
                () -> (int) Math.round(o.getEntityDistanceScaling().getValue() * 100.0),
                v -> o.getEntityDistanceScaling().setValue(v / 100.0),
                v -> v + "%"));
        fps.add(new Toggle("FPS-счётчик на экране",
                () -> cfg.fpsCounter,
                v -> cfg.fpsCounter = v));
        tabs.add(fps);

        // --- Камера ---
        List<W> cam = new ArrayList<>();
        cam.add(new Header("ОБЗОР"));
        cam.add(new Slider("Поле зрения (FOV)", 30, 150, 1, true,
                () -> cfg.fov,
                v -> {
                    cfg.fov = v;
                    o.getFov().setValue(Math.min(v, 110));
                },
                v -> v + "°"));
        cam.add(new Slider("Эффекты FOV", 0, 100, 5, true,
                () -> (int) Math.round(o.getFovEffectScale().getValue() * 100.0),
                v -> o.getFovEffectScale().setValue(v / 100.0),
                v -> v + "%"));
        cam.add(new Header("ПЛАВНОСТЬ"));
        cam.add(new Toggle("Плавная камера",
                () -> o.smoothCameraEnabled,
                v -> o.smoothCameraEnabled = v));
        cam.add(new Toggle("Покачивание камеры",
                () -> o.getBobView().getValue(),
                v -> o.getBobView().setValue(v)));
        tabs.add(cam);

        // --- Графика ---
        List<W> gfx = new ArrayList<>();
        gfx.add(new Header("КАЧЕСТВО"));
        gfx.add(new Cycle("Графика", this::graphicsName, () -> {
            GraphicsMode g = o.getGraphicsMode().getValue();
            o.getGraphicsMode().setValue(g == GraphicsMode.FAST ? GraphicsMode.FANCY : GraphicsMode.FAST);
        }, null));
        gfx.add(new Cycle("Частицы", this::particlesName, () -> {
            ParticlesMode p = o.getParticles().getValue();
            o.getParticles().setValue(p == ParticlesMode.ALL ? ParticlesMode.DECREASED
                    : (p == ParticlesMode.DECREASED ? ParticlesMode.MINIMAL : ParticlesMode.ALL));
        }, null));
        gfx.add(new Cycle("Облака", this::cloudsName, () -> {
            CloudRenderMode c = o.getCloudRenderMode().getValue();
            o.getCloudRenderMode().setValue(c == CloudRenderMode.OFF ? CloudRenderMode.FAST
                    : (c == CloudRenderMode.FAST ? CloudRenderMode.FANCY : CloudRenderMode.OFF));
        }, null));
        gfx.add(new Toggle("Тени сущностей",
                () -> o.getEntityShadows().getValue(),
                v -> o.getEntityShadows().setValue(v)));
        tabs.add(gfx);

        // --- Меню ---
        List<W> menu = new ArrayList<>();
        menu.add(new Header("ВНЕШНИЙ ВИД"));
        menu.add(new Cycle("Цвет акцента",
                () -> ACCENT_NAMES[Math.floorMod(cfg.accent, ACCENTS.length)],
                () -> cfg.accent = (cfg.accent + 1) % ACCENTS.length,
                () -> ACCENTS[Math.floorMod(cfg.accent, ACCENTS.length)]));
        menu.add(new Toggle("Анимации", () -> cfg.animations, v -> cfg.animations = v));
        menu.add(new Slider("Скорость анимаций", 50, 200, 10, true,
                () -> Math.round(cfg.animSpeed * 100f),
                v -> cfg.animSpeed = v / 100f,
                v -> v + "%"));
        menu.add(new Header("ПРОЧЕЕ"));
        menu.add(new Button("Сбросить настройки меню", () -> {
            cfg.resetMenuSettings();
            showToast("Настройки меню сброшены");
        }));
        tabs.add(menu);
    }

    // ------------------------------------------------------------------ экран

    @Override
    protected void init() {
        pw = Math.max(270, Math.min(width - 12, 400));
        ph = Math.max(150, Math.min(height - 10, 250));
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        sbX = px + 8;
        sbY = py + 34;
        cX = px + 96;
        cY = py + 34;
        cW = pw - 104;
        cH = ph - 42;
    }

    @Override
    public boolean shouldPause() {
        return parent != null;
    }

    @Override
    public void close() {
        closing = true; // плавное закрытие, потом render() вернёт предыдущий экран
    }

    @Override
    public void removed() {
        FpsMenuConfig.save();
        mc.options.write();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min((now - lastNanos) / 1.0e9f, 0.05f);
        lastNanos = now;
        time += dt;

        open = approach(open, closing ? 0f : 1f, 14f, dt);
        if (closing && open < 0.03f) {
            mc.setScreen(parent);
            return;
        }
        alpha = Math.max(0f, Math.min(1f, open));
        curScale = 0.93f + 0.07f * alpha;

        int target = ACCENTS[Math.floorMod(cfg.accent, ACCENTS.length)];
        accR = approach(accR, (target >> 16) & 0xFF, 10f, dt);
        accG = approach(accG, (target >> 8) & 0xFF, 10f, dt);
        accB = approach(accB, target & 0xFF, 10f, dt);
        acc = ((int) accR << 16) | ((int) accG << 8) | (int) accB;

        double mx = mapX(mouseX);
        double my = mapY(mouseY);

        // лёгкое затемнение фона (мир остаётся виден — FOV и графику видно вживую)
        ctx.fill(0, 0, width, height, c(0x70000000));

        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(width / 2f, height / 2f, 0f);
        m.scale(curScale, curScale, 1f);
        m.translate(-width / 2f, -height / 2f, 0f);

        drawFrame(ctx, mx, my, dt);
        drawSidebar(ctx, mx, my, dt);
        drawContent(ctx, mx, my, dt);

        m.pop();
    }

    private void drawFrame(DrawContext ctx, double mx, double my, float dt) {
        rrect(ctx, px - 1, py - 1, px + pw + 1, py + ph + 1, 7, c(withA(acc, 0x60)));
        rrect(ctx, px, py, px + pw, py + ph, 6, c(0xFF11131A));

        textScaled(ctx, "FPS МЕНЮ", px + 12, py + 9, 1.4f, 0xFFFFFFFF);

        int fps = mc.getCurrentFps();
        String fs = fps + " FPS";
        int fcol = fps >= 100 ? 0xFF55FF7A : (fps >= 60 ? 0xFFFFE066 : 0xFFFF5C5C);
        text(ctx, fs, px + pw - 34 - textRenderer.getWidth(fs), py + 11, fcol);

        boolean closeHov = hit(mx, my, px + pw - 26, py + 7, 18, 18);
        closeHover = approach(closeHover, closeHov ? 1f : 0f, 18f, dt);
        rrect(ctx, px + pw - 26, py + 7, px + pw - 8, py + 25, 4,
                c(lerp(0xFF1F2330, 0xFFE0475B, closeHover)));
        text(ctx, "X", px + pw - 17 - textRenderer.getWidth("X") / 2, py + 12, 0xFFFFFFFF);

        // линия под заголовком с бегущим бликом
        int segs = 28;
        int lx = px + 10;
        int lw = pw - 20;
        float sh = cfg.animations ? ((time * 0.6f) % 1.4f) - 0.2f : -5f;
        for (int i = 0; i < segs; i++) {
            float t = i / (float) (segs - 1);
            float glow = Math.max(0f, 1f - Math.abs(t - sh) * 5f);
            float fade = 1f - t * 0.75f;
            int a = (int) (Math.min(1f, 0.55f * fade + glow) * 255f);
            int x1 = lx + lw * i / segs;
            int x2 = lx + lw * (i + 1) / segs;
            int col = glow > 0.3f ? lerp(acc, 0xFFFFFF, glow * 0.6f) : acc;
            ctx.fill(x1, py + 28, x2, py + 30, c(withA(col, a)));
        }
    }

    private void drawSidebar(DrawContext ctx, double mx, double my, float dt) {
        tabInd = approach(tabInd, tab * TAB_STEP, 16f, dt);
        int iy = sbY + Math.round(tabInd);
        rrect(ctx, sbX, iy, sbX + sbW, iy + 24, 5, c(withA(acc, 0x40)));
        ctx.fill(sbX, iy + 6, sbX + 2, iy + 18, c(0xFF000000 | acc));

        for (int i = 0; i < TAB_NAMES.length; i++) {
            int ty = sbY + i * TAB_STEP;
            boolean hov = hit(mx, my, sbX, ty, sbW, 24);
            tabHover[i] = approach(tabHover[i], hov ? 1f : 0f, 18f, dt);
            if (i != tab) {
                rrect(ctx, sbX, ty, sbX + sbW, ty + 24, 5, c(withA(0xFFFFFF, Math.round(18 * tabHover[i]))));
            }
            int col = lerp(0xFF8E97A8, 0xFFFFFFFF, i == tab ? 1f : tabHover[i] * 0.8f);
            int shift = i == tab ? 0 : Math.round(2 * tabHover[i]);
            text(ctx, TAB_NAMES[i], sbX + 12 + shift, ty + 8, col);
        }
        if (ph >= 190) {
            text(ctx, "P - закрыть", sbX + 4, py + ph - 16, 0xFF5E6676);
        }
    }

    private void drawContent(DrawContext ctx, double mx, double my, float dt) {
        List<W> list = tabs.get(tab);
        tabFade = approach(tabFade, 1f, 14f, dt);

        int total = 0;
        for (W w : list) total += w.h + 4;
        maxScroll = Math.max(0f, (float) (total - cH + 4));
        scrollTarget = Math.max(0f, Math.min(maxScroll, scrollTarget));
        scroll = dragScroll ? scrollTarget : approach(scroll, scrollTarget, 18f, dt);

        float saved = alpha;
        alpha = saved * tabFade;
        int off = Math.round((1f - tabFade) * 14f);

        ctx.enableScissor(cX - 2, cY, cX + cW + 2, cY + cH);
        float yy = cY + 2 - scroll;
        for (W w : list) {
            w.rx = cX + off;
            w.ry = Math.round(yy);
            w.rw = cW - 8;
            if (w.ry + w.h >= cY && w.ry <= cY + cH) {
                boolean hov = inContent(mx, my) && hit(mx, my, w.rx, w.ry, w.rw, w.h);
                w.draw(ctx, hov, dt);
            }
            yy += w.h + 4;
        }
        ctx.disableScissor();

        // полоса прокрутки
        if (maxScroll > 0f) {
            int trackH = cH - 4;
            int thumbH = Math.max(14, Math.round(trackH * (cH / (float) total)));
            int thumbY = cY + 2 + Math.round((trackH - thumbH) * (scroll / maxScroll));
            ctx.fill(cX + cW - 3, cY + 2, cX + cW - 1, cY + 2 + trackH, c(0x30FFFFFF));
            rrect(ctx, cX + cW - 3, thumbY, cX + cW - 1, thumbY + thumbH, 1, c(withA(acc, 0xB0)));
        }
        alpha = saved;

        // всплывающее уведомление
        if (toastT > 0f) {
            toastT -= dt;
            float a = Math.min(1f, Math.max(0f, toastT) * 2f);
            float saved2 = alpha;
            alpha = alpha * a;
            int tw = textRenderer.getWidth(toast) + 16;
            int tx = cX + (cW - tw) / 2;
            int ty = cY + cH - 22;
            rrect(ctx, tx, ty, tx + tw, ty + 16, 5, c(lerp(0xFF000000 | acc, 0xFF0B0D12, 0.45f)));
            text(ctx, toast, tx + 8, ty + 4, 0xFFFFFFFF);
            alpha = saved2;
        }
    }

    // ------------------------------------------------------------------ ввод

    private double mapX(double x) {
        return (x - width / 2.0) / curScale + width / 2.0;
    }

    private double mapY(double y) {
        return (y - height / 2.0) / curScale + height / 2.0;
    }

    private static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean inContent(double mx, double my) {
        return hit(mx, my, cX, cY, cW, cH);
    }

    private W widgetAt(double mx, double my) {
        if (!inContent(mx, my)) return null;
        for (W w : tabs.get(tab)) {
            if (hit(mx, my, w.rx, w.ry, w.rw, w.h)) return w;
        }
        return null;
    }

    private void selectTab(int i) {
        if (i == tab) return;
        tab = i;
        scroll = 0f;
        scrollTarget = 0f;
        tabFade = 0f;
        pressed = null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing || button != 0) return true;
        double mx = mapX(mouseX);
        double my = mapY(mouseY);

        if (hit(mx, my, px + pw - 26, py + 7, 18, 18)) {
            close();
            return true;
        }
        for (int i = 0; i < TAB_NAMES.length; i++) {
            if (hit(mx, my, sbX, sbY + i * TAB_STEP, sbW, 24)) {
                selectTab(i);
                return true;
            }
        }
        pressed = null;
        pressInContent = false;
        dragScroll = false;
        sliderActive = false;
        dragDist = 0;
        if (inContent(mx, my)) {
            pressInContent = true;
            pressed = widgetAt(mx, my);
            // слайдер "хватается" только за нижнюю часть (полоску); верх строки — для прокрутки свайпом
            sliderActive = pressed != null && pressed.slider() && my >= pressed.ry + 12;
            if (sliderActive) pressed.press(mx);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (closing || !pressInContent) return true;
        if (sliderActive && pressed != null) {
            pressed.drag(mapX(mouseX));
            return true;
        }
        dragDist += Math.abs(deltaX) + Math.abs(deltaY);
        if (dragDist > 6) dragScroll = true;
        if (dragScroll) {
            scrollTarget = Math.max(0f, Math.min(maxScroll, scrollTarget - (float) (deltaY / curScale)));
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        double mx = mapX(mouseX);
        double my = mapY(mouseY);
        if (pressed != null) {
            if (sliderActive) {
                pressed.release();
            } else if (!dragScroll && pressed == widgetAt(mx, my)) {
                pressed.activate();
            }
        }
        pressed = null;
        sliderActive = false;
        pressInContent = false;
        dragScroll = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollTarget = Math.max(0f, Math.min(maxScroll, scrollTarget - (float) verticalAmount * 28f));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (FpsMenuClient.OPEN_KEY != null && FpsMenuClient.OPEN_KEY.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ рисование / утилиты

    private float approach(float cur, float target, float rate, float dt) {
        if (!cfg.animations) return target;
        float k = 1f - (float) Math.exp(-rate * cfg.animSpeed * dt);
        float v = cur + (target - cur) * k;
        return Math.abs(target - v) < 0.001f ? target : v;
    }

    /** Применяет общую прозрачность (анимация появления) к цвету ARGB. */
    private int c(int argb) {
        int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static int withA(int rgb, int a) {
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static int lerp(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int s = 24; s >= 0; s -= 8) {
            int a = (c1 >>> s) & 0xFF;
            int b = (c2 >>> s) & 0xFF;
            out |= (Math.round(a + (b - a) * t) & 0xFF) << s;
        }
        return out;
    }

    private void text(DrawContext ctx, String s, int x, int y, int argb) {
        int col = c(argb);
        if ((col >>> 24) < 8) return; // при очень малой альфе ванильный шрифт рисует непрозрачно
        ctx.drawText(textRenderer, s, x, y, col, true);
    }

    private void textScaled(DrawContext ctx, String s, int x, int y, float sc, int argb) {
        int col = c(argb);
        if ((col >>> 24) < 8) return;
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate((float) x, (float) y, 0f);
        m.scale(sc, sc, 1f);
        ctx.drawText(textRenderer, s, 0, 0, col, true);
        m.pop();
    }

    /** Скруглённый прямоугольник из горизонтальных полос. */
    private void rrect(DrawContext ctx, int x1, int y1, int x2, int y2, int r, int color) {
        r = Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        if (r <= 0) {
            ctx.fill(x1, y1, x2, y2, color);
            return;
        }
        ctx.fill(x1, y1 + r, x2, y2 - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            ctx.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
            ctx.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
        }
    }

    // ------------------------------------------------------------------ виджеты

    private abstract class W {
        int h = 24;
        int rx, ry, rw;
        float hover;

        boolean slider() { return false; }
        void press(double mx) { }
        void drag(double mx) { }
        void release() { }
        void activate() { }

        abstract void draw(DrawContext ctx, boolean hovered, float dt);

        void bg(DrawContext ctx, boolean hovered, float dt) {
            hover = approach(hover, hovered ? 1f : 0f, 18f, dt);
            rrect(ctx, rx, ry, rx + rw, ry + h, 4, c(lerp(0xFF1A1D27, 0xFF252A3A, hover)));
        }
    }

    private class Header extends W {
        final String t;

        Header(String t) {
            this.t = t;
            this.h = 14;
        }

        @Override
        void draw(DrawContext ctx, boolean hovered, float dt) {
            textScaled(ctx, t, rx + 2, ry + 4, 0.8f, 0xFF000000 | acc);
        }
    }

    private class Toggle extends W {
        final String label;
        final BooleanSupplier get;
        final Consumer<Boolean> set;
        float k = -1f;

        Toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
            this.label = label;
            this.get = get;
            this.set = set;
        }

        @Override
        void draw(DrawContext ctx, boolean hovered, float dt) {
            bg(ctx, hovered, dt);
            boolean on = get.getAsBoolean();
            if (k < 0f) k = on ? 1f : 0f;
            k = approach(k, on ? 1f : 0f, 16f, dt);
            text(ctx, label, rx + 8, ry + (h - 8) / 2, 0xFFE8ECF4);
            int tx = rx + rw - 38;
            int ty = ry + (h - 14) / 2;
            rrect(ctx, tx, ty, tx + 30, ty + 14, 7, c(lerp(0xFF3A3F4F, 0xFF000000 | acc, k)));
            int kx = tx + 2 + Math.round(k * 16f);
            rrect(ctx, kx, ty + 2, kx + 10, ty + 12, 5, c(0xFFFFFFFF));
        }

        @Override
        void activate() {
            set.accept(!get.getAsBoolean());
        }
    }

    private class Slider extends W {
        final String label;
        final int min, max, step;
        final boolean live;
        final IntSupplier get;
        final IntConsumer set;
        final IntFunction<String> fmt;
        boolean dragging;
        int dragValue;
        float shown = -1f;
        float knob = 4f;

        Slider(String label, int min, int max, int step, boolean live,
               IntSupplier get, IntConsumer set, IntFunction<String> fmt) {
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.live = live;
            this.get = get;
            this.set = set;
            this.fmt = fmt;
            this.h = 32;
        }

        int value() {
            return dragging ? dragValue : Math.max(min, Math.min(max, get.getAsInt()));
        }

        @Override
        boolean slider() { return true; }

        void apply(double mx) {
            double f = (mx - (rx + 8)) / (double) (rw - 16);
            f = Math.max(0.0, Math.min(1.0, f));
            int steps = (int) Math.round(f * (max - min) / step);
            int v = Math.max(min, Math.min(max, min + steps * step));
            dragValue = v;
            if (live) set.accept(v);
        }

        @Override
        void press(double mx) {
            dragValue = value();
            dragging = true;
            apply(mx);
        }

        @Override
        void drag(double mx) {
            if (dragging) apply(mx);
        }

        @Override
        void release() {
            if (dragging) {
                if (!live) set.accept(dragValue);
                dragging = false;
            }
        }

        @Override
        void draw(DrawContext ctx, boolean hovered, float dt) {
            bg(ctx, hovered || dragging, dt);
            int v = value();
            float frac = (v - min) / (float) (max - min);
            if (shown < 0f) shown = frac;
            shown = dragging ? frac : approach(shown, frac, 20f, dt);
            knob = approach(knob, dragging ? 5f : 4f, 20f, dt);

            text(ctx, label, rx + 8, ry + 6, 0xFFE8ECF4);
            String s = fmt.apply(v);
            text(ctx, s, rx + rw - 8 - textRenderer.getWidth(s), ry + 6, 0xFF000000 | acc);

            int tx = rx + 8;
            int tw = rw - 16;
            int ty = ry + 21;
            rrect(ctx, tx, ty, tx + tw, ty + 4, 2, c(0xFF2C3242));
            int fw = Math.round(tw * shown);
            if (fw > 0) rrect(ctx, tx, ty, tx + Math.max(fw, 4), ty + 4, 2, c(0xFF000000 | acc));
            int kr = Math.round(knob);
            int kx = tx + fw;
            rrect(ctx, kx - kr, ty + 2 - kr, kx + kr, ty + 2 + kr, kr, c(0xFFFFFFFF));
        }
    }

    private class Cycle extends W {
        final String label;
        final Supplier<String> val;
        final Runnable next;
        final IntSupplier swatch;

        Cycle(String label, Supplier<String> val, Runnable next, IntSupplier swatch) {
            this.label = label;
            this.val = val;
            this.next = next;
            this.swatch = swatch;
        }

        @Override
        void draw(DrawContext ctx, boolean hovered, float dt) {
            bg(ctx, hovered, dt);
            text(ctx, label, rx + 8, ry + (h - 8) / 2, 0xFFE8ECF4);
            String full = "< " + val.get() + " >";
            int right = rx + rw - 8;
            int fx = right - textRenderer.getWidth(full);
            text(ctx, full, fx, ry + (h - 8) / 2, lerp(0xFF9AA3B2, 0xFF000000 | acc, hover));
            if (swatch != null) {
                rrect(ctx, fx - 14, ry + (h - 8) / 2, fx - 6, ry + (h - 8) / 2 + 8, 2,
                        c(0xFF000000 | swatch.getAsInt()));
            }
        }

        @Override
        void activate() {
            next.run();
        }
    }

    private class Button extends W {
        final String label;
        final Runnable action;
        float flash;

        Button(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }

        @Override
        void draw(DrawContext ctx, boolean hovered, float dt) {
            hover = approach(hover, hovered ? 1f : 0f, 18f, dt);
            flash = approach(flash, 0f, 6f, dt);
            int col = lerp(0xFF1B1F2B, 0xFF000000 | acc, 0.18f + 0.22f * hover + 0.6f * flash);
            rrect(ctx, rx, ry, rx + rw, ry + h, 4, c(col));
            int tw = textRenderer.getWidth(label);
            text(ctx, label, rx + (rw - tw) / 2, ry + (h - 8) / 2, 0xFFFFFFFF);
        }

        @Override
        void activate() {
            flash = 1f;
            action.run();
        }
    }
}
