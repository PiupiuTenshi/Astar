package astar.ui.map;

import astar.data.LandmarkData;
import astar.model.LatLng;
import astar.ui.MainFrame;
import astar.util.GeoMath;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.File;

public class TileMapPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int TILE_SIZE = 256;
    private static final String TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";

    private final MainFrame app;

    // Viewport state
    private int zoom = 14;
    private double centerLat = 10.7769;
    private double centerLng = 106.7009;

    // Tile cache
    private final Map<String, BufferedImage> tileCache = new ConcurrentHashMap<>();
    private final ExecutorService tileLoader = Executors.newFixedThreadPool(16);

    // Pan state
    private Point dragStart = null;
    private double dragLat, dragLng;

    // Hover & Route
    private int hovered = -1;
    private List<LatLng> route = null;

    public TileMapPanel(MainFrame app) {
        this.app = app;
        setBackground(new Color(180, 200, 215));
        setToolTipText("");

        addMouseWheelListener(e -> {
            int notches = e.getWheelRotation();
            int newZoom = Math.max(10, Math.min(18, zoom - notches));
            if (newZoom == zoom) return;

            Point p = e.getPoint();
            LatLng ll = screenToLatLng(p.x, p.y); // Must be called BEFORE changing zoom!

            zoom = newZoom;
            double[] targetTile = latLngToTileXY(ll.lat, ll.lng, zoom);
            double cx = targetTile[0] - (p.x - getWidth() / 2.0) / TILE_SIZE;
            double cy = targetTile[1] - (p.y - getHeight() / 2.0) / TILE_SIZE;

            double n = Math.pow(2, zoom);
            centerLng = cx / n * 360.0 - 180.0;
            centerLat = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * cy / n))));

            tileCache.clear();
            repaint();
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int idx = nearestPoint(e.getX(), e.getY());
                if (idx >= 0) {
                    app.onPointClicked(idx);
                } else {
                    dragStart = e.getPoint();
                    dragLat = centerLat;
                    dragLng = centerLng;
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) { dragStart = null; }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart != null) {
                    int dx = e.getX() - dragStart.x;
                    int dy = e.getY() - dragStart.y;
                    double n = Math.pow(2, zoom);
                    centerLng = dragLng - dx * 360.0 / (TILE_SIZE * n);
                    double mercY = GeoMath.lngLatToMercY(dragLat) + dy * 2 * Math.PI / (TILE_SIZE * n);
                    centerLat = GeoMath.mercYToLat(mercY);
                    repaint();
                }
            }
            @Override
            public void mouseMoved(MouseEvent e) {
                int prev = hovered;
                hovered = nearestPoint(e.getX(), e.getY());
                if (hovered != prev) repaint();
            }
        });
    }

    public void setRoute(List<LatLng> r) { route = r; }
    public void clearRoute() { route = null; }

    private double[] latLngToTileXY(double lat, double lng, int z) {
        double n = Math.pow(2, z);
        double x = (lng + 180.0) / 360.0 * n;
        double latRad = Math.toRadians(lat);
        double y = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n;
        return new double[]{x, y};
    }

    private int[] latLngToScreen(double lat, double lng) {
        int w = getWidth(), h = getHeight();
        double[] center = latLngToTileXY(centerLat, centerLng, zoom);
        double[] pt = latLngToTileXY(lat, lng, zoom);
        int px = (int) ((pt[0] - center[0]) * TILE_SIZE + w / 2.0);
        int py = (int) ((pt[1] - center[1]) * TILE_SIZE + h / 2.0);
        return new int[]{px, py};
    }

    private LatLng screenToLatLng(int sx, int sy) {
        int w = getWidth(), h = getHeight();
        double[] center = latLngToTileXY(centerLat, centerLng, zoom);
        double tx = center[0] + (sx - w / 2.0) / TILE_SIZE;
        double ty = center[1] + (sy - h / 2.0) / TILE_SIZE;
        double n = Math.pow(2, zoom);
        double lng = tx / n * 360.0 - 180.0;
        double latRad = Math.atan(Math.sinh(Math.PI * (1 - 2 * ty / n)));
        return new LatLng(Math.toDegrees(latRad), lng);
    }

    private int nearestPoint(int mx, int my) {
        int best = -1;
        double bestD = 14;
        for (int i = 0; i < LandmarkData.POINTS.length; i++) {
            int[] s = latLngToScreen((double) LandmarkData.POINTS[i][1], (double) LandmarkData.POINTS[i][2]);
            double d = Math.hypot(mx - s[0], my - s[1]);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        int idx = nearestPoint(e.getX(), e.getY());
        if (idx >= 0) return "<html><b>" + LandmarkData.POINTS[idx][0] + "</b><br>Lat: " + LandmarkData.POINTS[idx][1] + "  Lng: " + LandmarkData.POINTS[idx][2] + "<br>Click to select</html>";
        return null;
    }

    private BufferedImage getTile(int z, int tx, int ty) {
        int n = (int) Math.pow(2, z);
        tx = ((tx % n) + n) % n;
        if (ty < 0 || ty >= n) return null;

        String key = z + "/" + tx + "/" + ty;

        // 1. Kiểm tra RAM cache trước (nhanh nhất)
        BufferedImage img = tileCache.get(key);
        if (img != null) return img;

        // 2. Tạo thư mục Cache trên ổ cứng nếu chưa có
        File cacheDir = new File(System.getProperty("java.io.tmpdir"), "AStar_Map_Cache");
        if (!cacheDir.exists()) cacheDir.mkdirs();

        File tileFile = new File(cacheDir, z + "_" + tx + "_" + ty + ".png");

        // 3. Nếu file đã có trên ổ cứng -> Đọc từ SSD lên RAM
        if (tileFile.exists()) {
            try {
                BufferedImage localImg = ImageIO.read(tileFile);
                if (localImg != null) {
                    tileCache.put(key, localImg);
                    return localImg;
                }
            } catch (Exception e) {
                // Lỗi đọc file thì kệ, cho tải lại ở bước 4
            }
        }

        // 4. Nếu không có ở ổ cứng -> Hiển thị "Loading" và tải từ mạng
        tileCache.put(key, createLoadingTile());
        final int ftx = tx, fty = ty;

        tileLoader.submit(() -> {
            try {
                String url = TILE_URL.replace("{z}", "" + z).replace("{x}", "" + ftx).replace("{y}", "" + fty);
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "AStarGoongApp/1.0 Java");

                if (conn.getResponseCode() == 200) {
                    BufferedImage tile = ImageIO.read(conn.getInputStream());
                    if (tile != null) {
                        // Lưu vào RAM
                        tileCache.put(key, tile);
                        // Lưu xuống ổ cứng để lần sau không phải tải lại
                        ImageIO.write(tile, "png", tileFile);
                        SwingUtilities.invokeLater(this::repaint);
                    }
                }
            } catch (Exception ex) {
                tileCache.remove(key); // Xóa tile "Loading" để lần sau kéo tới nó thử tải lại
            }
        });

        return tileCache.get(key);
    }

    private BufferedImage createLoadingTile() {
        BufferedImage img = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(new Color(210, 220, 230));
        g2.fillRect(0, 0, TILE_SIZE, TILE_SIZE);
        g2.setColor(new Color(180, 190, 200));
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2.drawString("Loading…", TILE_SIZE / 2 - 25, TILE_SIZE / 2);
        g2.dispose();
        return img;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();

        double[] centerTile = latLngToTileXY(centerLat, centerLng, zoom);
        int centerTileX = (int) Math.floor(centerTile[0]);
        int centerTileY = (int) Math.floor(centerTile[1]);
        double offsetX = (centerTile[0] - centerTileX) * TILE_SIZE;
        double offsetY = (centerTile[1] - centerTileY) * TILE_SIZE;

        int startX = (int) Math.floor(-(w / 2.0 - offsetX) / TILE_SIZE);
        int startY = (int) Math.floor(-(h / 2.0 - offsetY) / TILE_SIZE);
        int tilesX = (int) Math.ceil((double) w / TILE_SIZE) + 2;
        int tilesY = (int) Math.ceil((double) h / TILE_SIZE) + 2;

        for (int dx = -1; dx <= tilesX; dx++) {
            for (int dy = -1; dy <= tilesY; dy++) {
                int tx = centerTileX + dx + startX;
                int ty = centerTileY + dy + startY;
                BufferedImage tile = getTile(zoom, tx, ty);
                if (tile != null) {
                    int px = (int) (w / 2.0 - offsetX + (dx + startX) * TILE_SIZE);
                    int py = (int) (h / 2.0 - offsetY + (dy + startY) * TILE_SIZE);
                    g2.drawImage(tile, px, py, TILE_SIZE, TILE_SIZE, null);
                }
            }
        }

        if (route != null && route.size() > 1) {
            int[] px = new int[route.size()], py = new int[route.size()];
            for (int i = 0; i < route.size(); i++) {
                int[] s = latLngToScreen(route.get(i).lat, route.get(i).lng);
                px[i] = s[0]; py[i] = s[1];
            }
            g2.setColor(new Color(255, 165, 0, 80));
            g2.setStroke(new BasicStroke(10, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawPolyline(px, py, route.size());
            g2.setColor(new Color(255, 140, 0));
            g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawPolyline(px, py, route.size());
            drawEP(g2, route.get(0), new Color(46, 204, 113), "S");
            drawEP(g2, route.get(route.size() - 1), new Color(220, 50, 50), "E");
        }

        final int R = 9;
        for (int i = 0; i < LandmarkData.POINTS.length; i++) {
            int[] s = latLngToScreen((double) LandmarkData.POINTS[i][1], (double) LandmarkData.POINTS[i][2]);
            if (s[0] < -20 || s[0] > w + 20 || s[1] < -20 || s[1] > h + 20) continue;

            boolean isSt = (i == app.selectedStart);
            boolean isEn = (i == app.selectedEnd);
            boolean isHov = (i == hovered);

            Color fill = isSt ? new Color(46, 204, 113) : isEn ? new Color(220, 50, 50) : isHov ? new Color(241, 196, 15) : new Color(66, 133, 244);

            if (isSt || isEn) {
                g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 60));
                g2.fillOval(s[0] - R - 6, s[1] - R - 6, (R + 6) * 2, (R + 6) * 2);
            }
            g2.setColor(new Color(0, 0, 0, 100));
            g2.fillOval(s[0] - R + 2, s[1] - R + 3, R * 2, R * 2);
            g2.setColor(fill);
            g2.fillOval(s[0] - R, s[1] - R, R * 2, R * 2);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(isSt || isEn ? 2.5f : 1.8f));
            g2.drawOval(s[0] - R, s[1] - R, R * 2, R * 2);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 7));
            String num = String.valueOf(i + 1);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(num, s[0] - fm.stringWidth(num) / 2, s[1] + fm.getAscent() / 2 - 1);

            if (isSt || isEn || isHov) {
                String name = (String) LandmarkData.POINTS[i][0];
                g2.setFont(new Font("SansSerif", Font.BOLD, 12));
                fm = g2.getFontMetrics();
                int lw = fm.stringWidth(name);
                int lx = Math.max(4, Math.min(s[0] - lw / 2, w - lw - 6));
                int ly = s[1] - R - 9;
                if (ly < 16) ly = s[1] + R + 18;
                g2.setColor(new Color(0, 0, 0, 185));
                g2.fillRoundRect(lx - 5, ly - fm.getAscent() - 2, lw + 10, fm.getHeight() + 4, 8, 8);
                g2.setColor(isSt ? new Color(80, 255, 130) : isEn ? new Color(255, 120, 120) : new Color(255, 230, 60));
                g2.drawString(name, lx, ly);
            }
        }

        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(w - 80, h - 38, 72, 28, 10, 10);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 13));
        g2.drawString("Zoom: " + zoom, w - 74, h - 18);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g2.setColor(new Color(0, 0, 0, 130));
        String attr = "© OpenStreetMap contributors";
        FontMetrics fm2 = g2.getFontMetrics();
        int aw = fm2.stringWidth(attr);
        g2.fillRect(w - aw - 8, h - 16, aw + 8, 16);
        g2.setColor(Color.WHITE);
        g2.drawString(attr, w - aw - 4, h - 4);

        drawLegend(g2, h);

        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRoundRect(8, 8, 340, 22, 8, 8);
        g2.setColor(Color.WHITE);
        g2.drawString("Scroll=Zoom  |  Drag=Pan  |  Click dot=Select", 13, 23);
    }

    private void drawEP(Graphics2D g2, LatLng p, Color c, String lbl) {
        int[] s = latLngToScreen(p.lat, p.lng);
        g2.setColor(c);
        g2.fillOval(s[0] - 11, s[1] - 11, 22, 22);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(lbl, s[0] - fm.stringWidth(lbl) / 2, s[1] + fm.getAscent() / 2 - 1);
    }

    private void drawLegend(Graphics2D g2, int h) {
        int lx = 10, ly = h - 152, lw = 208, lht = 140;
        g2.setColor(new Color(255, 255, 255, 210));
        g2.fillRoundRect(lx, ly, lw, lht, 12, 12);
        g2.setColor(new Color(100, 130, 160));
        g2.drawRoundRect(lx, ly, lw, lht, 12, 12);
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.setColor(new Color(30, 50, 80));
        g2.drawString("Legend", lx + 10, ly + 20);
        ldot(g2, lx + 10, ly + 38, new Color(66, 133, 244), "Landmark (click to select)", new Color(30, 50, 80));
        ldot(g2, lx + 10, ly + 58, new Color(46, 204, 113), "Start — 1st click", new Color(30, 50, 80));
        ldot(g2, lx + 10, ly + 78, new Color(220, 50, 50), "End   — 2nd click", new Color(30, 50, 80));
        ldot(g2, lx + 10, ly + 98, new Color(241, 196, 15), "Hovered", new Color(30, 50, 80));
        g2.setColor(new Color(255, 140, 0));
        g2.setStroke(new BasicStroke(3f));
        g2.drawLine(lx + 10, ly + 118, lx + 32, ly + 118);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2.setColor(new Color(30, 50, 80));
        g2.drawString("A* route", lx + 38, ly + 122);
    }

    private void ldot(Graphics2D g2, int x, int y, Color c, String label, Color tc) {
        g2.setColor(c);
        g2.fillOval(x, y - 7, 14, 14);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2.setColor(tc);
        g2.drawString(label, x + 20, y + 4);
    }
}