package astar.util;
import astar.model.LatLng;

public class GeoMath {
    // Thuật toán Haversine tính khoảng cách giữa 2 tọa độ
    public static double hav(LatLng a, LatLng b) {
        double R = 6371; // Bán kính trái đất (km)
        double dLa = Math.toRadians(b.lat - a.lat);
        double dLo = Math.toRadians(b.lng - a.lng);
        double h = Math.sin(dLa / 2) * Math.sin(dLa / 2) +
                Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.sin(dLo / 2) * Math.sin(dLo / 2);
        return 2 * R * Math.asin(Math.sqrt(h));
    }

    // Các hàm Mercator dùng cho vẽ bản đồ
    public static double lngLatToMercY(double lat) {
        return Math.log(Math.tan(Math.PI / 4 + Math.toRadians(lat) / 2));
    }

    public static double mercYToLat(double mercY) {
        return Math.toDegrees(2 * Math.atan(Math.exp(mercY)) - Math.PI / 2);
    }
}