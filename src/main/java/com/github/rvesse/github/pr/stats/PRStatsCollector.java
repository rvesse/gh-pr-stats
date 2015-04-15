/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.rvesse.github.pr.stats;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.egit.github.core.PullRequest;
import org.joda.time.Duration;
import org.joda.time.Instant;

public class PRStatsCollector {

    private int count, open, merged, mergeable, closed;
    private List<Long> daysToMerge = new ArrayList<Long>();
    private List<Long> daysOpen = new ArrayList<Long>();

    private static final Instant NOW = Instant.now();

    public void collect(PullRequest pr) {
        this.count++;

        if (pr.getMergedAt() != null) {
            this.merged++;
            long daysToMerge = calculateDays(toInstant(pr.getCreatedAt()), toInstant(pr.getMergedAt()));
            this.daysToMerge.add(daysToMerge);
        } else if (pr.getClosedAt() != null) {
            this.closed++;
        } else {
            this.open++;
            long daysOpen = calculateDays(toInstant(pr.getCreatedAt()), NOW);
            this.daysOpen.add(daysOpen);
        }

        if (pr.isMergeable() && !pr.isMerged()) {
            this.mergeable++;
        }
    }

    private Instant toInstant(Date date) {
        return new Instant(date);
    }

    private long calculateDays(Instant start, Instant end) {
        Duration duration = new Duration(start, end);
        return duration.getStandardDays();
    }

    public void output(PrintStream stream) {
        stream.println("Total Pull Requests: " + this.count);
        stream.println("Open Pull Requests: " + this.open);
        stream.println("Merged Pull Requests: " + this.merged);
        stream.println("Mergeable Open Pull Requests: " + this.mergeable);
        stream.println("Closed Pull Requests: " + this.closed);
        stream.println();
        outputStats(daysToMerge, "Days to Merge", stream);
        outputStats(daysOpen, "Days Open", stream);

    }

    private void outputStats(List<Long> values, String metric, PrintStream stream) {
        if (values.size() == 0)
            return;

        long min = Long.MAX_VALUE, max = 0, sum = 0;

        Map<Long, Long> cf = new HashMap<Long, Long>();
        for (long value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
            sum += value;

            if (cf.containsKey(value)) {
                cf.put(value, cf.get(value).longValue() + 1);
            } else {
                cf.put(value, 1l);
            }
        }

        stream.println("Minimum " + metric + ": " + min);
        stream.println("Maximum " + metric + ": " + max);
        stream.println("Average " + metric + ": " + (sum / values.size()));
        stream.println("Cumulative Frequency:");
        long total = 0, previous = -1;
        for (long i = 0; i <= max; i++) {
            if (cf.containsKey(i)) {
                total += cf.get(i).longValue();
            } else {
                continue;
            }
            if (total != previous) {
                stream.print(i + " " + metric + ": " + total);
                stream.println(" (" + cf.get(i).longValue() + ")");
                previous = total;
            }
        }
        stream.println();
    }
}