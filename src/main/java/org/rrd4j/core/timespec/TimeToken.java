package org.rrd4j.core.timespec;

class TimeToken {
    public static final int MIDNIGHT = 1;
    public static final int NOON = 2;
    public static final int TEATIME = 3;
    public static final int PM = 4;
    public static final int AM = 5;
    public static final int YESTERDAY = 6;
    public static final int TODAY = 7;
    public static final int TOMORROW = 8;
    public static final int NOW = 9;
    public static final int START = 10;
    public static final int END = 11;
    public static final int SECONDS = 12;
    public static final int MINUTES = 13;
    public static final int HOURS = 14;
    public static final int DAYS = 15;
    public static final int WEEKS = 16;
    public static final int MONTHS = 17;
    public static final int YEARS = 18;
    public static final int MONTHS_MINUTES = 19;
    public static final int NUMBER = 20;
    public static final int PLUS = 21;
    public static final int MINUS = 22;
    public static final int DOT = 23;
    public static final int COLON = 24;
    public static final int SLASH = 25;
    public static final int ID = 26;
    public static final int JUNK = 27;
    public static final int JAN = 28;
    public static final int FEB = 29;
    public static final int MAR = 30;
    public static final int APR = 31;
    public static final int MAY = 32;
    public static final int JUN = 33;
    public static final int JUL = 34;
    public static final int AUG = 35;
    public static final int SEP = 36;
    public static final int OCT = 37;
    public static final int NOV = 38;
    public static final int DEC = 39;
    public static final int SUN = 40;
    public static final int MON = 41;
    public static final int TUE = 42;
    public static final int WED = 43;
    public static final int THU = 44;
    public static final int FRI = 45;
    public static final int SAT = 46;
    public static final int EOF = -1;

    final String value; /* token name */
    final int id;   /* token id */

    public TimeToken(String value, int id) {
        this.value = value;
        this.id = id;
    }

    public String toString() {
        return value + " [" + id + "]";
    }
}