# GitHub Pull Request Stats

A simple tool for gathering pull request statistics on a GitHub repository

# Setup

Clone the code:

    > git clone https://github.com/rvesse/gh-pr-stats.git
    
Build the code:

    > cd gh-pr-stats
    > mvn clean package
    
## Creating an Personal Access Token

You can optionally create an personal access token in order to use OAuth2 authentication with this application.  To do this follow these steps:

* Login to your GitHub Account
* Go to the `Settings` page
* Click on the `Developer Settings` in the left hand navigation
* Under `Personal Access Tokens` click `Generate New Token`
    * This token only needs the `public_repo` privileges.
    * You may wish to add the `repo` privilege if you wish to analyse private repositories
* Save the generated token somewhere for your future use as GitHub will not show you the token again

# Generating Stats

To generate statistics do the following:

    > ./pr-stats owner repo
    
This will prompt you for your username and password and then use the GitHub API to generate statistics for the `owner/repo` repository

It is generally recommended to use OAuth2 authentication by generating a Personal Access Token as detailed above.  With this you can generate statistics like so:

    > ./pr-stats --oauth token owner repo
    
Provided the `token` is a valid access token you will not need to provide a username or password to authenticate.

Or if you have stored the token in a file you can do the following:

    > ./pr-stats --oauth-file file owner repo

## Available Stats

By default the tool just generates summary statistics about the number of pull requests by category (merged, open, closed) and their ages.

You can also generate user statistics by adding the `--user-summary` or `--user-stats` options to the command line invocation.  The former
generates summary statistics about the number of users who have submitted pull requests, the latter adds detailed statistics for each user.

# License

This tool is licensed under the Apache License 2.0, see the `LICENSE` file in this repository for details
