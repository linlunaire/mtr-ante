package cn.zbx1425.mtrsteamloco.render.rail;

/** Keeps the two existing geometry tolerances while rejecting repeat loops that cannot terminate. */
final class RailRepeatGeometry {
    private RailRepeatGeometry() { }

    static double step(double adjustedLength, float interval) {
        if (!Double.isFinite(adjustedLength) || adjustedLength <= 0
                || !Float.isFinite(interval) || interval <= 0) return 0;
        double step = adjustedLength / Math.max(1, Math.round(adjustedLength / interval));
        return Double.isFinite(step) && step > 0 ? step : 0;
    }

    static double advance(double current, double step) {
        double next = current + step;
        if (next <= current) throw new IllegalArgumentException("Rail repeat interval does not advance geometry");
        return next;
    }
}
