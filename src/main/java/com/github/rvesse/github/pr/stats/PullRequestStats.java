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
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.egit.github.core.PullRequest;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.PullRequestService;
import org.eclipse.egit.github.core.service.UserService;

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

    @Option(name = { "-u", "--user", "--username" }, title = "GitHubUsername", description = "Sets the GitHub username with which to authenticate, it is generally more secure to use OAuth2 tokens via the --oauth option")
    private String user;

    @Option(name = { "-p", "--pwd", "--password" }, title = "GitHubPassword", description = "Sets the GitHub password with which to authenticate.  If omitted the application will prompt you for it.", hidden = true)
    private String pwd;

    @Option(name = { "--oauth" }, title = "GitHubOAuth2Token", description = "Sets the GitHub OAuth2 Token to use for authenitcation")
    private String oauthToken;

    @Inject
    private HelpOption help = new HelpOption();

    @Inject
    private CommandMetadata metadata;

    public static void main(String[] args) {
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
        User user = new UserService(client).getUser();
        System.out.println("You have " + client.getRemainingRequests() + " GitHub API requests of "
                + client.getRequestLimit() + " remaining");

        PullRequestService prService = new PullRequestService(client);
        RepositoryId repoId = prepareRepositoryId();
        List<PullRequest> prs = prService.getPullRequests(repoId, "all");
        PRStatsCollector collector = new PRStatsCollector();
        for (PullRequest pr : prs) {
            System.out.println("Processing PR #" + pr.getNumber());
            collector.collect(pr);
        }
        System.out.println("You have " + client.getRemainingRequests() + " GitHub API requests of "
                + client.getRequestLimit() + " remaining");
        System.out.println();
        collector.output(System.out);

    }

    protected RepositoryId prepareRepositoryId() {
        if (this.repo.size() < 2) {
            System.err
                    .println("Insufficient repository information provided, you must provide both the owner and repository name");
            System.exit(1);
        }
        System.out.println("Generating PR Statistics for repository " + this.repo.get(0) + "/" + this.repo.get(1));
        return new RepositoryId(this.repo.get(0), this.repo.get(1));
    }

    protected void prepareCredentials(GitHubClient client) {
        if (this.oauthToken != null) {
            // OAuth 2 Authentication
            client.setOAuth2Token(this.oauthToken);
            System.out.println("Authenticating to GitHub using OAuth2");
        } else {
            // Username and Password authentication
            if (this.user == null) {
                System.out.print("Please enter your GitHub username [" + System.getProperty("user.name") + "]: ");
                this.user = System.console().readLine();
            }
            if (this.user == null) {
                this.user = System.getProperty("user.name");
            }
            if (this.pwd == null) {
                System.out.print("Please enter your GitHub password: ");
                this.pwd = new String(System.console().readPassword());
            }
            if (this.pwd == null) {
                System.err.println("Failed to specify a GitHub password");
                System.exit(1);
            }
            System.out.println("Authenticating to GitHub using Username and Password as user " + this.user);
            client.setCredentials(this.user, this.pwd);
        }
    }
}
