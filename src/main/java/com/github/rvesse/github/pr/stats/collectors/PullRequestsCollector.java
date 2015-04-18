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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.egit.github.core.PullRequest;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.PullRequestService;

public class PullRequestsCollector extends AbstractPullRequestCollector {

    private Map<Integer, UserCollector> users = new HashMap<Integer, UserCollector>();
    private Map<Integer, MergingUserCollector> mergingUsers = new HashMap<Integer, MergingUserCollector>();

    private boolean userStats, mergingUserStats;

    public PullRequestsCollector(boolean collectUserStats, boolean collectMergingUserStats) {
        this.userStats = collectUserStats;
        this.mergingUserStats = collectMergingUserStats;
    }

    @Override
    public void collect(GitHubClient client, PullRequest pr) {
        // Collect standard stats
        super.collect(client, pr);

        // Collect user stats
        if (this.userStats) {
            UserCollector userCollector = this.users.get(pr.getUser().getId());
            if (userCollector == null) {
                userCollector = new UserCollector(pr.getUser());
                userCollector.start();
                this.users.put(pr.getUser().getId(), userCollector);
            }
            userCollector.collect(client, pr);
        }

        // Collect merging user stats
        if (this.mergingUserStats) {
            if (pr.getMergedAt() != null) {
                User mergeUser = pr.getMergedBy();
                if (mergeUser == null) {
                    try {
                        RepositoryId repo = new RepositoryId(pr.getBase().getRepo().getOwner().getLogin(), pr.getBase()
                                .getRepo().getName());
                        pr = new PullRequestService(client).getPullRequest(repo, pr.getNumber());
                        mergeUser = pr.getMergedBy();
                    } catch (IOException e) {
                        // Ignore
                        System.out.println("Failed to obtain detailed information for PR #" + pr.getNumber());
                    }
                }
                if (mergeUser != null) {
                    MergingUserCollector mergeUserCollector = this.mergingUsers.get(mergeUser.getId());
                    if (mergeUserCollector == null) {
                        mergeUserCollector = new MergingUserCollector(pr.getMergedBy());
                        mergeUserCollector.start();
                        this.mergingUsers.put(pr.getMergedBy().getId(), mergeUserCollector);
                    }
                    mergeUserCollector.collect(client, pr);
                } else {
                    System.out.println("Unable to determine merging user for PR #" + pr.getNumber());
                }
            }
        }
    }

    @Override
    public void start() {
        super.start();

        this.users.clear();
        this.mergingUsers.clear();
    }

    @Override
    public void end() {
        super.end();

        for (AbstractUserPullRequestCollector userCollector : this.users.values()) {
            userCollector.end();
        }
        for (AbstractUserPullRequestCollector mergeUserCollector : this.mergingUsers.values()) {
            mergeUserCollector.end();
        }
    }

    public long getTotalUsers() {
        return this.users.size();
    }

    public List<UserCollector> getUserStats() {
        return new ArrayList<UserCollector>(this.users.values());
    }

    public long getTotalMergingUsers() {
        return this.mergingUsers.size();
    }

    public List<MergingUserCollector> getMergingUserStats() {
        return new ArrayList<MergingUserCollector>(this.mergingUsers.values());
    }
}
