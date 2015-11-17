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

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.stat.Frequency;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.eclipse.egit.github.core.PullRequest;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.PullRequestService;
import org.eclipse.egit.github.core.service.UserService;

import com.github.rvesse.github.pr.stats.collectors.AbstractPullRequestCollector;
import com.github.rvesse.github.pr.stats.collectors.AbstractUserPullRequestCollector;
import com.github.rvesse.github.pr.stats.collectors.LongStatsCollector;
import com.github.rvesse.github.pr.stats.collectors.MergingUserCollector;
import com.github.rvesse.github.pr.stats.collectors.PullRequestsCollector;
import com.github.rvesse.github.pr.stats.collectors.UserCollector;
import com.github.rvesse.github.pr.stats.comparators.UserComparator;
import io.airlift.airline.Arguments;
import io.airlift.airline.Command;
import io.airlift.airline.HelpOption;
import io.airlift.airline.Option;
import io.airlift.airline.ParseArgumentsMissingException;
import io.airlift.airline.SingleCommand;
import io.airlift.airline.help.cli.CliCommandUsageGenerator;
import io.airlift.airline.model.CommandMetadata;

@Command(name = "pr-stats", description = "Generates Pull Request statistics for a GitHub repository")
public class PullRequestStats {

    @Arguments(title = { "Owner", "Repository" }, description = "Sets the repository for which to generate statistics", required = true)
    private List<String> repo = new ArrayList<String>();

    @Option(name = { "-u", "--user", "--username" }, title = "GitHubUsername", description = "Sets the GitHub username with which to authenticate, it is generally more secure to use OAuth2 tokens via the --oauth option.  If omitted the application will prompt you for it.")
    private String user;

    @Option(name = { "-p", "--pwd", "--password" }, title = "GitHubPassword", description = "Sets the GitHub password with which to authenticate, it is generally more secure to use OAuth2 tokens via the --oauth option.  If omitted the application will prompt you for it.", hidden = true)
    private String pwd;

    @Option(name = { "--oauth" }, title = "GitHubOAuth2Token", description = "Sets the GitHub OAuth2 Token to use for authenitcation")
    private String oauthToken;

    @Option(name = { "--user-summary" }, description = "When set includes a user summary in the statistics")
    private boolean userSummary = false;

    @Option(name = { "--user-stats" }, description = "When set includes detailed user statistics")
    private boolean userDetailedStats = false;

    @Option(name = { "--merge-summary" }, description = "When set includes a summary of merging users in the statistics i.e. details about who merges the pull requests")
    private boolean mergeSummary = false;

    @Option(name = { "--merge-stats" }, description = "When set includes detailed merging user statistics i.e. details about who merges the pull requests")
    private boolean mergeDetailedStats = false;

    @Option(name = { "-a", "--all" }, description = "When set includes all available statistics in the output")
    private boolean all = false;

    @Inject
    private HelpOption help = new HelpOption();

    @Inject
    private CommandMetadata metadata;

