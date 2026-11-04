package dev.stella.client.gui;

import dev.stella.client.gui.theme.ThemeManager;
import dev.stella.client.render.Render2D;
import dev.stella.client.render.StellaFont;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Gallery of your screenshots folder: thumbnails, newest first. Click to open, Shift + right-click to
 * delete, buttons to open the folder and change page.
 */
public class ScreenshotGallery extends StellaScreen {
    private static final int COLS = 4, ROWS = 3, PER_PAGE = COLS * ROWS;
    private final List<Path> files = new ArrayList<>();
    private final List<Identifier> ids = new ArrayList<>();
    private final List<int[]> sizes = new ArrayList<>();
    private final List<NativeImageBackedTexture> textures = new ArrayList<>();
    private int page;
    private String status = "";

    public ScreenshotGallery() { super(Text.literal("Screenshots")); }

    private Path dir() { return client.runDirectory.toPath().resolve("screenshots"); }

    @Override
    protected void init() {
        files.clear();
        try {
            if (Files.isDirectory(dir())) try (var s = Files.list(dir())) {
                s.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png"))
                        .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed()).forEach(files::add);
            }
        } catch (Exception e) { status = "Could not read the screenshots folder"; }
        loadPage();
    }

    private void freeTextures() {
        for (int i = 0; i < ids.size(); i++) client.getTextureManager().destroyTexture(ids.get(i));
        ids.clear(); sizes.clear(); textures.clear();
    }

    private void loadPage() {
        freeTextures();
        for (int i = page * PER_PAGE; i < Math.min(files.size(), (page + 1) * PER_PAGE); i++) {
            try (InputStream in = Files.newInputStream(files.get(i))) {
                NativeImage img = NativeImage.read(in);
                Identifier id = Identifier.of("stella", "gallery/" + (i - page * PER_PAGE));
                final int idx = i;
                NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "stella_gallery_" + idx, img);
                client.getTextureManager().registerTexture(id, tex);
                ids.add(id); sizes.add(new int[]{img.getWidth(), img.getHeight()}); textures.add(tex);
            } catch (Exception e) {
                ids.add(null); sizes.add(new int[]{1, 1}); textures.add(null);
            }
        }
    }

    @Override public void removed() { freeTextures(); }

    private int[] cell(int k) {
        int gw = Math.min(width - 40, 760), cw = (gw - (COLS - 1) * 10) / COLS, ch = cw * 9 / 16;
        int x0 = (width - gw) / 2, y0 = 44;
        return new int[]{x0 + (k % COLS) * (cw + 10), y0 + (k / COLS) * (ch + 22), cw, ch};
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fillGradient(0, 0, width, height, 0xE00A0612, 0xF0140A22);
        int accent = ThemeManager.get().accent();
        String title = "SCREENSHOTS";
        StellaFont.draw(ctx, title, (width - StellaFont.width(title)) / 2, 14, accent, true);
        String sub = files.size() + " screenshots  •  click to open  •  shift + right-click to delete  •  page " + (page + 1) + "/" + Math.max(1, pages());
        Render2D.textScaled(ctx, sub, (width - StellaFont.width(sub) * 0.75f) / 2, 27, 0.75f, 0xFF9A93A6, true);
        for (int k = 0; k < ids.size(); k++) {
            int[] c = cell(k);
            boolean hover = mx >= c[0] && mx < c[0] + c[2] && my >= c[1] && my < c[1] + c[3];
            Render2D.roundRect(ctx, c[0] - 2, c[1] - 2, c[2] + 4, c[3] + 4, 4, hover ? accent : 0xFF2A2234);
            if (ids.get(k) != null) {
                int[] s = sizes.get(k);
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ids.get(k), c[0], c[1], 0f, 0f, c[2], c[3], s[0], s[1], s[0], s[1]);
            } else ctx.fill(c[0], c[1], c[0] + c[2], c[1] + c[3], 0xFF1A1424);
            String name = files.get(page * PER_PAGE + k).getFileName().toString();
            Render2D.textScaled(ctx, name, c[0], c[1] + c[3] + 4, 0.6f, hover ? 0xFFF3F0F7 : 0xFF9A93A6, true);
        }
        if (files.isEmpty()) {
            String e = "No screenshots yet - press F2 in game";
            StellaFont.draw(ctx, e, (width - StellaFont.width(e)) / 2, height / 2, 0xFF9A93A6, true);
        }
        button(ctx, "< Prev", width / 2 - 150, height - 26, mx, my);
        button(ctx, "Open Folder", width / 2 - 45, height - 26, mx, my);
        button(ctx, "Next >", width / 2 + 60, height - 26, mx, my);
        if (!status.isEmpty()) Render2D.textScaled(ctx, status, 8, height - 12, 0.7f, 0xFFFFC94D, true);
    }

    private void button(DrawContext ctx, String s, int x, int y, int mx, int my) {
        boolean h = mx >= x && mx < x + 90 && my >= y && my < y + 18;
        Render2D.roundRect(ctx, x, y, 90, 18, 4, h ? ThemeManager.get().accent() : 0xFF241C30);
        StellaFont.draw(ctx, s, x + (90 - StellaFont.width(s)) / 2, y + 5, 0xFFF3F0F7, true);
    }

    private int pages() { return (files.size() + PER_PAGE - 1) / PER_PAGE; }

    @Override
    protected boolean onMouseClicked(double mx, double my, int button) {
        int y = height - 26;
        if (my >= y && my < y + 18) {
            if (mx >= width / 2 - 150 && mx < width / 2 - 60 && page > 0) { page--; loadPage(); return true; }
            if (mx >= width / 2 + 60 && mx < width / 2 + 150 && page < pages() - 1) { page++; loadPage(); return true; }
            if (mx >= width / 2 - 45 && mx < width / 2 + 45) {
                try { Files.createDirectories(dir()); Util.getOperatingSystem().open(dir().toFile()); } catch (Exception ignored) { }
                return true;
            }
        }
        for (int k = 0; k < ids.size(); k++) {
            int[] c = cell(k);
            if (mx < c[0] || mx >= c[0] + c[2] || my < c[1] || my >= c[1] + c[3]) continue;
            Path f = files.get(page * PER_PAGE + k);
            boolean shift = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
            if (button == 1 && shift) {
                try { Files.deleteIfExists(f); status = "Deleted " + f.getFileName(); } catch (Exception e) { status = "Could not delete"; }
                init();
            } else if (button == 0) Util.getOperatingSystem().open(f.toFile());
            return true;
        }
        return false;
    }

    @Override public boolean shouldPause() { return false; }
}
