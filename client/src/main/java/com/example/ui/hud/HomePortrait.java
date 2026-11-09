package com.example.ui.hud;

import com.example.ui.WindowChrome;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;

/**
 * 全身圖畫在「下一個任務」卡片後面，手指再畫一次蓋在卡片前面。
 * 兩次用同一個矩形。頭部若超過標題列，也從這個前景層補上。
 */
public final class HomePortrait implements WindowChrome.FramePortrait {

    private final AnimeShell shell;
    private final HomeCharacterArt art;
    private final Rectangle bust = new Rectangle();
    /** 軌道前景等比例繪製後的實際矩形（頂右對齊）。 */
    private final Rectangle orbitalDrawn = new Rectangle();
    /** 視窗控制鈕區域：角色不蓋住，點擊仍可穿透。 */
    private final Rectangle controlHole = new Rectangle();
    private CharacterTheme theme = CharacterTheme.SWEATER;
    /** 跨框前景縮放快取，避免每次整窗重繪都雙線性縮放大幅 PNG。 */
    private java.awt.image.BufferedImage orbitalOverflowLayer;
    private int orbitalOverflowW = -1;
    private int orbitalOverflowH = -1;

    public HomePortrait(AnimeShell shell) {
        this.shell = shell;
        this.art = HomeCharacterArt.load();
    }

    public void setTheme(CharacterTheme theme) {
        this.theme = theme == null ? CharacterTheme.SWEATER : theme;
        orbitalOverflowLayer = null;
        orbitalOverflowW = -1;
        orbitalOverflowH = -1;
    }

    @Override
    public Insets overflow(int width, int height, boolean maximized) {
        if (theme == CharacterTheme.ORBITAL) {
            if (!OrbitalAssets.shared().hasOverflow()) {
                return new Insets(0, 0, 0, 0);
            }
            // 外層尺寸 → 留白，需與 panelOverflow 對齊，否則換頁會改視窗大小閃爍。
            return OrbitalFrame.forCanvas(width, height, maximized);
        }
        // 留白不跟著換頁收掉。收掉會改透明視窗的大小，macOS 會先閃一幀空白。
        return HomeCharacterLayout.overflow(width, height, maximized, art != null);
    }

    @Override
    public Insets panelOverflow(int panelWidth, int panelHeight) {
        if (theme != CharacterTheme.ORBITAL) {
            return null;
        }
        if (!OrbitalAssets.shared().hasOverflow()) {
            // 只有窗內預覽時不要沿用毛衣女孩留白。
            return new Insets(0, 0, 0, 0);
        }
        return OrbitalFrame.forPanel(panelWidth, panelHeight);
    }

    @Override
    public void paintBehind(Graphics2D g, JComponent canvas, Rectangle panel) {
        place(canvas, panel);
    }

    @Override
    public void paintInFront(Graphics2D g, JComponent canvas, Rectangle panel) {
        if (theme == CharacterTheme.ORBITAL) {
            // 只補殼層裁掉的跨框頭髮／右緣；軀幹已在卡片後面。
            drawOrbitalOverflow(g, canvas, panel);
            return;
        }
        if (bust.width <= 0 || art == null) {
            return;
        }
        drawHeadAndArms(g, canvas);
        drawFingers(g);
    }

    @Override
    public boolean opaqueAt(JComponent canvas, int x, int y) {
        if (shell == null || !shell.isHoldingHome() || !bust.contains(x, y)) {
            return false;
        }
        if (theme == CharacterTheme.ORBITAL) {
            return opaqueForeground(x, y);
        }
        if (art == null) {
            return false;
        }
        int imageX = (x - bust.x) * art.bust.getWidth() / Math.max(1, bust.width);
        int imageY = (y - bust.y) * art.bust.getHeight() / Math.max(1, bust.height);
        return art.opaqueBust(imageX, imageY);
    }

    @Override
    public void pressed() {
        if (shell != null && shell.isHoldingHome()) {
            shell.greet();
        }
    }

