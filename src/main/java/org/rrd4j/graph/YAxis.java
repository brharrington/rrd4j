package org.rrd4j.graph;


public interface YAxis {
    public boolean drawGrid(int axis, AxisImageParameters aim);
    public boolean drawTickMarksAndLabels(int axis, AxisImageParameters aim);
}
