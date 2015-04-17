package com.github.rvesse.github.pr.stats.collectors;

import java.util.Date;

import org.eclipse.egit.github.core.PullRequest;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.joda.time.Duration;
import org.joda.time.Instant;

public class AbstractPullRequestCollector implements Collector<PullRequest> {

    protected int count;
    protected int open;
    protected int merged;
    protected int mergeable;
    protected int closed;
    protected LongStatsCollector daysOpen = new LongStatsCollector();
    protected LongStatsCollector daysToMerge = new LongStatsCollector();
    private LongStatsCollector daysToClose = new LongStatsCollector();
    private static final Instant NOW = Instant.now();

    @Override
    public void start() {
        // Reset
        this.count = 0;
        this.open = 0;
        this.merged = 0;
        this.mergeable = 0;
        this.closed = 0;
        this.daysOpen.start();
        this.daysToMerge.start();
        this.daysToClose.start();
    }

    @Override
    public void end() {
        this.daysOpen.end();
        this.daysToMerge.end();
        this.daysToClose.end();
    }

    @Override
    public void collect(GitHubClient client, PullRequest pr) {
        this.count++;

        if (pr.getMergedAt() != null) {
            this.merged++;
            long daysToMerge = calculateDays(toInstant(pr.getCreatedAt()), toInstant(pr.getMergedAt()));
            this.daysToMerge.collect(client, daysToMerge);
        } else if (pr.getClosedAt() != null) {
            this.closed++;
            long daysToClose = calculateDays(toInstant(pr.getCreatedAt()), toInstant(pr.getClosedAt()));
            this.daysToClose.collect(client, daysToClose);
        } else {
            this.open++;
            long daysOpen = calculateDays(toInstant(pr.getCreatedAt()), NOW);
            this.daysOpen.collect(client, daysOpen);
            
            if (pr.isMergeable()) {
                this.mergeable++;
            }
        }

    }

    public long getTotal() {
        return this.count;
    }

    public long getOpen() {
        return this.open;
    }

    public long getMerged() {
        return this.merged;
    }

    public long getClosed() {
        return this.closed;
    }

    public long getOpenMergeable() {
        return this.mergeable;
    }

    public double getOpenPercentage() {
        return calcPercentage(this.open);
    }

    public double getMergedPercentage() {
        return calcPercentage(this.merged);
    }

    public double getClosedPercentage() {
        return calcPercentage(this.closed);
    }
    
    public LongStatsCollector getDaysOpenStats() {
        return this.daysOpen;
    }
    
    public LongStatsCollector getDaysToMergeStats() {
        return this.daysToMerge;
    }
    
    public LongStatsCollector getDaysToCloseStats() {
        return this.daysToClose;
    }

    protected final Instant toInstant(Date date) {
        return new Instant(date);
    }

    protected final long calculateDays(Instant start, Instant end) {
        Duration duration = new Duration(start, end);
        return duration.getStandardDays();
    }

    protected final double calcPercentage(long num) {
        return ((double) num / (double) this.count);
    }
}