    private void place(JComponent canvas, Rectangle panel) {
        bust.setBounds(0, 0, 0, 0);
        orbitalDrawn.setBounds(0, 0, 0, 0);
        controlHole.setBounds(0, 0, 0, 0);
        if (shell == null || canvas == null || panel == null || !shell.isHoldingHome()) {
            if (shell != null) {
                shell.releaseHomeFigure();
            }
            return;
        }
        if (theme == CharacterTheme.ORBITAL) {
            placeOrbital(canvas, panel);
            return;
        }
        if (art == null) {
            shell.releaseHomeFigure();
            return;
        }
        boolean maximized = WindowChrome.isMaximizedFrame(canvas);
        Rectangle controls = WindowChrome.chromeControls(canvas);
        int titleH = WindowChrome.titleBarHeight(canvas);
        Dimension bodySize = new Dimension(art.bust.getWidth(), art.bust.getHeight());
        Rectangle canvasBounds = new Rectangle(0, 0, canvas.getWidth(), canvas.getHeight());
        shell.prepareHomeLayout();
        Rectangle stage = stageOnCanvas(canvas);
        HomeCharacterLayout.Box box = HomeCharacterLayout.place(
                canvasBounds, panel, titleH, stage, controls, maximized, bodySize, bodySize);
        bust.setBounds(box.bust);
        if (!shell.isShowing() || bust.height <= 0) {
            return;
        }
        try {
            Point origin = SwingUtilities.convertPoint(shell, 0, 0, canvas);
            Rectangle figure = new Rectangle(
                    box.bust.x - origin.x, box.bust.y - origin.y, box.bust.width, box.bust.height);
            Rectangle card = new Rectangle(
                    box.card.x - origin.x, box.card.y - origin.y, box.card.width, box.card.height);
            shell.presentHomeFigure(figure, card, box.textRight - origin.x, art.bust);
            shell.dropHomeBubbleBelow(card.y + card.height + 8);
        } catch (IllegalArgumentException ex) {
            shell.presentHomeFigure(null, null, 0, null);
        }
    }

