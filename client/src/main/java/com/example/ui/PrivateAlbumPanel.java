package com.example.ui;

import com.example.service.PrivateAlbumPaths;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** 私房相簿：縮圖格狀與大圖檢視（左右切換）。 */
public final class PrivateAlbumPanel extends JPanel {

    public static final String CARD_GRID = "grid";
    public static final String CARD_VIEWER = "viewer";
    static final String EMPTY_HEAD = "尚無可顯示的圖片";
    static final String EMPTY_BODY = "請將 jpg、png 或 webp 放到相簿資料夾後重新整理。";

    private static final int THUMB = 120;
    private static final int THUMB_GAP = 10;

    private final CardLayout cards = new CardLayout();
    private final JPanel cardHost = new JPanel(cards);
    private final JPanel gridWrap = new JPanel(new BorderLayout());
    private final JPanel thumbGrid = new JPanel(new WrapLayout(WrapLayout.LEFT, THUMB_GAP, THUMB_GAP));
    private final JLabel emptyHead = new JLabel(EMPTY_HEAD, SwingConstants.CENTER);
    private final JLabel emptyBody = new JLabel(EMPTY_BODY, SwingConstants.CENTER);
    private final JLabel viewerLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel viewerCaption = new JLabel("", SwingConstants.CENTER);
    private final CandyButton prevButton = new CandyButton("上一張", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
    private final CandyButton nextButton = new CandyButton("下一張", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
    private final CandyButton backButton = new CandyButton("返回格狀", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);

    private Path albumDirectory = PrivateAlbumPaths.defaultDirectory();
    private List<Path> images = List.of();
    private int viewerIndex = -1;

    public PrivateAlbumPanel(Font mainFont, Font boldFont) {
        super(new BorderLayout());
        setOpaque(false);
        cardHost.setOpaque(false);

        emptyHead.setFont(boldFont);
        emptyHead.setForeground(NightTheme.INK);
        emptyBody.setFont(mainFont);
        emptyBody.setForeground(NightTheme.MUTED);

        JScrollPane scroll = new JScrollPane(thumbGrid);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        thumbGrid.setOpaque(false);

        JPanel emptyPanel = new JPanel();
        emptyPanel.setOpaque(false);
        emptyPanel.setLayout(new BoxLayout(emptyPanel, BoxLayout.Y_AXIS));
        emptyHead.setAlignmentX(CENTER_ALIGNMENT);
        emptyBody.setAlignmentX(CENTER_ALIGNMENT);
        emptyPanel.add(Box.createVerticalGlue());
        emptyPanel.add(emptyHead);
        emptyPanel.add(Box.createVerticalStrut(8));
        emptyPanel.add(emptyBody);
        emptyPanel.add(Box.createVerticalGlue());

        gridWrap.setOpaque(false);
        gridWrap.add(scroll, BorderLayout.CENTER);
        gridWrap.add(emptyPanel, BorderLayout.SOUTH);

        viewerLabel.setFont(mainFont);
        viewerCaption.setFont(mainFont);
        viewerCaption.setForeground(NightTheme.MUTED);

        prevButton.setFont(mainFont);
        nextButton.setFont(mainFont);
        backButton.setFont(mainFont);
        prevButton.addActionListener(e -> showRelative(-1));
        nextButton.addActionListener(e -> showRelative(1));
        backButton.addActionListener(e -> showGrid());

        JPanel viewerNav = new JPanel();
        viewerNav.setOpaque(false);
        viewerNav.add(prevButton);
        viewerNav.add(nextButton);
        viewerNav.add(backButton);

        JPanel viewer = new JPanel(new BorderLayout(0, 8));
        viewer.setOpaque(false);
        viewer.add(viewerLabel, BorderLayout.CENTER);
        viewer.add(viewerCaption, BorderLayout.NORTH);
        viewer.add(viewerNav, BorderLayout.SOUTH);

        cardHost.add(gridWrap, CARD_GRID);
        cardHost.add(viewer, CARD_VIEWER);
        add(cardHost, BorderLayout.CENTER);
        cards.show(cardHost, CARD_GRID);
    }

    public void setAlbumDirectory(Path directory) {
        albumDirectory = directory == null ? PrivateAlbumPaths.defaultDirectory() : directory;
    }

    public Path albumDirectoryForTests() {
        return albumDirectory;
    }

    public void reload() {
        List<Path> loaded;
        try {
            loaded = PrivateAlbumPaths.listImages(albumDirectory);
        } catch (IOException ex) {
            loaded = List.of();
        }
        images = loaded;
        rebuildGrid();
        boolean empty = images.isEmpty();
        thumbGrid.getParent().setVisible(!empty);
        emptyHead.setVisible(empty);
        emptyBody.setVisible(empty);
        if (viewerIndex >= 0) {
            if (images.isEmpty()) {
                showGrid();
            } else if (viewerIndex >= images.size()) {
                openViewer(images.size() - 1);
            } else {
                openViewer(viewerIndex);
            }
        }
        revalidate();
        repaint();
    }

    public List<Path> listedImagesForTests() {
        return images;
    }

    public boolean showingViewerForTests() {
        return viewerIndex >= 0;
    }

    public int viewerIndexForTests() {
        return viewerIndex;
    }

    public void openViewerForTests(int index) {
        openViewer(index);
    }

    public void viewerNextForTests() {
        showRelative(1);
    }

    public void viewerPreviousForTests() {
        showRelative(-1);
    }

    public BufferedImage renderForTests(int width, int height) {
        Dimension prev = getSize();
        setSize(width, height);
        doLayout();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(ClassicBevel.FACE);
            g.fillRect(0, 0, width, height);
            paint(g);
        } finally {
            g.dispose();
        }
        setSize(prev);
        return image;
    }

    private void rebuildGrid() {
        thumbGrid.removeAll();
        for (int i = 0; i < images.size(); i++) {
            final int index = i;
            Path file = images.get(i);
            JLabel thumb = new JLabel();
            thumb.setHorizontalAlignment(SwingConstants.CENTER);
            thumb.setPreferredSize(new Dimension(THUMB, THUMB));
            thumb.setToolTipText(file.getFileName().toString());
            thumb.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            try {
                BufferedImage raw = ImageIO.read(file.toFile());
                if (raw != null) {
                    thumb.setIcon(new ImageIcon(scaleImage(raw, THUMB, THUMB)));
                }
            } catch (IOException ignored) {
                thumb.setText("?");
            }
            thumb.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    openViewer(index);
                }
            });
            thumbGrid.add(thumb);
        }
        thumbGrid.revalidate();
        thumbGrid.repaint();
    }

    private void openViewer(int index) {
        if (index < 0 || index >= images.size()) {
            return;
        }
        viewerIndex = index;
        Path file = images.get(index);
        viewerCaption.setText(file.getFileName().toString());
        try {
            BufferedImage raw = ImageIO.read(file.toFile());
            if (raw == null) {
                viewerLabel.setIcon(null);
                viewerLabel.setText("無法讀取圖片");
            } else {
                int maxW = Math.max(200, getWidth() - 40);
                int maxH = Math.max(200, getHeight() - 120);
                viewerLabel.setText("");
                viewerLabel.setIcon(new ImageIcon(scaleImage(raw, maxW, maxH)));
            }
        } catch (IOException ex) {
            viewerLabel.setIcon(null);
            viewerLabel.setText("無法讀取圖片");
        }
        syncViewerButtons();
        cards.show(cardHost, CARD_VIEWER);
    }

    private void showRelative(int delta) {
        if (images.isEmpty() || viewerIndex < 0) {
            return;
        }
        int next = viewerIndex + delta;
        if (next < 0 || next >= images.size()) {
            return;
        }
        openViewer(next);
    }

    private void showGrid() {
        viewerIndex = -1;
        viewerLabel.setIcon(null);
        viewerLabel.setText("");
        viewerCaption.setText("");
        cards.show(cardHost, CARD_GRID);
    }

    private void syncViewerButtons() {
        boolean has = !images.isEmpty() && viewerIndex >= 0;
        prevButton.setEnabled(has && viewerIndex > 0);
        nextButton.setEnabled(has && viewerIndex < images.size() - 1);
    }

    private static Image scaleImage(BufferedImage source, int maxW, int maxH) {
        int w = source.getWidth();
        int h = source.getHeight();
        if (w <= 0 || h <= 0) {
            return source;
        }
        double scale = Math.min((double) maxW / w, (double) maxH / h);
        scale = Math.min(1.0, scale);
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, tw, th, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}