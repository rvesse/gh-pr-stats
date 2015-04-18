package com.github.rvesse.github.pr.stats.collectors;

import org.eclipse.egit.github.core.PullRequest;
import org.eclipse.egit.github.core.User;

public class MergingUserCollector extends AbstractUserPullRequestCollector {

    public MergingUserCollector(User user) {
        super(user);
    }

    @Override
    protected boolean shouldCollect(PullRequest pr) {
        return pr.getMergedAt() != null && pr.getMergedBy().getId() == this.getUser().getId();
    }

}
