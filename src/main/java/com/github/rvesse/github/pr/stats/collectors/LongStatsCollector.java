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

package com.github.rvesse.github.pr.stats.collectors;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.stat.Frequency;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.eclipse.egit.github.core.client.GitHubClient;

public class LongStatsCollector implements Collector<Long> {

    private List<Long> items = new ArrayList<Long>();
    private Frequency freq = new Frequency();
    private Percentile percentiles;
    private DescriptiveStatistics stats = new DescriptiveStatistics();

    @Override
    public void collect(GitHubClient client, Long item) {
        if (item == null)
            throw new IllegalArgumentException("item cannot be null");
        this.items.add(item);

        this.freq.incrementValue(item.longValue(), 1);
        this.stats.addValue(item.doubleValue());
    }

    @Override
    public void start() {
        // Reset
        this.items.clear();
        this.freq.clear();
        this.stats.clear();
        this.percentiles = null;
    }

    @Override
    public void end() {
        double[] ds = toDoubles();

        // Populate percentiles
        this.percentiles = new Percentile();
        this.percentiles.setData(ds);
    }

    protected double[] toDoubles() {
        double[] ds = new double[this.items.size()];
        for (int i = 0; i < ds.length; i++) {
            ds[i] = this.items.get(i).doubleValue();
        }
        return ds;
    }

    public DescriptiveStatistics getDescriptiveStats() {
        return this.stats;
    }

    public Frequency getFrequencies() {
        return this.freq;
    }

    public Percentile getPercentiles() {
        if (this.percentiles == null)
            throw new IllegalStateException("Cannot calculate percentiles until all stats are collected");
        return this.percentiles;
    }

    public long[] getModes() {
        if (this.percentiles == null)
            throw new IllegalStateException("Cannot calculate modes until all stats are collected");

        double[] ds = toDoubles();
        double[] dmodes = StatUtils.mode(ds);

        if (dmodes == null)
            return null;
        long[] modes = new long[dmodes.length];
        for (int i = 0; i < modes.length; i++) {
            modes[i] = (long) dmodes[i];
        }
        return modes;
    }
}
