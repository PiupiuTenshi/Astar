package astar.algorithm;

import astar.model.Edge;
import astar.model.LatLng;
import astar.model.Node;
import astar.util.GeoMath;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;

public class AStarEngine {
    public static List<Node> buildGraph(List<LatLng> wp) {
        List<Node> ns = new ArrayList<>();
        for (LatLng p : wp) ns.add(new Node(p));
        for (int i = 0; i < ns.size() - 1; i++) {
            double c = GeoMath.hav(ns.get(i).p, ns.get(i + 1).p);
            ns.get(i).nb.add(new Edge(ns.get(i + 1), c));
            ns.get(i + 1).nb.add(new Edge(ns.get(i), c));
        }
        return ns;
    }

    public static List<Node> findPath(List<Node> all, Node s, Node goal) {
        for (Node n : all) {
            n.g = Double.MAX_VALUE;
            n.f = Double.MAX_VALUE;
            n.parent = null;
            n.closed = false;
        }
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        s.g = 0;
        s.f = GeoMath.hav(s.p, goal.p);
        open.add(s);

        while (!open.isEmpty()) {
            Node cur = open.poll();
            if (cur == goal) return rebuild(goal);
            if (cur.closed) continue;
            cur.closed = true;

            for (Edge e : cur.nb) {
                if (e.n.closed) continue;
                double tg = cur.g + e.c;
                if (tg < e.n.g) {
                    e.n.g = tg;
                    e.n.f = tg + GeoMath.hav(e.n.p, goal.p);
                    e.n.parent = cur;
                    open.add(e.n);
                }
            }
        }
        return null;
    }

    private static List<Node> rebuild(Node g) {
        LinkedList<Node> p = new LinkedList<>();
        for (Node c = g; c != null; c = c.parent) p.addFirst(c);
        return p;
    }
}