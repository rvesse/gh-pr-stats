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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.egit.github.core.PullRequest;
import org.eclipse.egit.github.core.client.GitHubClient;

public class PullRequestsCollector extends AbstractPullRequestCollector {

    private Map<Integer, UserPullRequestsCollector> users = new HashMap<Integer, UserPullRequestsCollector>();

    @Override
    public void collect(GitHubClient client, PullRequest pr) {
        // Collect standard stats
        super.collect(client, pr);

        // Collect user stats
        UserPullRequestsCollector userCollector = this.users.get(pr.getUser().getId());
        if (userCollector == null) {
            userCollector = new UserPullRequestsCollector(client, pr.getUser());
            userCollector.start();
            this.users.put(pr.getUser().getId(), userCollector);
        }
        userCollector.collect(client, pr);
    }

    @Override
    public void start() {
        super.start();

        this.users.clear();
    }

    @Override
    public void end() {
        super.end();

        for (UserPullRequestsCollector userCollector : this.users.values()) {
            userCollector.end();
        }
    }

    public long getTotalUsers() {
        return this.users.size();
    }

    public List<UserPullRequestsCollector> getUserStats() {
        return new ArrayList<UserPullRequestsCollector>(this.users.values());
    }
}
