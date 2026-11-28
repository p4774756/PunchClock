package com.example.ui;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** 私房相簿：縮圖格狀與大圖左右切換。 */
public final class PrivateAlbumPanel extends JPanel {

    public static final String CARD_GRID = "grid";
    public static final String CARD_VIEW = "view";

    private static final int THUMB_EDGE = 120;
    private static final int GRID_COLUMNS = 3;

    private final CardLayout cards = new CardLayout();
    private final JPanel cardHost = new JPanel(cards);
    private final JPanel gridHost = new JPanel();
    private final JLabel largeImage = new JLabel("", SwingConstants.CENTER);
    private final JLabel viewCaption = new JLabel("", SwingConstants.CENTER);
    private final List<String> fileNames = new ArrayList<>();
    private int viewIndex = -1;
    private String activeCard = CARD_GRID;

    public PrivateAlbumPanel() {
        super(new BorderLayout(0, 8));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        gridHost.setOpaque(false);
        gridHost.setLayout(new GridLayout(0, GRID_COLUMNS, 8, 8));
        JScrollPane scroll = new JScrollPane(gridHost);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        JPanel view = new JPanel(new BorderLayout(0, 8));
        view.setOpaque(false);
        largeImage.setOpaque(true);
        largeImage.setBackground(CandyTheme.PANEL);
        view.add(largeImage, BorderLayout.CENTER);

        Font captionFont = UiFonts.chinesePlain(12);
        viewCaption.setFont(captionFont);
        viewCaption.setForeground(NightTheme.MUTED);
        view.add(viewCaption, BorderLayout.NORTH);

        JPanel controls = new JPanel();
        controls.setOpaque(false);
        CandyButton prev = new CandyButton("上一張", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
        CandyButton next = new CandyButton("下一張", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
        CandyButton back = new CandyButton("返回縮圖", CandyButton.Variant.SECONDARY, CandyButton.Size.SMALL);
        prev.setFont(UiFonts.chinesePlain(12));
        next.setFont(UiFonts.chinesePlain(12));
        back.setFont(UiFonts.chinesePlain(12));
        prev.addActionListener(e -> showViewIndex(PrivateAlbumNavigation.previousIndex(viewIndex, fileNames.size())));
        next.addActionListener(e -> showViewIndex(PrivateAlbumNavigation.nextIndex(viewIndex, fileNames.size())));
        back.addActionListener(e -> showGrid());
        controls.add(prev);
        controls.add(next);
        controls.add(back);
        view.add(controls, BorderLayout.SOUTH);

        cardHost.setOpaque(false);
        cardHost.add(scroll, CARD_GRID);
        cardHost.add(view, CARD_VIEW);
        add(cardHost, BorderLayout.CENTER);

        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!CARD_VIEW.equals(activeCard) || fileNames.isEmpty()) {
                    return;
                }
                if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                    showViewIndex(PrivateAlbumNavigation.previousIndex(viewIndex, fileNames.size()));
                } else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
                    showViewIndex(PrivateAlbumNavigation.nextIndex(viewIndex, fileNames.size()));
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    showGrid();
                }
            }
        });

        reloadCatalog();
    }

    public void reloadCatalog() {
        fileNames.clear();
        fileNames.addAll(PrivateAlbumCatalog.listImageFileNames(PrivateAlbumPanel.class));
        viewIndex = -1;
        activeCard = CARD_GRID;
        cards.show(cardHost, CARD_GRID);
        rebuildThumbnails();
        largeImage.setIcon(null);
        viewCaption.setText("");
    }

    private void rebuildThumbnails() {
        gridHost.removeAll();
        for (int i = 0; i < fileNames.size(); i++) {
            String name = fileNames.get(i);
            ImageIcon icon = thumbnailIcon(name);
            JButton tile = new JButton(icon);
            tile.setOpaque(false);
            tile.setContentAreaFilled(false);
            tile.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180)));
            tile.setToolTipText(name);
            tile.setFocusable(false);
            final int index = i;
            tile.addActionListener(e -> showViewIndex(index));
            gridHost.add(tile);
        }
        gridHost.revalidate();
        gridHost.repaint();
    }

    private ImageIcon thumbnailIcon(String fileName) {
        BufferedImage src = PrivateAlbumCatalog.loadImage(PrivateAlbumPanel.class, fileName);
        if (src == null) {
            return new ImageIcon(new BufferedImage(THUMB_EDGE, THUMB_EDGE, BufferedImage.TYPE_INT_RGB));
        }
        return new ImageIcon(scaleToFit(src, THUMB_EDGE, THUMB_EDGE));
    }

    private void showViewIndex(int index) {
        if (fileNames.isEmpty()) {
            return;
        }
        viewIndex = PrivateAlbumNavigation.clampIndex(index, fileNames.size());
        String name = fileNames.get(viewIndex);
        BufferedImage src = PrivateAlbumCatalog.loadImage(PrivateAlbumPanel.class, name);
        if (src != null) {
            BufferedImage scaled = scaleToFit(src, 520, 400);
            largeImage.setIcon(new ImageIcon(scaled));
        } else {
            largeImage.setIcon(null);
        }
        viewCaption.setText((viewIndex + 1) + " / " + fileNames.size() + "  " + name);
        activeCard = CARD_VIEW;
        cards.show(cardHost, CARD_VIEW);
        requestFocusInWindow();
        revalidate();
        repaint();
    }

    private void showGrid() {
        activeCard = CARD_GRID;
        cards.show(cardHost, CARD_GRID);
        revalidate();
        repaint();
    }

    private static BufferedImage scaleToFit(BufferedImage src, int maxW, int maxH) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= 0 || h <= 0) {
            return src;
        }
        double scale = Math.min((double) maxW / w, (double) maxH / h);
        if (scale >= 1.0) {
            return src;
        }
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, tw, th, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    public List<String> imageFileNamesForTests() {
        return List.copyOf(fileNames);
    }

    public String activeCardForTests() {
        return activeCard;
    }

    public int viewIndexForTests() {
        return viewIndex;
    }

    public void showViewIndexForTests(int index) {
        showViewIndex(index);
    }

  /** 離屏繪製目前版面（縮圖格或大圖檢視）。 */
    public BufferedImage renderForTests(int width, int height) {
        Dimension prev = getSize();
        setSize(width, height);
        doLayout();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics g = image.getGraphics();
        try {
            g.setColor(CandyTheme.PANEL);
            g.fillRect(0, 0, width, height);
            paint(g);
        } finally {
            g.dispose();
        }
        setSize(prev);
        return image;
    }
}