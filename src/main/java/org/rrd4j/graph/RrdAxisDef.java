package org.rrd4j.graph;


import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings for a particular axis.  Used for multiple value axis (y-axis).
 */
public class RrdAxisDef {

     Paint color = null;
     boolean opposite_side = false;
     boolean logarithmic = false;
     String verticalLabel = null;
     Double min = Double.NaN;
     Double max = Double.NaN;

    public Paint getColor() { return color; }
    public void setColor(Paint c) { color = c; }
    public boolean opposite() { return opposite_side; }
    public void setOpposite(boolean o) { opposite_side = o; }
    public void setLogarithmic(boolean l) { logarithmic = l;}
    public boolean Logarithmic() { return logarithmic;}
    public String getVerticalLabel() { return verticalLabel; }
    public void setVerticalLabel(String label) { verticalLabel = label; }
    public Double getMinValue () { return min; }
    public void setMinValue(Double min) { this.min = min; }
    public Double getMaxValue() { return max; }
    public void setMaxValue(Double max) { this.max = max; }

}