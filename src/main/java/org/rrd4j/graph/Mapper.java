package org.rrd4j.graph;

import org.rrd4j.graph.ImageParameters;
import org.rrd4j.graph.RrdGraphDef;

class Mapper {
    private RrdGraphDef gdef;
    private ImageParameters im;
    private double pixieX;
    private double[] pixieY;

    Mapper(RrdGraph rrdGraph) {
        this.gdef = rrdGraph.gdef;
        this.im = rrdGraph.im;
        this.pixieY = new double[im.axisImageParams.length];

        pixieX = (double) im.xsize / (double) (im.end - im.start);

        for (int yaxis=0; yaxis<pixieY.length; yaxis++) {
            double minval =  im.axisImageParams[yaxis].yminval;
            double maxval =  im.axisImageParams[yaxis].ymaxval;
            if (!im.axisImageParams[yaxis].logarithmic) {
                pixieY[yaxis] = (double) im.ysize / (maxval - minval);
            }
            else {
                pixieY[yaxis] = (double) im.ysize / (ValueAxisLogarithmic.log10(maxval) - ValueAxisLogarithmic.log10(minval));
            }
        }
    }

    Mapper(RrdGraphDef gdef, ImageParameters im) {
        this.gdef = gdef;
        this.im = im;
        pixieX = (double) im.xsize / (double) (im.end - im.start);
        if (!gdef.logarithmic) {
            pixieY = (double) im.ysize / (im.maxval - im.minval);
        }
        else {
            pixieY = (double) im.ysize / (Math.log10(im.maxval) - Math.log10(im.minval));
        }
    }

    int xtr(double mytime) {
        return (int) ((double) im.xorigin + pixieX * (mytime - im.start));
    }

    int ytr(int yaxis, double value) {
        double yval;
        double yminval = im.axisImageParams[yaxis].yminval;
        if (!im.axisImageParams[yaxis].logarithmic) {
            yval = im.yorigin - pixieY[yaxis] * (value - yminval) + 0.5;
        }
        else {
            if (value < yminval) {
                yval = im.yorigin;
            }
            else {
                yval = im.yorigin - pixieY[yaxis] * (ValueAxisLogarithmic.log10(value) - ValueAxisLogarithmic.log10(yminval)) + 0.5;
            }
        }
        if (!gdef.rigid) {
            return (int) yval;
        }
        else if ((int) yval > im.yorigin) {
            return im.yorigin + 2;
        }
        else if ((int) yval < im.yorigin - im.ysize) {
            return im.yorigin - im.ysize - 2;
        }
        else {
            return (int) yval;
        }
    }

}
