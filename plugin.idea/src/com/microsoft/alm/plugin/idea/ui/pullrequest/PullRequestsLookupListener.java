// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.plugin.idea.ui.pullrequest;

import com.microsoft.alm.plugin.authentication.AuthHelper;
import com.microsoft.alm.plugin.context.ServerContext;
import com.microsoft.alm.plugin.context.ServerContextManager;
import com.microsoft.alm.plugin.idea.utils.IdeaHelper;
import com.microsoft.alm.plugin.operations.Operation;
import com.microsoft.alm.plugin.operations.PullRequestLookupOperation;

public class PullRequestsLookupListener implements Operation.Listener {

    private final VcsPullRequestsModel model;
    private ServerContext context;
    private PullRequestLookupOperation activeOperation;

    public PullRequestsLookupListener(final VcsPullRequestsModel model) {
        assert model != null;
        this.model = model;
    }

    public void loadPullRequests(final ServerContext context) {
        this.context = context;
        final PullRequestLookupOperation activeOperation = new PullRequestLookupOperation(context);
        loadPullRequests(activeOperation);
    }

    private void loadPullRequests(final PullRequestLookupOperation activeOperation) {
        assert activeOperation != null;
        this.activeOperation = activeOperation;
        this.activeOperation.addListener(this);
        this.activeOperation.doWorkAsync(Operation.EMPTY_INPUTS);
    }

    @Override
    public void notifyLookupStarted() {
        IdeaHelper.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                model.setLoading(true);
                model.setLoadingErrors(false);
                model.clearPullRequests();
            }
        });
    }

    @Override
    public void notifyLookupCompleted() {
        operationDone();
        IdeaHelper.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                model.setLoading(false);
            }
        });
    }

    @Override
    public void notifyLookupResults(final Operation.Results results) {
        final PullRequestLookupOperation.PullRequestLookupResults lookupResults = (PullRequestLookupOperation.PullRequestLookupResults) results;
        if (lookupResults.isCancelled()) {
            operationDone();
            IdeaHelper.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    model.setLoading(false);
                }
            });
        } else if (lookupResults.hasError()) {
            IdeaHelper.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (AuthHelper.isNotAuthorizedError(lookupResults.getError())) {
                        model.setAuthenticated(false);
                        final ServerContext newContext = ServerContextManager.getInstance().updateAuthenticationInfo(context.getUri().toString());
                        if (newContext != null) {
                            //try reloading the pull requests with new context and authentication info
                            model.loadPullRequests(newContext);
                        } else {
                            //user cancelled login, don't retry
                        }
                    } else {
                        model.setLoadingErrors(true);
                    }
                }
            });
        } else {
            IdeaHelper.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    model.appendPullRequests(lookupResults.getPullRequests(), lookupResults.getScope());
                }
            });
        }
    }

    private void operationDone() {
        if (activeOperation != null) {
            activeOperation.removeListener(this);
            activeOperation = null;
        }
    }

    public void terminateActiveOperation() {
        if (activeOperation != null) {
            activeOperation.removeListener(this);
            activeOperation.cancel();
            activeOperation = null;
        }
    }
}
