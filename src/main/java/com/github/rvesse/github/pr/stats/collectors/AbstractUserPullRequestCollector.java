package com.github.rvesse.github.pr.stats.collectors;

import org.eclipse.egit.github.core.PullRequest;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;

public class AbstractUserPullRequestCollector extends AbstractPullRequestCollector {

    private User user;
    private long selfMerged;

    public AbstractUserPullRequestCollector(User user) {
        this.user = user;
    }
    
    @Override
    public void start() {
        super.start();
        this.selfMerged = 0;
    }
    
    @Override
    public void collect(GitHubClient client, PullRequest pr) {
        if (shouldCollect(pr))
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

    @Override
    public String toString() {
        return this.user.getLogin();
    }

    protected boolean shouldCollect(PullRequest pr) {
        return true;
    }

    public long getSelfMerged() {
        return this.selfMerged;
    }

    public double getSelfMergedPercentage() {
        return calcPercentage(this.selfMerged);
    }

}