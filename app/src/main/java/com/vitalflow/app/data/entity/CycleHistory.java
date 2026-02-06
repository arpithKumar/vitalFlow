package com.vitalflow.app.data.entity;

import java.util.Date;

public class CycleHistory {
    public Date startDate;
    public Date endDate;
    public int durationDays;       // Length of bleeding (e.g., 5 days)
    public int cycleLengthDays;    // Gap since previous period (e.g., 28 days)

    public CycleHistory(Date startDate, Date endDate, int durationDays, int cycleLengthDays) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.durationDays = durationDays;
        this.cycleLengthDays = cycleLengthDays;
    }
}
