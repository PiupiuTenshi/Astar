package astar.ui;

import astar.api.GoongApiClient;
import astar.algorithm.AStarEngine;
import astar.data.LandmarkData;
import astar.model.LatLng;
import astar.model.Node;
import astar.ui.map.TileMapPanel;
import astar.util.GeoMath;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MainFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private JTextField apiKeyField;
    private JTextArea resultArea;
    private JButton findBtn, clearBtn, resetBtn;
    private JLabel statusLabel, startLabel, endLabel;
    private TileMapPanel mapPanel;

    public int selectedStart = -1;
    public int selectedEnd = -1;

    public MainFrame() {
        setTitle("A* Pathfinder — HCMC Real Map — Goong.io");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1300, 840);
        setLocationRelativeTo(null);
        buildUI();
    }

    private void buildUI() {
        setLayout(new BorderLayout());

        // Header
        JPanel header = new JPanel(new GridBagLayout());
        header.setBackground(new Color(15, 25, 40));
        header.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(3, 6, 3, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("🗺  A* Pathfinder — Ho Chi Minh City  |  Real Map Tiles");
        title.setFont(new Font("SansSerif", Font.BOLD, 17));
        title.setForeground(new Color(255, 200, 50));
        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 5; gc.weightx = 1;
        header.add(title, gc);
        gc.gridwidth = 1; gc.gridy = 1;

        gc.gridx = 0; gc.weightx = 0; header.add(hl("Goong API Key:"), gc);
        apiKeyField = hf("pVeNeNvoeGDNUK5zom1KMKbAYWwXEqY2wmUrSVWe", 28);
        gc.gridx = 1; gc.weightx = 2; header.add(apiKeyField, gc);

        gc.gridx = 2; gc.weightx = 0; header.add(hl("Controls:"), gc);
        JLabel ctrl = new JLabel("<html><font color='#aaddff'>Scroll = Zoom &nbsp;|&nbsp; Drag = Pan &nbsp;|&nbsp; Click dot = Select</font></html>");
        ctrl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        gc.gridx = 3; gc.weightx = 1; header.add(ctrl, gc);

        gc.gridy = 2; gc.gridwidth = 5; gc.gridx = 0;
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        findBtn = btn("▶  Find Path (A*)", new Color(46, 204, 113));
        findBtn.addActionListener(e -> runAStar());
        resetBtn = btn("↺  Reset Selection", new Color(90, 110, 200));
        resetBtn.addActionListener(e -> resetSel());
        clearBtn = btn("✖  Clear All", new Color(200, 70, 60));
        clearBtn.addActionListener(e -> clearAll());
        startLabel = il("Start: —", new Color(80, 255, 130));
        endLabel = il("End:   —", new Color(255, 110, 110));
        row.add(findBtn); row.add(resetBtn); row.add(clearBtn);
        row.add(Box.createHorizontalStrut(20));
        row.add(startLabel); row.add(Box.createHorizontalStrut(16)); row.add(endLabel);
        header.add(row, gc);
        add(header, BorderLayout.NORTH);

        // Map panel
        mapPanel = new TileMapPanel(this);

        resultArea = new JTextArea(helpText());
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        resultArea.setBackground(new Color(14, 20, 30));
        resultArea.setForeground(new Color(150, 215, 150));
        resultArea.setMargin(new Insets(10, 10, 10, 10));
        JScrollPane scroll = new JScrollPane(resultArea);
        scroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(50, 75, 110)),
                "A* Result", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 12), new Color(160, 200, 255)));
        scroll.setPreferredSize(new Dimension(360, 100));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mapPanel, scroll);
        split.setDividerLocation(900);
        split.setResizeWeight(0.75);
        add(split, BorderLayout.CENTER);

        statusLabel = new JLabel("  Loading map tiles… (requires internet)  |  Click a landmark dot to set Start.");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(10, 18, 30));
        statusLabel.setForeground(new Color(160, 210, 255));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        add(statusLabel, BorderLayout.SOUTH);
    }

    private JLabel hl(String t) { JLabel l = new JLabel(t); l.setForeground(new Color(180, 200, 230)); l.setFont(new Font("SansSerif", Font.BOLD, 12)); return l; }
    private JTextField hf(String t, int c) {
        JTextField f = new JTextField(t, c); f.setFont(new Font("Monospaced", Font.PLAIN, 12));
        f.setBackground(new Color(30, 48, 70)); f.setForeground(Color.WHITE); f.setCaretColor(Color.WHITE);
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(60, 90, 130)), BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        return f;
    }
    private JButton btn(String t, Color bg) {
        JButton b = new JButton(t); b.setBackground(bg); b.setForeground(Color.WHITE);
        b.setFont(new Font("SansSerif", Font.BOLD, 12)); b.setFocusPainted(false); b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); b.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        return b;
    }
    private JLabel il(String t, Color c) { JLabel l = new JLabel(t); l.setForeground(c); l.setFont(new Font("SansSerif", Font.BOLD, 12)); return l; }

    private String helpText() {
        return "═══ Instructions ═══\n\n" +
                "1. Enter your Goong.io API key above\n\n" +
                "2. The map shows real HCMC tiles.\n" +
                "   Scroll to zoom, drag to pan.\n\n" +
                "3. Click a numbered dot → START (green)\n\n" +
                "4. Click another dot   → END   (red)\n\n" +
                "5. Click ▶ Find Path (A*)\n\n" +
                "The golden line = A* computed route.\n";
    }

    public void onPointClicked(int idx) {
        if (selectedStart == -1) {
            selectedStart = idx;
            startLabel.setText("Start: " + LandmarkData.POINTS[idx][0]);
            statusLabel.setText("  Start: " + LandmarkData.POINTS[idx][0] + "   Now click End point.");
        } else if (idx == selectedStart) {
            statusLabel.setText("  Already Start. Click a different dot for End.");
            return;
        } else {
            selectedEnd = idx;
            endLabel.setText("End:   " + LandmarkData.POINTS[idx][0]);
            statusLabel.setText("  End: " + LandmarkData.POINTS[idx][0] + "   Click ▶ Find Path (A*)");
        }
        mapPanel.repaint();
    }

    private void resetSel() {
        selectedStart = selectedEnd = -1;
        startLabel.setText("Start: —"); endLabel.setText("End:   —");
        mapPanel.clearRoute(); mapPanel.repaint();
        statusLabel.setText("  Reset. Click a dot to set Start.");
        resultArea.setText(helpText());
    }

    private void clearAll() { resetSel(); }

    private void runAStar() {
        String key = apiKeyField.getText().trim();
        if (key.isEmpty() || key.startsWith("YOUR_")) {
            JOptionPane.showMessageDialog(this, "Enter a valid Goong API key.", "Error", JOptionPane.ERROR_MESSAGE); return;
        }
        if (selectedStart == -1 || selectedEnd == -1) {
            JOptionPane.showMessageDialog(this, "Select Start and End on the map first.", "Error", JOptionPane.ERROR_MESSAGE); return;
        }

        LatLng spt = new LatLng((double) LandmarkData.POINTS[selectedStart][1], (double) LandmarkData.POINTS[selectedStart][2]);
        LatLng ept = new LatLng((double) LandmarkData.POINTS[selectedEnd][1], (double) LandmarkData.POINTS[selectedEnd][2]);

        findBtn.setEnabled(false);
        statusLabel.setText("  ⏳ Querying Goong Directions API…");
        resultArea.setText("Querying Goong.io…\n\n");

        new SwingWorker<List<LatLng>, String>() {
            @Override
            protected List<LatLng> doInBackground() throws Exception {
                publish("Start : " + LandmarkData.POINTS[selectedStart][0] + "\n");
                publish("End   : " + LandmarkData.POINTS[selectedEnd][0] + "\n\n");

                String json = GoongApiClient.fetchDirections(key, spt, ept);
                List<LatLng> wp = GoongApiClient.parseWaypoints(json);

                if (wp.isEmpty()) { publish("❌ No route from Goong.\n   Check API key.\n"); return null; }
                publish("✔ " + wp.size() + " road waypoints\n\n");
                publish("Running A*…\n");

                List<Node> nodes = AStarEngine.buildGraph(wp);
                List<Node> path = AStarEngine.findPath(nodes, nodes.get(0), nodes.get(nodes.size() - 1));

                if (path == null) { publish("❌ A* found no path.\n"); return null; }

                double dist = 0;
                for (int i = 0; i < path.size() - 1; i++) dist += GeoMath.hav(path.get(i).p, path.get(i + 1).p);
                publish("✔ Path found — " + path.size() + " nodes\n");
                publish(String.format("Distance : %.2f km\n", dist));
                publish("═══════════════════════\n");

                publish("\nRoute waypoints:\n");
                List<LatLng> result = new ArrayList<>();
                for (int i = 0; i < path.size(); i++) {
                    publish(String.format("  [%3d] %.6f, %.6f\n", i + 1, path.get(i).p.lat, path.get(i).p.lng));
                    result.add(path.get(i).p);
                }
                return result;
            }

            @Override
            protected void process(List<String> c) { for (String s : c) resultArea.append(s); }

            @Override
            protected void done() {
                findBtn.setEnabled(true);
                try {
                    List<LatLng> path = get();
                    if (path != null && !path.isEmpty()) {
                        mapPanel.setRoute(path); mapPanel.repaint();
                        statusLabel.setText("  ✔ A* path drawn on map! (golden line)");
                    } else { statusLabel.setText("  ❌ Could not compute path."); }
                } catch (Exception ex) {
                    resultArea.append("\n❌ " + ex.getMessage() + "\n");
                    statusLabel.setText("  ❌ Error.");
                }
            }
        }.execute();
    }
}