    private Rectangle stageOnCanvas(JComponent canvas) {
        Rectangle stage = shell.homeStageBounds();
        if (stage.width < 80 || !shell.isShowing()) {
            return null;
        }
        try {
            Point origin = SwingUtilities.convertPoint(shell, stage.x, stage.y, canvas);
            return new Rectangle(origin.x, origin.y, stage.width, stage.height);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * 軌道版角色等比例頂右對齊。卡片對齊腰部；頭髮連續蓋過標題列，控制鈕留洞。
     */
    private void placeOrbital(JComponent canvas, Rectangle panel) {
        shell.releaseHomeFigure();
        Rectangle controls = WindowChrome.chromeControls(canvas);
        if (controls != null && controls.width > 0) {
            controlHole.setBounds(controls.x - 6, controls.y - 2, controls.width + 12, controls.height + 6);
        }
        if (!OrbitalAssets.shared().hasOverflow()) {
            return;
        }
        shell.prepareHomeLayout();
        Rectangle stage = stageOnCanvas(canvas);
        boolean maximized = WindowChrome.isMaximizedFrame(canvas);
        Rectangle dest = OrbitalFrame.foregroundDest(stage, maximized);
        java.awt.image.BufferedImage image = OrbitalAssets.shared().foreground();
        if (dest.width <= 0 || image == null) {
            return;
        }
        Rectangle fitted = OrbitalAssets.fitTopRight(image, dest);
        bust.setBounds(fitted);
        orbitalDrawn.setBounds(fitted);
        try {
            Point origin = SwingUtilities.convertPoint(shell, 0, 0, canvas);
            shell.presentOrbitalFigure(new Rectangle(
                    fitted.x - origin.x,
                    fitted.y - origin.y,
                    fitted.width,
                    fitted.height));
        } catch (IllegalArgumentException ex) {
            shell.presentOrbitalFigure(null);
        }
    }

    /**
     * 跨框層：頭髮蓋過標題列、手套貼右框外；避開視窗控制鈕。
     * 殼層頂緣以下已由 AnimeShell 畫過，這裡往下多畫一點避免接縫。
     */
    private void drawOrbitalOverflow(Graphics2D g, JComponent canvas, Rectangle panel) {
        java.awt.image.BufferedImage image = OrbitalAssets.shared().foreground();
        if (image == null || orbitalDrawn.width <= 0 || panel == null) {
            return;
        }
        int shellTopOnCanvas;
        try {
            Point shellOrigin = SwingUtilities.convertPoint(shell, 0, 0, canvas);
            shellTopOnCanvas = shellOrigin.y;
        } catch (IllegalArgumentException ex) {
            shellTopOnCanvas = panel.y + Math.max(1, WindowChrome.titleBarHeight(canvas));
        }
        // 標題列＋殼層上緣重疊帶，讓額前髮連續。
        int seam = shellTopOnCanvas + 18;
        Shape previous = g.getClip();
        Area area = new Area(orbitalDrawn);
        if (controlHole.width > 0) {
            area.subtract(new Area(controlHole));
        }
        // 只要殼層之上（含標題）與右框外；軀幹留給殼層＋卡片。
        Area keep = new Area(new Rectangle(
                orbitalDrawn.x, orbitalDrawn.y, orbitalDrawn.width, Math.max(0, seam - orbitalDrawn.y)));
        if (panel.width > 0) {
            int rightEdge = panel.x + panel.width;
            if (orbitalDrawn.x + orbitalDrawn.width > rightEdge) {
                keep.add(new Area(new Rectangle(
                        rightEdge,
                        Math.max(orbitalDrawn.y, seam),
                        orbitalDrawn.x + orbitalDrawn.width - rightEdge,
                        Math.max(0, orbitalDrawn.y + orbitalDrawn.height - seam))));
            }
        }
        area.intersect(keep);
        // 卡片區絕對不畫前景，避免跨框層蓋回腰線。
        Rectangle reminder = reminderOnCanvas(canvas);
        if (reminder != null && reminder.width > 0) {
            area.subtract(new Area(reminder));
        }
        g.setClip(area);
        java.awt.image.BufferedImage scaled = scaledOrbitalOverflow(image);
        if (scaled != null) {
            g.drawImage(scaled, orbitalDrawn.x, orbitalDrawn.y, null);
        } else {
            OrbitalAssets.drawFitted(g, image, orbitalDrawn);
        }
        g.setClip(previous);
    }

    private java.awt.image.BufferedImage scaledOrbitalOverflow(java.awt.image.BufferedImage image) {
        if (image == null || orbitalDrawn.width < 2 || orbitalDrawn.height < 2) {
            return null;
        }
        int w = orbitalDrawn.width;
        int h = orbitalDrawn.height;
        if (orbitalOverflowLayer == null || orbitalOverflowW != w || orbitalOverflowH != h) {
            java.awt.image.BufferedImage layer = new java.awt.image.BufferedImage(
                    w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D lg = layer.createGraphics();
            try {
                lg.setRenderingHint(
                        java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                lg.drawImage(image, 0, 0, w, h, null);
            } finally {
                lg.dispose();
            }
            orbitalOverflowLayer = layer;
            orbitalOverflowW = w;
            orbitalOverflowH = h;
        }
        return orbitalOverflowLayer;
    }

    private Rectangle reminderOnCanvas(JComponent canvas) {
        if (shell == null || canvas == null) {
            return null;
        }
        Rectangle reminder = shell.homeReminderBounds();
        if (reminder.width < 40) {
            return null;
        }
        try {
            Point origin = SwingUtilities.convertPoint(shell, reminder.x, reminder.y, canvas);
            return new Rectangle(origin.x, origin.y, reminder.width, reminder.height);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean opaqueForeground(int x, int y) {
        java.awt.image.BufferedImage image = OrbitalAssets.shared().foreground();
        if (image == null || orbitalDrawn.width <= 0) {
            return false;
        }
        if (controlHole.width > 0 && controlHole.contains(x, y)) {
            return false;
        }
        if (!orbitalDrawn.contains(x, y)) {
            return false;
        }
        // 卡片區留給殼層命中，不要被玻璃層吃掉。
        JComponent canvas = findCanvas();
        Rectangle reminder = reminderOnCanvas(canvas);
        if (reminder != null && reminder.contains(x, y)) {
            return false;
        }
        int imageX = (x - orbitalDrawn.x) * image.getWidth() / Math.max(1, orbitalDrawn.width);
        int imageY = (y - orbitalDrawn.y) * image.getHeight() / Math.max(1, orbitalDrawn.height);
        if (imageX < 0 || imageY < 0 || imageX >= image.getWidth() || imageY >= image.getHeight()) {
            return false;
        }
        return ((image.getRGB(imageX, imageY) >>> 24) & 0xff) > 24;
    }

    private JComponent findCanvas() {
        if (shell == null) {
            return null;
        }
        java.awt.Window window = SwingUtilities.getWindowAncestor(shell);
        if (!(window instanceof javax.swing.JFrame)) {
            return null;
        }
        Object canvas = ((javax.swing.JFrame) window).getRootPane().getClientProperty("punchclock.canvas");
        return canvas instanceof JComponent ? (JComponent) canvas : null;
    }

    /** 頭和前臂畫在最前面。只有標題列那一段讓過視窗按鈕，袖子維持原圖的外緣。 */
    private void drawHeadAndArms(Graphics2D g, JComponent canvas) {
        int bottom = bust.y + Math.round(bust.height * HomeCharacterLayout.LEDGE_Y);
        if (bottom - bust.y <= 0) {
            return;
        }
        Shape clip = g.getClip();
        Rectangle controls = WindowChrome.chromeControls(canvas);
        int split = bust.y;
        if (controls != null && controls.height > 0) {
            split = Math.max(bust.y, Math.min(bottom, controls.y + controls.height));
        }
        if (controls != null && controls.width > 0 && controls.x > bust.x && split > bust.y + 2) {
            int right = Math.min(bust.x + bust.width, controls.x - 4);
            if (right - bust.x >= 8) {
                g.clipRect(bust.x, bust.y, right - bust.x, split - bust.y);
                draw(g, art.bust, bust);
                g.setClip(clip);
            }
        } else {
            split = bust.y;
        }
        if (bottom > split) {
            g.clipRect(bust.x, split, bust.width, bottom - split);
            draw(g, art.bust, bust);
            g.setClip(clip);
        }
        int sleeveBottom = bust.y + Math.round(bust.height * 0.336f);
        int sleeveX = bust.x + Math.round(bust.width * 0.60f);
        if (sleeveBottom > bottom && bust.x + bust.width > sleeveX) {
            g.clipRect(sleeveX, bottom, bust.x + bust.width - sleeveX, sleeveBottom - bottom);
            draw(g, art.bust, bust);
            g.setClip(clip);
        }
    }

    /** 卡片頂緣以下只留兩隻手的輪廓，兩手之間和兩側的毛衣不畫。 */
    private void drawFingers(Graphics2D g) {
        if (art.fingers == null) {
            return;
        }
        int top = bust.y + Math.round(bust.height * HomeCharacterLayout.LEDGE_Y);
        int height = bust.y + bust.height - top;
        if (height <= 0) {
            return;
        }
        Shape clip = g.getClip();
        g.clipRect(bust.x, top, bust.width, height);
        draw(g, art.fingers, bust);
        g.setClip(clip);
    }

    private static void draw(Graphics2D g, java.awt.image.BufferedImage image, Rectangle dest) {
        Object previous = g.getRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, dest.x, dest.y, dest.width, dest.height, null);
        if (previous != null) {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, previous);
        }
    }
}
