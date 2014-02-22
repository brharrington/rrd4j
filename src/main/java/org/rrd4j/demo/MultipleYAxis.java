package org.rrd4j.demo;

import org.rrd4j.DsType;
import org.rrd4j.core.RrdDb;
import org.rrd4j.core.RrdDef;
import org.rrd4j.core.Sample;
import org.rrd4j.core.Util;
import org.rrd4j.graph.RrdGraph;
import org.rrd4j.graph.RrdGraphConstants;
import org.rrd4j.graph.RrdGraphDef;
import org.rrd4j.graph.RrdAxisDef;

import java.awt.*;
import java.io.IOException;

import static org.rrd4j.ConsolFun.*;

class MultipleYAxis {
    public static void main(String[] args) throws IOException {

        String rrdFileYa = Util.getRrd4jDemoPath("multiYaxisYa.rrd");
        String rrdFileYb = Util.getRrd4jDemoPath("multiYaxisYb.rrd");
        String rrdFileYc = Util.getRrd4jDemoPath("multiYaxisYc.rrd");
        String rrdFileYd = Util.getRrd4jDemoPath("multiYaxisYd.rrd");

        String pngFile = Util.getRrd4jDemoPath("multiYaxis.png");

        // create
        long start = Util.getTime();
        long end = start + 300 * 300;

        RrdDef rrdDefYa = new RrdDef(rrdFileYa, start - 1, 300);
        RrdDef rrdDefYb = new RrdDef(rrdFileYb, start - 1, 300);
        RrdDef rrdDefYc = new RrdDef(rrdFileYc, start - 1, 300);
        RrdDef rrdDefYd = new RrdDef(rrdFileYd, start - 1, 300);

        rrdDefYa.addDatasource("Ya", DsType.GAUGE, 600, Double.NaN, Double.NaN);
        rrdDefYb.addDatasource("Yb", DsType.GAUGE, 600, Double.NaN, Double.NaN);
        rrdDefYc.addDatasource("Yc", DsType.GAUGE, 600, Double.NaN, Double.NaN);
        rrdDefYd.addDatasource("Yd", DsType.GAUGE, 600, Double.NaN, Double.NaN);
        /*
        *    0.5  xff       X-files factor. Valid values are between 0 and 1.
        *      1 steps     Number of archive steps
        *    300 rows      Number of archive rows
        */
        rrdDefYa.addArchive(AVERAGE, 0.5, 1, 300);
        rrdDefYb.addArchive(AVERAGE, 0.5, 1, 300);
        rrdDefYc.addArchive(AVERAGE, 0.5, 1, 300);
        rrdDefYd.addArchive(AVERAGE, 0.5, 1, 300);

        RrdDb rrdDbYa = new RrdDb(rrdDefYa);
        RrdDb rrdDbYb = new RrdDb(rrdDefYb);
        RrdDb rrdDbYc = new RrdDb(rrdDefYc);
        RrdDb rrdDbYd = new RrdDb(rrdDefYd);

        // update

        for (long t = start; t < end; t += 300) {
            double valueYa =  3000000 * (Math.sin(t / 3000.0) * 50 + 50);
            double valueYb =  10000 * ((Math.sin(t / 9000.0) * 50 + 100) / 3.0);
            double valueYc =  10000 * ((Math.sin(t / 12000.0) * 50 + 200) / 6.0);
            double valueYd =  valueYc - 500000;

            Sample sampleYa = rrdDbYa.createSample(t);
            sampleYa.setValue("Ya", valueYa);
            sampleYa.update();

            Sample sampleYb = rrdDbYb.createSample(t);
            sampleYb.setValue("Yb", valueYb);
            sampleYb.update();

            Sample sampleYc = rrdDbYc.createSample(t);
            sampleYc.setValue("Yc", valueYc);
            sampleYc.update();

            Sample sampleYd = rrdDbYd.createSample(t);
            sampleYd.setValue("Yd", valueYd);
            sampleYd.update();
        }
        // graph
        RrdGraphDef gDef = new RrdGraphDef();
        gDef.setFilename(pngFile);
        gDef.setWidth(450);
        gDef.setHeight(250);
        gDef.setImageFormat("png");
        gDef.setTimeSpan(start, start + 86400);
        gDef.setTitle("Rrd4j's Multiple Y Axis demo");


        configureAxisPlots(gDef, rrdFileYa, rrdFileYb, rrdFileYc, rrdFileYd);

        //axis 0 determines the grid type
        gDef.setLogarithmic(0, false);
        //individual scale / type is computed for each axis
        gDef.setLogarithmic(2, true);

        new RrdGraph(gDef);

    }


    /*
     *
     * Y0 axis has middle range with smallest min value and largest max value,
     * Y1 axis has largest range,
     * Y2 axis has smallest range with larger min value and smallest max value
     * Y0 and Y2 have their ranges extend to y1 range since they already fit within it
     * makes graph easier to read
     */
    static void  configureAxisPlots(RrdGraphDef gDef, String rrdFileYa, String rrdFileYb, String rrdFileYc, String rrdFileYd) {

        RrdAxisDef yaRrdAxisDef = new RrdAxisDef();
        yaRrdAxisDef.setVerticalLabel("Ya Axis");
        yaRrdAxisDef.setColor(Color.RED);
        gDef.addValueAxis(0, yaRrdAxisDef);

        RrdAxisDef ybRrdAxisDef = new RrdAxisDef();
        ybRrdAxisDef.setVerticalLabel("Yb Axis");
        ybRrdAxisDef.setOpposite(true);
        ybRrdAxisDef.setColor(Color.GREEN);
        gDef.addValueAxis(1, ybRrdAxisDef);

        RrdAxisDef ycRrdAxisDef = new RrdAxisDef();
        ycRrdAxisDef.setVerticalLabel("Yc & Yd Axis");
        ycRrdAxisDef.setOpposite(true);
        ycRrdAxisDef.setColor(Color.BLUE);
        gDef.addValueAxis(2, ycRrdAxisDef);


        //connect data sources to the graph definition

        gDef.datasource("Ya", rrdFileYa, "Ya", AVERAGE);
        gDef.datasource("Yb", rrdFileYb, "Yb", AVERAGE);
        gDef.datasource("Yc", rrdFileYc, "Yc", AVERAGE);
        gDef.datasource("Yd", rrdFileYd, "Yd", AVERAGE);


        //Draw Ya data source on default (index 0) axis with legend "Ya metrics"
        gDef.line(0, "Ya", Color.RED, "Ya metrics", new BasicStroke(2.0F), false);

        //Draw Yb data source on second axis (index 1)  with legend "Yb metrics"
        gDef.line(1, "Yb", Color.GREEN, "Yb metrics", new BasicStroke(2.0F), false);

        //Draw Yc data source on third axis (index 2)  with legend "Yc metrics"
        gDef.line(2, "Yc", Color.BLUE, "Yc metrics", new BasicStroke(2.0F), false);
        gDef.line(2, "Yd", Color.BLUE, "Yd metrics", new BasicStroke(2.0F), false);
    }
}