    public static void main(String[] args) throws MalformedURLException, IOException {
        SingleCommand<PullRequestStats> cmd = SingleCommand.singleCommand(PullRequestStats.class);
        try {
            PullRequestStats prStats = cmd.parse(args);
            prStats.run();
            System.exit(0);
        } catch (ParseArgumentsMissingException e) {
            if (ArrayUtils.contains(args, "--help") || ArrayUtils.contains(args, "-h")) {
                CliCommandUsageGenerator generator = new CliCommandUsageGenerator();
                try {
                    generator.usage(null, null, "pr-stats", cmd.getCommandMetadata());
                } catch (IOException e1) {
                    System.err.println("Failed to dispaly help: " + e1.getMessage());
                    e1.printStackTrace(System.err);
                    System.exit(1);
                }
                return;
            } else {
                System.err.println(e.getMessage());
                System.exit(1);
            }
        } catch (Throwable t) {
            System.err.println(t.getMessage());
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    public void run() throws IOException {
        if (help.showHelpIfRequested()) {
            CliCommandUsageGenerator generator = new CliCommandUsageGenerator();
            generator.usage(null, null, "pr-stats", this.metadata);
            return;
        }

        GitHubClient client = new GitHubClient();
        prepareCredentials(client);
        client.setUserAgent("GitHub PR Stats Bot/0.0.1 (+http://github.com/rvesse/gh-pr-stats.git)");

        // Get the user just to force us to make one request so we can get stats
        // about the remaining requests
        @SuppressWarnings("unused")
        User user = new UserService(client).getUser();
        System.out.println("You have " + client.getRemainingRequests() + " GitHub API requests of "
                + client.getRequestLimit() + " remaining");
        long start = client.getRemainingRequests();

        // Collect statistics for the pull requests
        PullRequestService prService = new PullRequestService(client);
        RepositoryId repoId = prepareRepositoryId();
        List<PullRequest> prs = prService.getPullRequests(repoId, "all");
        PullRequestsCollector collector = new PullRequestsCollector(this.userSummary || this.userDetailedStats
                || this.all, this.mergeSummary || this.mergeDetailedStats || this.all);
        collector.start();
        for (PullRequest pr : prs) {
            System.out.println("Processing PR #" + pr.getNumber());
            collector.collect(client, pr);
        }
        collector.end();

        // Inform the user about how many API requests were used
        System.out.println();
        System.out.println("You have " + client.getRemainingRequests() + " GitHub API requests of "
                + client.getRequestLimit() + " remaining");
        System.out.println("Generating statistics used " + (start - client.getRemainingRequests())
                + " GitHub API requests");
        System.out.println();

        // Output Stats
        // Basic stats
        outputBasicStatus(collector);
        System.out.println();

        // Age Stats
        outputAgeStats(collector.getDaysToMergeStats(), "Days to Merge", true);
        System.out.println();
        outputAgeStats(collector.getDaysOpenStats(), "Days Open", true);
        System.out.println();
        outputAgeStats(collector.getDaysToCloseStats(), "Days to Close", true);
        System.out.println();

        // User Stats
        List<UserCollector> userStats = collector.getUserStats();
        UserComparator<UserCollector> userComparator = new UserComparator<UserCollector>();
        if (this.userSummary || this.all) {
            System.out.println("Total Users: " + userStats.size());

            if (userStats.size() > 0) {
                UserCollector maxUser = Collections.max(userStats, userComparator);
                List<UserCollector> maxUsers = findEquivalent(userStats, maxUser, userComparator);
                UserCollector minUser = Collections.min(userStats, userComparator);
                List<UserCollector> minUsers = findEquivalent(userStats, minUser, userComparator);

                System.out.println("Max Pull Requests by User: " + maxUser.getTotal() + " " + maxUsers);
                System.out.println("Min Pull Requests by User: " + minUser.getTotal() + " " + minUsers);
                System.out.println("Average Pull Requests per User: " + (collector.getTotal() / userStats.size()));
            }
            System.out.println();
        }

        if (userStats.size() > 0 && (this.userDetailedStats || this.all)) {
            Collections.sort(userStats, userComparator);
            for (AbstractUserPullRequestCollector userCollector : userStats) {
                outputUserStats(userCollector);
                System.out.println();
            }
        }

        // Merging User Stats
        List<MergingUserCollector> mergingUserStats = collector.getMergingUserStats();
        UserComparator<MergingUserCollector> mergeUserComparator = new UserComparator<MergingUserCollector>();
        if (this.mergeSummary || this.all) {
            System.out.println("Total Merging Users: " + mergingUserStats.size());

            if (mergingUserStats.size() > 0) {
                MergingUserCollector maxUser = Collections.max(mergingUserStats, mergeUserComparator);
                List<MergingUserCollector> maxUsers = findEquivalent(mergingUserStats, maxUser, mergeUserComparator);
                MergingUserCollector minUser = Collections.min(mergingUserStats, mergeUserComparator);
                List<MergingUserCollector> minUsers = findEquivalent(mergingUserStats, minUser, mergeUserComparator);

                System.out.println("Max Pull Requests Merged by User: " + maxUser.getMerged() + " " + maxUsers);
                System.out.println("Min Pull Requests Merged by User: " + minUser.getMerged() + " " + minUsers);
                System.out.println("Average Pull Requests Merged per User: "
                        + (collector.getMerged() / mergingUserStats.size()));
            }
            System.out.println();
        }

        if (mergingUserStats.size() > 0 && (this.mergeDetailedStats || this.all)) {
            Collections.sort(mergingUserStats, mergeUserComparator);
            for (MergingUserCollector userCollector : mergingUserStats) {
                outputUserStats(userCollector);
                System.out.println();
            }
        }

    }

    private void outputBasicStatus(AbstractPullRequestCollector collector) {
        if (collector.getTotal() == 0)
            return;
        System.out.println("Total Pull Requests: " + collector.getTotal());
        if (collector.getMerged() > 0) {
            System.out.println("Merged Pull Requests: " + collector.getMerged());
        }
        if (collector.getOpen() > 0) {
            System.out.println("Open Pull Requests: " + collector.getOpen());
            System.out.println("Open Mergeable Pull Requests: " + collector.getOpenMergeable());
        }
        if (collector.getClosed() > 0) {
            System.out.println("Closed Pull Requests: " + collector.getClosed());
        }
        if (collector.getMerged() > 0) {
            outputPercentage(collector.getMergedPercentage(), "Merged Pull Requests");
        }
        if (collector.getOpen() > 0) {
            outputPercentage(collector.getOpenPercentage(), "Open Pull Requests");
        }
        if (collector.getClosed() > 0) {
            outputPercentage(collector.getClosedPercentage(), "Closed Pull Requests");
        }
    }

    private void outputAgeStats(LongStatsCollector collector, String metric, boolean includeFrequencies) {
        if (collector.getDescriptiveStats().getN() == 0)
            return;

        // Present stats
        System.out.println("Minimum " + metric + ": " + (long) collector.getDescriptiveStats().getMin());
        System.out.println("Maximum " + metric + ": " + (long) collector.getDescriptiveStats().getMax());
        System.out.println("Average (Arithmetic Mean) " + metric + ": "
                + (long) collector.getDescriptiveStats().getMean());
        System.out.println("Average (Geometric Mean) " + metric + ": "
                + (long) collector.getDescriptiveStats().getGeometricMean());

        if (includeFrequencies) {
            long[] modes = collector.getModes();
            if (modes != null)
                System.out.println("Most Popular " + metric + ": " + toPrintableList(modes));
            System.out.println("Cumulative Frequency:");
            Frequency freq = collector.getFrequencies();
            Percentile percentiles = collector.getPercentiles();
            outputPercentile(freq, percentiles, 25, metric);
            outputPercentile(freq, percentiles, 50, metric);
            outputPercentile(freq, percentiles, 75, metric);
            outputPercentile(freq, percentiles, 100, metric);
            outputCumulativePrecentage(freq, 7, metric);
            outputCumulativePrecentage(freq, 30, metric);
            outputCumulativePrecentage(freq, 90, metric);
            outputCumulativePrecentage(freq, 180, metric);
            outputCumulativePrecentage(freq, 365, metric);
        }
    }

    private <T extends AbstractUserPullRequestCollector> void outputUserStats(T collector) {
        System.out.println(collector.getUser().getLogin());
        outputBasicStatus(collector);
        outputPercentage(collector.getSelfMergedPercentage(), "Self Merged Pull Requests");
        outputAgeStats(collector.getDaysToMergeStats(), "Days to Merge", false);
        outputAgeStats(collector.getDaysOpenStats(), "Days Open", false);
        outputAgeStats(collector.getDaysToCloseStats(), "Days to Close", false);
    }

    private RepositoryId prepareRepositoryId() {
        if (this.repo.size() < 2) {
            System.err
                    .println("Insufficient repository information provided, you must provide both the owner and repository name");
            System.exit(1);
        }
        System.out.println("Generating PR Statistics for repository " + this.repo.get(0) + "/" + this.repo.get(1));
        return new RepositoryId(this.repo.get(0), this.repo.get(1));
    }

    private void prepareCredentials(GitHubClient client) {
        if (this.oauthToken != null) {
            // OAuth 2 Authentication
            client.setOAuth2Token(this.oauthToken);
            System.out.println("Authenticating to GitHub using OAuth2 Token");
        } else {
            // Username and Password authentication
            if (this.user == null) {
                System.out.print("Please enter your GitHub username [" + System.getProperty("user.name") + "]: ");
                this.user = System.console().readLine();
            }
            if (this.user == null || this.user.length() == 0) {
                this.user = System.getProperty("user.name");
            }
            if (this.pwd == null) {
                System.out.print("Please enter your GitHub password: ");
                this.pwd = new String(System.console().readPassword());
            }
            if (this.pwd == null || this.pwd.length() == 0) {
                System.err.println("Failed to specify a GitHub password");
                System.exit(1);
            }
            System.out.println("Authenticating to GitHub using Username and Password as user " + this.user);
            client.setCredentials(this.user, this.pwd);
        }
    }

    private void outputPercentage(double percentage, String metric) {
        System.out.println("Percentage " + metric + ": " + (int) (percentage * 100) + "%");
    }

    private void outputPercentile(Frequency freq, Percentile percentiles, int p, String metric) {
        long value = (long) percentiles.evaluate((double) p);
        System.out.println("  " + p + "% (" + value + " " + metric + "): " + (long) freq.getCumFreq(value));
    }

    private void outputCumulativePrecentage(Frequency freq, long value, String metric) {
        System.out.println("Under " + value + " " + metric + ": " + (long) (freq.getCumPct(value) * 100) + "%");
    }

    private String toPrintableList(long[] values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0)
                builder.append(',');
            builder.append(values[i]);
        }
        return builder.toString();
    }

    private <T extends AbstractUserPullRequestCollector> List<T> findEquivalent(List<T> users, T user,
            UserComparator<T> comparator) {
        List<T> equivUsers = new ArrayList<T>();

        for (T possUser : users) {
            if (comparator.compare(user, possUser) == 0)
                equivUsers.add(possUser);
        }
        return equivUsers;
    }
}
