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

import org.eclipse.egit.github.core.PullRequest;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;

public class UserPullRequestsCollector extends AbstractPullRequestCollector {

    private User user;
    private long selfMerged;

    public UserPullRequestsCollector(GitHubClient client, User user) {
        this.user = user;
    }

    @Override
    public void start() {
        super.start();
        this.selfMerged = 0;
    }

    @Override
    public void collect(GitHubClient client, PullRequest pr) {
        if (pr.getUser().getId() != this.user.getId())
            return;

        // Collect standard stats
        super.collect(client, pr);

        if (pr.getMergedAt() != null) {
            User u = pr.getUser();
            User m = pr.getMergedBy();
            if (pr.getMergedBy() != null) {
                if (u.getId() == m.getId()) {
                    this.selfMerged++;
                }
            }
        }
    }

    public User getUser() {
        return this.user;
    }

    public long getSelfMerged() {
        return this.selfMerged;
    }

    public double getSelfMergedPercentage() {
        return calcPercentage(this.selfMerged);
    }

    @Override
    public String toString() {
        return this.user.getLogin();
    }
}
