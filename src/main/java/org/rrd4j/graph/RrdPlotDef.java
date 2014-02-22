package org.rrd4j.graph;


import java.util.ArrayList;
import java.util.List;

/**
 * Settings for a particular axis.  Used for multiple value axis (y-axis).
 */
public class RrdPlotDef {

    /*
    var yaxisLabel: Option[String] = None
    var yaxisMax: Option[Double] = None
    var yaxisMin: Option[Double] = None
     */
    int valueAxis;
    /* !ks todo delete
    String valueAxisLabel = null;
    boolean logarithmic = false;
    double minValue = Double.NaN; // ok
    double maxValue = Double.NaN; // ok
    boolean stack  = false;
    */


    final List<Source> sources = new ArrayList<Source>();
    final List<PlotElement> plotElements = new ArrayList<PlotElement>();

    public RrdPlotDef() {
        this(RrdGraphConstants.DEFAULT_Y_AXIS);
    }
    public RrdPlotDef(int valueAxis) {
        this.valueAxis =  valueAxis;
    }
}