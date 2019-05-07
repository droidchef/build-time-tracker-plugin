package co.droidchef.android.buildtimetracker.util

class Clock {
    long startTimeInMs

    Clock() {
        this(System.currentTimeMillis())
    }

    Clock(long startTimeInMs) {
        this.startTimeInMs = startTimeInMs
    }

    long getTimeInMs() {
        return System.currentTimeMillis() - startTimeInMs
    }
}
