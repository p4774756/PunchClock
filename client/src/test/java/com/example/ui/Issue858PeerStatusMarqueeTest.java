package com.example.ui;

import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JTable;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 好友表自訂狀態單行＋溢位跑馬燈（headless offscreen）。
 */
public class Issue858PeerStatusMarqueeTest {

    private static final int COL_W = 130;
    private static final Color STATUS_MUTED = new Color(0x64, 0x64, 0x64);

    @Test
    public void noStatusSingleRowHeight() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PeerTableFixture fx = build("AA123", "", COL_W);
            assertEquals(30, fx.table.getRowHeight());
            assertFalse(fx.nameCell.needsStatusMarquee());
            BufferedImage shot = paintNameCell(fx);
            maybeWrite(shot, "no-status");
            PeerTableFixture withStatus = build("BB456", "忙碌", COL_W);
            assertEquals(fx.table.getRowHeight(), withStatus.table.getRowHeight());
        });
    }

    @Test
    public void shortStatusBesideName() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PeerTableFixture fx = build("yes123", "開會中", COL_W);
            assertFalse(fx.nameCell.needsStatusMarquee());
            BufferedImage shot = paintNameCell(fx);
            maybeWrite(shot, "short-status");
            int statusX = statusRegionStart(shot, fx.nameCell.displayId());
            assertTrue(statusX > 0);
            Color sample = new Color(shot.getRGB(statusX, shot.getHeight() / 2), true);
            assertTrue("status should look muted", colorNear(sample, STATUS_MUTED, 48));
        });
    }

    @Test
    public void longStatusMarqueeMovesWhileRowHeightStaysSingle() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String status = "一二三四五六七八九十长长长长长长";
            PeerTableFixture fx = build("yes123Mac", status, COL_W);
            assertTrue(fx.nameCell.needsStatusMarquee());
            assertEquals(30, fx.table.getRowHeight());

            PeerStatusMarquee.setOffset(fx.table, 0);
            BufferedImage frame0 = paintNameCell(fx);
            maybeWrite(frame0, "long-status-frame0");

            PeerStatusMarquee.setOffset(fx.table, 18);
            BufferedImage frame1 = paintNameCell(fx);
            maybeWrite(frame1, "long-status-frame1");

            PeerStatusMarquee.setOffset(fx.table, 36);
            BufferedImage frame2 = paintNameCell(fx);
            maybeWrite(frame2, "long-status-frame2");

            int diff01 = rowPixelDiff(frame0, frame1);
            int diff12 = rowPixelDiff(frame1, frame2);
            assertTrue("marquee should shift status pixels", diff01 > 4);
            assertTrue(diff12 > 4);
            assertTrue(fx.nameCell.avatarIcon() != null);
        });
    }

    @Test
    public void timerStopsWhenNothingOverflows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PeerTableFixture fx = build("AA123", "OK", 280);
            PeerStatusMarquee.sync(fx.table);
            assertFalse(PeerStatusMarquee.timerRunning(fx.table));
        });
    }

    private static PeerTableFixture refreshCell(PeerTableFixture fx) {
        Component cell = fx.table.prepareRenderer(fx.table.getCellRenderer(0, 0), 0, 0);
        assertTrue(cell instanceof PeerFriendNameCell);
        fx.nameCell = (PeerFriendNameCell) cell;
        return fx;
    }

    private static PeerTableFixture build(String peerId, String status, int colW) {
        Font main = UiFonts.chinesePlain(13);
        Font bold = UiFonts.chineseBold(13);
        PanelFactory.PeerInteractionRefs refs = new PanelFactory.PeerInteractionRefs();
        JPanel panel = PanelFactory.createPeerInteractionPanel(refs, main, bold);
        JPanel host = new JPanel();
        host.add(panel);
        Map<String, Boolean> online = new LinkedHashMap<>();
        online.put(peerId, true);
        refs.peerTable.putClientProperty(PanelFactory.PEER_ONLINE_CLIENT_KEY, online);
        Map<String, String> statuses = new LinkedHashMap<>();
        statuses.put(peerId, status);
        refs.peerTable.putClientProperty(PanelFactory.PEER_STATUS_TEXTS_KEY, statuses);
        refs.peerTableModel.addRow(new Object[]{peerId, PanelFactory.PEER_FAVORITE_OFF});
        NightTheme.apply(refs.peerTable);
        refs.peerTable.setSize(colW + 56, 80);
        refs.peerTable.doLayout();
        assertTrue(colW >= 28);
        refs.peerTable.getColumnModel().getColumn(0).setWidth(colW);
        PeerStatusMarquee.sync(refs.peerTable);
        Component cell = refs.peerTable.prepareRenderer(refs.peerTable.getCellRenderer(0, 0), 0, 0);
        assertTrue(cell instanceof PeerFriendNameCell);
        return new PeerTableFixture(refs.peerTable, refs.peerTableScroll, (PeerFriendNameCell) cell);
    }

    private static BufferedImage paintNameCell(PeerTableFixture fx) {
        fx = refreshCell(fx);
        int rowH = fx.table.getRowHeight();
        int colW = fx.table.getColumnModel().getColumn(0).getWidth();
        fx.nameCell.setBackground(CandyTheme.ROW);
        fx.nameCell.setSize(colW, rowH);
        BufferedImage image = new BufferedImage(colW, rowH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            fx.nameCell.paint(g);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static int rowPixelDiff(BufferedImage a, BufferedImage b) {
        int diff = 0;
        int w = Math.min(a.getWidth(), b.getWidth());
        int h = Math.min(a.getHeight(), b.getHeight());
        int y = h / 2;
        for (int x = 0; x < w; x++) {
            if (a.getRGB(x, y) != b.getRGB(x, y)) {
                diff++;
            }
        }
        return diff;
    }

    private static int statusRegionStart(BufferedImage image, String displayId) {
        int midY = image.getHeight() / 2;
        for (int x = image.getWidth() - 1; x >= 0; x--) {
            Color c = new Color(image.getRGB(x, midY), true);
            if (colorNear(c, STATUS_MUTED, 56) && x > 24) {
                return x;
            }
        }
        return -1;
    }

    private static boolean colorNear(Color a, Color b, int tol) {
        return Math.abs(a.getRed() - b.getRed()) <= tol
                && Math.abs(a.getGreen() - b.getGreen()) <= tol
                && Math.abs(a.getBlue() - b.getBlue()) <= tol;
    }

    private static void maybeWrite(BufferedImage image, String stem) {
        String dirProp = System.getProperty("shotsmed858.dir");
        if (dirProp == null || dirProp.isBlank()) {
            return;
        }
        try {
            Path dir = Path.of(dirProp);
            Files.createDirectories(dir);
            ImageIO.write(image, "png", dir.resolve(stem + ".png").toFile());
        } catch (Exception ignored) {
            // optional artifact for local verify
        }
    }

    private static final class PeerTableFixture {
        final JTable table;
        final JScrollPane scroll;
        PeerFriendNameCell nameCell;

        PeerTableFixture(JTable table, JScrollPane scroll, PeerFriendNameCell nameCell) {
            this.table = table;
            this.scroll = scroll;
            this.nameCell = nameCell;
        }
    }
}