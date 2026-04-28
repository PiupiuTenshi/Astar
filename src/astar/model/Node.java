package astar.model;
import java.util.ArrayList;
import java.util.List;

public class Node {
    public LatLng p;
    public double g, f;
    public boolean closed;
    public Node parent;
    public List<Edge> nb = new ArrayList<>();

    public Node(LatLng p) {
        this.p = p;
    }
}