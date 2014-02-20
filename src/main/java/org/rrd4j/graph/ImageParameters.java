package org.rrd4j.graph;

import java.util.ArrayList;
import java.util.List;

class ImageParameters {
    long start;    // start time for xaxis
    long end;      // end   time for xaxis
    //double minvalue, maxvalue; //moved to y axis
    AxisImageParameters[] axisImageParams;
    //int unitsexponent;
    //double base;
    //double magfact;  //moved to y axis
    //char symbol;
    //double ygridstep;
    //int ylabfact;
    //double decimals;
    //int quadrant;
    //double scaledstep;
    int xsize;
    int ysize;
    int xorigin;
    int yorigin;
    int unitslength;
    int yAxisLabelWidth;
    int nbrLeftAxis;
    int nbrRightAxis;
    int xrightAxisSpace;
    int xgif, ygif;
    String unit;
}
