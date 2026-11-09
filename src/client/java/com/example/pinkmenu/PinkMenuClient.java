package com.example.pinkmenu;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

public class PinkMenuClient implements ClientModInitializer {

    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("pinkmenu", "main"));

    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        Config.load();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pinkmenu.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new MenuScreen());
                }
            }
        });
    }

    // ---------------- Einstellungen (werden gespeichert) ----------------
    static class Config {
        private static final Properties props = new Properties();

        private static Path file() {
            return FabricLoader.getInstance().getConfigDir().resolve("pinkmenu.properties");
        }

        static boolean getBool(String key) {
            return Boolean.parseBoolean(props.getProperty(key, "false"));
        }

        static void setBool(String key, boolean value) {
            props.setProperty(key, String.valueOf(value));
            save();
        }

        static int getInt(String key) {
            try {
                return Integer.parseInt(props.getProperty(key, "50"));
            } catch (NumberFormatException e) {
                return 50;
            }
        }

        static void setInt(String key, int value) {
            props.setProperty(key, String.valueOf(value));
            save();
        }

        static void load() {
            Path f = file();
            if (!Files.exists(f)) return;
            try (InputStream in = Files.newInputStream(f)) {
                props.load(in);
            } catch (IOException ignored) {
            }
        }

        static void save() {
            try (OutputStream out = Files.newOutputStream(file())) {
                props.store(out, "Pink Menu");
            } catch (IOException ignored) {
            }
        }
    }

    // ---------------- Zeichen-Hilfe: Flaeche mit duenner Aussenlinie ----------------
    static void drawBox(DrawContext c, int x, int y, int w, int h, int fill, int line) {
        c.fill(x, y, x + w, y + h, fill);
        c.fill(x, y, x + w, y + 1, line);
        c.fill(x, y + h - 1, x + w, y + h, line);
        c.fill(x, y, x + 1, y + h, line);
        c.fill(x + w - 1, y, x + w, y + h, line);
    }

    // ---------------- Flacher, durchsichtiger Button ----------------
    // Achtung: In ButtonWidget gibt es eine innere Klasse "Text". Deshalb hier immer
    // net.minecraft.text.Text voll ausschreiben.
    static class FlatButton extends ButtonWidget {
        boolean selected = false;

        FlatButton(int x, int y, int width, int height, net.minecraft.text.Text message, ButtonWidget.PressAction onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        }

        // In 1.21.11 zeichnet der Button seinen Inhalt ueber diese Methode.
        public void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

            int fill = selected ? 0x99FF6FE0 : (hover ? 0x66FF6FE0 : 0x4D140632);
            int line = (selected || hover) ? 0xFFFFB0F0 : 0xB3FF6FE0;
            drawBox(context, x, y, w, h, fill, line);

            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            net.minecraft.text.Text msg = getMessage();
            context.drawTextWithShadow(tr, msg, x + (w - tr.getWidth(msg)) / 2, y + (h - 8) / 2,
                    selected ? 0xFFFFFFFF : 0xFFFFE6FA);
        }
    }
    static class FlatSlider extends SliderWidget {
        private final String key;

        FlatSlider(int x, int y, int width, int height, String key) {
            super(x, y, width, height, Text.empty(), Config.getInt(key) / 100.0);
            this.key = key;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Stärke: " + (int) Math.round(this.value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            Config.setInt(key, (int) Math.round(this.value * 100));
        }

        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

            drawBox(context, x, y, w, h,
                    hover ? 0x66FF6FE0 : 0x4D140632,
                    hover ? 0xFFFFB0F0 : 0xB3FF6FE0);

            // gefuellter Teil des Reglers
            int filled = (int) ((w - 2) * this.value);
            context.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, 0x80FF6FE0);

            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            Text msg = getMessage();
            context.drawTextWithShadow(tr, msg, x + (w - tr.getWidth(msg)) / 2, y + (h - 8) / 2, 0xFFFFE6FA);
        }
    }

    // ---------------- Das Menue (ein Fenster, etwa Inventar-Groesse) ----------------
    static class MenuScreen extends Screen {

        private static final int WIN_W = 176;
        private static final int WIN_H = 166;
        private static final String[] TABS = {"PVP", "Effects", "Skins"};
        private int tab = 0;

        MenuScreen() {
            super(Text.literal("Pink Menu"));
        }

        private int left() {
            return (this.width - WIN_W) / 2;
        }

        private int top() {
            return (this.height - WIN_H) / 2;
        }

        @Override
        protected void init() {
            int left = left();
            int top = top();
            int x = left + 10;
            int y = top + 34;
            int bw = WIN_W - 20;
            String prefix = TABS[tab].toLowerCase(Locale.ROOT);

            // Inhalt des aktiven Reiters
            for (int i = 1; i <= 3; i++) {
                addDrawableChild(toggle("Funktion " + i, x, y + (i - 1) * 24, bw, prefix + ".f" + i));
            }
            addDrawableChild(new FlatSlider(x, y + 72, bw, 20, prefix + ".strength"));
            addDrawableChild(new FlatButton(x, y + 96, bw, 20, Text.literal("Schließen"), b -> this.close()));

            // Reiter rechts am Fensterrand
            int tabX = left + WIN_W + 4;
            int tabY = top + 10;
            for (int t = 0; t < TABS.length; t++) {
                final int index = t;
                FlatButton tabButton = new FlatButton(tabX, tabY + t * 26, 52, 22, Text.literal(TABS[t]), b -> {
                    this.tab = index;
                    this.clearAndInit();
                });
                tabButton.selected = (t == tab);
                addDrawableChild(tabButton);
            }
        }

        private FlatButton toggle(String name, int x, int y, int width, String key) {
            return new FlatButton(x, y, width, 20, label(name, Config.getBool(key)), b -> {
                boolean now = !Config.getBool(key);
                Config.setBool(key, now);
                b.setMessage(label(name, now));
            });
        }

        private static Text label(String name, boolean on) {
            return Text.literal(name + ": " + (on ? "AN" : "AUS"));
        }

        // Kein Vollbild-Hintergrund: die Spielwelt bleibt hinter dem Fenster sichtbar.
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            int left = left();
            int top = top();
            int right = left + WIN_W;
            int bottom = top + WIN_H;
            int g1 = top + WIN_H * 35 / 100;
            int g2 = top + WIN_H * 65 / 100;

            // Verlauf im Fenster: dunkles Indigo -> Lila -> leuchtendes Pink
            context.fillGradient(left, top, right, g1, 0xFF14083F, 0xFF5B1A9E);
            context.fillGradient(left, g1, right, g2, 0xFF5B1A9E, 0xFFD43FD0);
            context.fillGradient(left, g2, right, bottom, 0xFFD43FD0, 0xFFA81E6C);

            // duenne Aussenlinie
            int line = 0xFFFF6FE0;
            context.fill(left, top, right, top + 1, line);
            context.fill(left, bottom - 1, right, bottom, line);
            context.fill(left, top, left + 1, bottom, line);
            context.fill(right - 1, top, right, bottom, line);

            // Balken, der den aktiven Reiter mit dem Fenster verbindet
            int barY = top + 10 + tab * 26;
            context.fill(right, barY, right + 4, barY + 22, line);

            // Titel und Name des aktiven Reiters
            int titleX = left + (WIN_W - this.textRenderer.getWidth(this.title)) / 2;
            context.drawTextWithShadow(this.textRenderer, this.title, titleX, top + 7, 0xFFFFC8F4);
            Text sub = Text.literal(TABS[tab]);
            int subX = left + (WIN_W - this.textRenderer.getWidth(sub)) / 2;
            context.drawTextWithShadow(this.textRenderer, sub, subX, top + 19, 0xFFFF8FE8);

            super.render(context, mouseX, mouseY, delta);
        }
    }
}
