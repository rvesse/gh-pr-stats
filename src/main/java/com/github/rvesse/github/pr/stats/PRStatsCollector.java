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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.stat.Frequency;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.eclipse.egit.github.core.PullRequest;
import org.joda.time.Duration;
import org.joda.time.Instant;

public class PRStatsCollector {

    private int count, open, merged, mergeable, closed;
    private List<Long> daysToMerge = new ArrayList<Long>();
    private List<Long> daysOpen = new ArrayList<Long>();
    private Map<String, Long> users = new HashMap<String, Long>();

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

        if (pr.isMergeable() && pr.getMergedAt() == null) {
            this.mergeable++;
        }

        String user = pr.getUser().getLogin();
        if (users.containsKey(user)) {
            users.put(user, users.get(user).longValue() + 1);
        } else {
            users.put(user, 1l);
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
        // Basic stats
        stream.println("Total Pull Requests: " + this.count);
        stream.println("Open Pull Requests: " + this.open);
        stream.println("Merged Pull Requests: " + this.merged);
        stream.println("Mergeable Open Pull Requests: " + this.mergeable);
        stream.println("Closed Pull Requests: " + this.closed);
        stream.println();
        outputPercentage(this.open, this.count, "Open Pull Requests", stream);
        outputPercentage(this.merged, this.count, "Merged Pull Requests", stream);
        outputPercentage(this.closed, this.count, "Closed Pull Requests", stream);
        stream.println();

        // Age stats
        outputStats(daysToMerge, "Days to Merge", stream);
        outputStats(daysOpen, "Days Open", stream);
        stream.println();

        // User stats
        stream.println("Total Users: " + this.users.size());
        long max = 0, prevMax = 0, min = Long.MAX_VALUE, prevMin = Long.MAX_VALUE, sum = 0;
        List<String> maxUsers = new ArrayList<String>();
        List<String> minUsers = new ArrayList<String>();
        for (Entry<String, Long> prsByUser : this.users.entrySet()) {
            long num = prsByUser.getValue().longValue();
            max = Math.max(num, max);
            if (prevMax != max) {
                prevMax = max;
                maxUsers.clear();
            }
            if (max == num) {
                maxUsers.add(prsByUser.getKey());
            }
            min = Math.min(num, min);
            if (prevMin != min) {
                prevMin = min;
                minUsers.clear();
            }
            if (min == num) {
                minUsers.add(prsByUser.getKey());
            }
            sum += num;
        }
        stream.println("Max Pull Requests by User(s): " + max + " " + maxUsers);
        stream.println("Min Pull Requests by User(s): " + min + " " + minUsers);
        stream.println("Average Pull Request per User: " + (sum / users.size()));
        stream.println();
        List<Entry<String, Long>> users = new ArrayList<Map.Entry<String, Long>>(this.users.entrySet());
        Collections.sort(users, new SortEntriesByCount<String>());
        stream.println("Pull Requests per User:");
        for (Entry<String, Long> prByUser : users) {
            stream.println("" + prByUser.getKey() + ": " + prByUser.getValue().longValue());
        }
        stream.println();

    }

    private void outputStats(List<Long> values, String metric, PrintStream stream) {
        if (values.size() == 0)
            return;

        //Map<Long, Long> cf = new HashMap<Long, Long>();
        Frequency freq = new Frequency();
        Percentile percentiles = new Percentile();
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (long value : values) {
            freq.incrementValue(value, 1);
            stats.addValue((double)value);
            
//            if (cf.containsKey(value)) {
//                cf.put(value, cf.get(value).longValue() + 1);
//            } else {
//                cf.put(value, 1l);
//            }
        }
        double[] ds = toDoubles(values);
        percentiles.setData(ds);
        long mode = (long)StatUtils.mode(ds)[0];

        stream.println("Minimum " + metric + ": " + (long)stats.getMin());
        stream.println("Maximum " + metric + ": " + (long)stats.getMax());
        stream.println("Average (Arithmetic Mean) " + metric + ": " + (long)stats.getMean());
        stream.println("Average (Geometric Mean) " + metric + ": " + (long)stats.getGeometricMean());
        stream.println("Mode " + metric + ": " + mode);
        stream.println("Cumulative Frequency:");
        outputPercentile(freq, percentiles, 25, metric, stream);
        outputPercentile(freq, percentiles, 50, metric, stream);
        outputPercentile(freq, percentiles, 75, metric, stream);
        outputPercentile(freq, percentiles, 100, metric, stream);
//        long total = 0, previous = -1;
//        for (long i = 0; i <= max; i++) {
//            if (cf.containsKey(i)) {
//                total += cf.get(i).longValue();
//            } else {
//                continue;
//            }
//            if (total != previous) {
//                stream.print(i + " " + metric + ": " + total);
//                stream.print(" (" + cf.get(i).longValue());
//                int percentage = (int) (((double) total / (double) values.size()) * 100);
//                stream.print(" " + percentage + "%");
//                stream.println(")");
//                previous = total;
//            }
//        }
        stream.println();
    }

    private void outputPercentage(long num, long total, String metric, PrintStream stream) {
        stream.println("Percentage " + metric + ": " + (int) (((double) num / (double) total) * 100) + "%");
    }

    private void outputPercentile(Frequency freq, Percentile percentiles, int p, String metric, PrintStream stream) {
        long value = (long) percentiles.evaluate((double)p);
        stream.println("  " + p + "% (" + value + " " + metric + "): " + (long) freq.getCumFreq(value));
    }

    private double[] toDoubles(List<Long> values) {
        double[] ds = new double[values.size()];
        for (int i = 0; i < ds.length; i++) {
            ds[i] = values.get(i);
        }
        return ds;
    }
}
