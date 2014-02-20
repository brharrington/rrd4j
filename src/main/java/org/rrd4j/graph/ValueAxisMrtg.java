package org.rrd4j.graph;

import org.rrd4j.core.Util;

import java.awt.*;

class ValueAxisMrtg implements YAxis, RrdGraphConstants {
    private ImageParameters im;
    private ImageWorker worker;
    private RrdGraphDef gdef;

    ValueAxisMrtg(RrdGraph rrdGraph) {
        this.im = rrdGraph.im;
        this.gdef = rrdGraph.gdef;
        this.worker = rrdGraph.worker;
        im.unit = gdef.unit;
    }

    public boolean drawGrid(int axis, AxisImageParameters aim) { return draw(aim); }
    public boolean drawTickMarksAndLabels(int axis, AxisImageParameters aim) {return true; }

    boolean draw(AxisImageParameters aim) {
        Font font = gdef.smallFont;
        Paint mGridColor = gdef.colors[COLOR_MGRID];
        Paint fontColor = gdef.colors[COLOR_FONT];
        int labelOffset = (int) (worker.getFontAscent(font) / 2);

        if (Double.isNaN((aim.ymaxval - aim.yminval) / aim.magfact)) {
            return false;
        }

        int xLeft = im.xorigin;
        int xRight = im.xorigin + im.xsize;
        String labfmt;
        if (aim.scaledstep / aim.magfact * Math.max(Math.abs(aim.quadrant), Math.abs(4 - aim.quadrant)) <= 1.0) {
            labfmt = "%5.2f";
        }
        else {
            labfmt = Util.sprintf(gdef.locale, "%%4.%df", 1 - ((aim.scaledstep / aim.magfact > 10.0 || Math.ceil(aim.scaledstep / aim.magfact) == aim.scaledstep / aim.magfact) ? 1 : 0));
        }
        if (aim.symbol != ' ' || im.unit != null) {
            labfmt += " ";
        }
        if (aim.symbol != ' ') {
            labfmt += aim.symbol;
        }
        if (im.unit != null) {
            labfmt += im.unit;
        }
        for (int i = 0; i <= 4; i++) {
            int y = im.yorigin - im.ysize * i / 4;
            if (y >= im.yorigin - im.ysize && y <= im.yorigin) {
                String graph_label = Util.sprintf(gdef.locale, labfmt, aim.scaledstep / aim.magfact * (i - aim.quadrant));
                int length = (int) (worker.getStringWidth(graph_label, font));
                worker.drawString(graph_label, xLeft - length - PADDING_VLABEL, y + labelOffset, font, fontColor);
                worker.drawLine(xLeft - 2, y, xLeft + 2, y, mGridColor, gdef.tickStroke);
                worker.drawLine(xRight - 2, y, xRight + 2, y, mGridColor, gdef.tickStroke);
                worker.drawLine(xLeft, y, xRight, y, mGridColor, gdef.gridStroke);
            }
        }
        return true;
    }

}
