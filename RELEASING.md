# A check list of things to be done before a release.

Things to do before issuing a new release:

* Ensure all new Configuration parameters are documented

* Use your IDE to regenerate equals/hashCode for ConfigurationImpl (this is just much safer than trying to inspect the code).

* Ensure all public API classes have a proper Javadoc.

* Update docs/user-manual/en/versions.md to include appropriate release notes and upgrade instructions. See previous
  entries for guidance.

* Bump the version numbers in example and test poms to the next release version. e.g. 2.0.0

* Build the release locally: mvn clean install -Prelease

* Test the standalone release (this should be done on windows as well as linux):
1. Unpack the distribution zip or tar.gz
2. Start and stop the server
3. Run the examples (follow the instructions under examples/index.html)
5. Check the manuals have been created properly
6. Check the javadocs are created correctly (including the diagrams)

If everything is successful follow these next steps to build and publish artifacts to Nexus and send out a release vote.

## Key to Sign the Release

If you don't have a key to sign the release artifacts, follow the instruction at [apache release signing](https://infra.apache.org/release-signing.html). 

Ensure that your key is listed at https://dist.apache.org/repos/dist/release/activemq/KEYS.
If not, generate the key information, e.g.:

```
gpg --list-sigs username@apache.org > /tmp/key; gpg --armor --export username@apache.org >> /tmp/key
```

Then send the key information in `/tmp/key` to `private@activemq.apache.org` so it can be added.

Ensure that your key is listed at https://home.apache.org/keys/committer/.
If not add your key id (available via `gpg --fingerprint <key>`) to the `OpenPGP Public Key Primary Fingerprint` field at 
https://id.apache.org/. Note: this is just the key id, not the whole fingerprint.

## Checking out a new empty git repository

Before starting make sure you clone a brand new git as follows as the release plugin will use the upstream for pushing the tags:

```sh
git clone git://github.com/apache/activemq-artemis.git
cd activemq-artemis
git remote add upstream https://gitbox.apache.org/repos/asf/activemq-artemis.git
```

If your git `user.email` and/or `user.name` are not set globally then you'll need to set these on the newly clone 
repository as they will be used during the release process to make commits to the upstream repository, e.g.:

```
git config user.email "username@apache.org"
git config user.name "FirstName LastName"
```

This should be the same `user.email` and `user.name` you use on your main repository.

## Running the release

You will have to use this following maven command to perform the release:

```sh
mvn clean release:prepare -DautoVersionSubmodules=true -Prelease
```

You could optionally set `pushChanges=false` so the version commit and tag won't be pushed upstream (you would have to do it yourself):

```sh
mvn clean release:prepare -DautoVersionSubmodules=true -DpushChanges=false -Prelease
```

When prompted make sure the next is a major release. Example:

```
[INFO] Checking dependencies and plugins for snapshots ...
What is the release version for "ActiveMQ Artemis Parent"? (org.apache.activemq:artemis-pom) 2.19.0: :
What is SCM release tag or label for "ActiveMQ Artemis Parent"? (org.apache.activemq:artemis-pom) artemis-pom-2.19.0: : 2.19.0
What is the new development version for "ActiveMQ Artemis Parent"? (org.apache.activemq:artemis-pom) 2.19.1-SNAPSHOT: : 2.20.0-SNAPSHOT
```

Otherwise snapshots will be created at 2.19.1-SNAPSHOT and forgotten. (Unless we ever release 2.19.1 on that example).

For more information look at the prepare plugin:

- https://maven.apache.org/maven-release/maven-release-plugin/prepare-mojo.html#pushChanges

If you set `pushChanges=false` then you will have to push the changes manually.  The first command is to push the commits
which are for changing the `&lt;version>` in the pom.xml files, and the second push is for the tag, e.g.:

```sh
git push upstream
git push upstream <version>
```

## Uploading to nexus

Ensure that your environment is ready to deploy to the ASF Nexus repository as described at
[Publishing Maven Artifacts](https://infra.apache.org/publishing-maven-artifacts.html)

Copy the file release.properties, that is generated at the root of the project during the release,
before starting the upload. You could need it if the upload fails.

To upload it to nexus, perform this command:

```sh
mvn release:perform -Prelease
```

Note: this can take quite a while depending on the speed for your Internet connection.
If the upload fails or is interrupted, remove the incomplete repository
using the "Drop" button on [Nexus website](https://repository.apache.org/#stagingRepositories).
Before starting the upload again, check the release.properties at the root of the project.

### Resuming release upload

If something happened during the release upload to nexus, you may need to eventually redo the upload.
Remove the incomplete repository using the "Drop" button on [Nexus website](https://repository.apache.org/#stagingRepositories).
Before starting the upload again, check the release.properties at the root of the project.

There is a release.properties file that is generated at the root of the project during the release.
In case you want to upload a previously tagged release, add this file as follows:

- release.properties
```
scm.url=scm:git:https://github.com/apache/activemq-artemis.git
scm.tag=1.4.0
```


## Closing the staging repository

Give the staging repository contents a quick inspection using the content navigation area, then proceed to close the
staging repo using the "Close" button on Nexus website, locking it from further modification and exposing its contents
at a staging URL to allow testing. Set a description such as "ActiveMQ Artemis <version> (RC1)" while closing.

## Stage the release to the dist dev area

Use the closed staging repo contents to populate the dist dev svn area
with the official release artifacts for voting. Use the script already present
in the repo to download the files and populate a new ${CURRENT-RELEASE} dir:

```sh
svn co https://dist.apache.org/repos/dist/dev/activemq/activemq-artemis/
cd activemq-artemis
./prepare-release.sh https://repository.apache.org/content/repositories/orgapacheactivemq-${NEXUS-REPO-ID} ${CURRENT-RELEASE}
```
Give the files a check over and commit the new dir and start a vote if all looks well. 

```bash
svn add <version>
svn commit
```

Old staged releases can be cleaned out periodically.


## Generate the git-report

Please, include the git-commit-report as part of the release process. To generate it, follow these steps:

- Download [jira-git-report](https://github.com/rh-messaging/jira-git-report)
- Execute the following command:
```bash
$ git clone https://github.com/rh-messaging/jira-git-report.git
$ cd jira-git-report
$ mvn compile assembly:single
$ cd target
$ java -jar jira-git-0.1.SNAPSHOT-jar-with-dependencies.jar artemis <checkoutDirectory>/activemq-artemis <checkoutDirectory>/artemis-website commit-report-<version>.html <previous-version> <version> true
```

real example used on [2.6.1](http://activemq.apache.org/artemis/commit-report-2.6.1.html):
```bash
$ java -jar 
java -jar jira-git-0.1.SNAPSHOT-jar-with-dependencies.jar artemis release-work/activemq-artemis release-work/activemq-website/src/components/artemis/download/commit-report-2.16.0.html 2.15.0 2.16.0 true
```
- This will parse all the git commits between the previous release, and current release tags while looking at current JIRA status.


## Cleanup JIRA

Use the git-report to do some JIRA cleanup making sure your commits and JIRA are accurate:
- Close as done all JIRA related at the commits included in the git-report
  but exclude all JIRA related to a commit reverted by a commit included in the same git-report.
  You can execute a bulk change on all JIRA related at the commits included in the git-report
  using the link `JIRAS on this Report` at the bottom of the report.
- Ensure that the version next the version being released exists checking the [ActiveMQ Artemis Releases page](https://issues.apache.org/jira/projects/ARTEMIS?selectedItem=com.atlassian.jira.jira-projects-plugin:release-page).
  If not, you need an administrator account to create it using the `Manage Versions` button at the [ActiveMQ Artemis Releases page](https://issues.apache.org/jira/projects/ARTEMIS?selectedItem=com.atlassian.jira.jira-projects-plugin:release-page)
- Move all JIRA not closed to the next release setting the `Fix Version` field.
- Regenerate the report once you cleared JIRA, to check your job.


## Locate Release Notes

1. Use the previous report to cleanup JIRA
2. Go to the "Releases" page for the Artemis JIRA project: https://issues.apache.org/jira/projects/ARTEMIS?selectedItem=com.atlassian.jira.jira-projects-plugin:release-page
3. Click on the version being released.
4. Click the "Release Notes" link near the top of the page.
5. Grab the URL to put into the VOTE email.


## Send Email

Once all the artifacts are stage then send an email to `dev@activemq.apache.org`.  It should have a subject like `[VOTE] 
Apache ActiveMQ Artemis <version>`.  Here is an example for the body of the message:

```
I would like to propose an Apache ActiveMQ Artemis <version> release.

We added these new features as part of <version>:

[ARTEMIS-123] - Great new feature 1
[ARTEMIS-456] - Great new feature 2

The release notes can be found here:
https://issues.apache.org/jira/secure/ReleaseNote.jspa?version=<releaseNotesVersionID>&projectId=12315920

Ths git commit report is here:
https://activemq.apache.org/components/artemis/download/commit-report-<version>

Source and binary distributions can be found here:
https://dist.apache.org/repos/dist/dev/activemq/activemq-artemis/<version>/

The Maven repository is here:
https://repository.apache.org/content/repositories/orgapacheactivemq-<repoID>

In case you want to give it a try with the maven repo on examples:
https://activemq.apache.org/components/artemis/documentation/hacking-guide/validating-releases.html

The source tag:
https://gitbox.apache.org/repos/asf/activemq-artemis.git;a=tag;h=refs/tags/<version>

I will update the website after the vote has passed.


[ ] +1 approve this release
[ ] +0 no opinion
[ ] -1 disapprove (and reason why)

Here's my +1
```


## Voting process

Rules for the Apache voting process are stipulated [here](https://www.apache.org/foundation/voting.html).

Assuming the vote is successful send a email with a subject like `[RESULT] [VOTE] Apache ActiveMQ Artemis <version>` 
informing the list about the voting results, e.g.:

```
Results of the Apache ActiveMQ Artemis <version> release vote.

Vote passes with 4 votes, 3 binding and 1 non-binding.

The following votes were received:

Binding:
+1 John Doe
+1 Jane Doe
+1 Bill Smith

Non Binding:
+1 Mike Williams

Thank you to everyone who contributed and took the time to review the
release candidates and vote.

I'll update the website as soon as the mirrors are updated (I will
wait 24 hours after I update SVN).

Regards
```


## Promote artifacts to the dist release area

After a successful vote, populate the dist release area using the staged
files from the dist dev area to allow them to mirror. Note: this can only
be done by a PMC member.

Use the script already present in the repo to copy the staged files
from the dist dev area to the dist release area:

```sh
svn co https://dist.apache.org/repos/dist/dev/activemq/activemq-artemis/
cd activemq-artemis
./promote-release.sh ${CURRENT-RELEASE}
```

Good mirror coverage can take up to 24 hours. Mirror status can be viewed [here](https://www.apache.org/mirrors/).


## Release the staging repo

Go to https://repository.apache.org/#stagingRepositories and click the "Release" button.


## Web site update:

Wait 24 hours after updating SVN to get a good mirror coverage, before proceeding.
Mirror status can be viewed [here](https://www.apache.org/mirrors/).

Clone the website repository from:

```sh
git clone https://gitbox.apache.org/repos/asf/activemq-website.git
```

Once the mirrors are up-to-date then update the following:
1. Copy `src/components/artemis/download/release-notes-<old-version>.md` to `src/components/artemis/download/release-notes-<new-version>.md`
   and update it deleting the existing list of bugs, features, improvements, etc. and replacing it
   with the HTML from the bottom of the release notes url you sent out with your VOTE email (appending `&styleName=Text`).
2. Copy `src/_artemis_releases/artemis-<old-version>-release.md` to `src/_artemis_releases/artemis-<new-version>-release.md`. Update the versions and dates.
3. Update the _artemis_ list within the `src/_data/current_releases.yml` file if needed.
4. Copy `src/components/artemis/documentation/latest` to `src/components/artemis/documentation/<old-version>`.
5. Create `src/components/artemis/documentation/latest` and copy these files into it:
    1. the contents of user-manual from `apache-artemis-<new-version>/web/user-manual`
    2. book.pdf version of user-manual (generated from the new version sources with the command `gitbook pdf`)
    3. book.epub version of user-manual (generated from the new version sources with the command `gitbook epub`)
    4. book.mobi version of user-manual (generated from the new version sources with the command `gitbook mobi`)
6. Create `src/components/artemis/documentation/hacking-guide` and copy these files into it: 
    1. the contents of user-manual from `apache-artemis-<new-version>/web/hacking-guide`
    2. book.pdf version of hacking-guide (generated with `gitbook pdf`)        
7. Copy `src/components/artemis/documentation/javadocs/javadoc-latest` to `src/components/artemis/documentation/javadocs/javadoc-<old-version>`.
8. Create `src/components/artemis/documentation/javadocs/javadoc-latest` and copy the contents of `apache-artemis-<new-version>/web/api` into it.
   
Run `git add` for all the added directories & files and then `git commit -m "updates for <version> release"`.
Once pushed, the changes should be published automatically by the `jekyll_websites` builder of the [apache buildbot](https://ci2.apache.org/#/builders).

Note: Generating PDFs, etc. with gitbook requires the installation of [Calibre](https://calibre-ebook.com).
You can install this manually, but it is recommended you use your platform's package management to install
(e.g. `sudo apt-get install calibre`).


## Send announcement to user list

Once the website is updated then send an email with a subject like `[ANNOUNCE] ActiveMQ Artemis <version> Released` to 
`user@activemq.apache.org` & `dev@activemq.apache.org`, e.g.:

```
I'm pleased to announce the release of ActiveMQ Artemis <version>.

Downloads are now available at:
https://activemq.apache.org/components/artemis/download/

For a complete list of updates:
https://activemq.apache.org/components/artemis/download/release-notes-<version>

I would like to highlight these improvements:

- Summary of feature 1:
<link to relevant documentation>

- Summary of feature 2:
<link to relevant documentation>

- Summary of feature 3:
<link to relevant documentation>

As usual it contains a handful of bug fixes and other improvements.

Many thanks for all the contributors to this release.
```


## Cleaning older releases out of the dist release area

Only the current releases should be mirrored. Older releases must be cleared:

```sh
svn rm -m "clean out older release" https://dist.apache.org/repos/dist/release/activemq/activemq-artemis/${OLD-RELEASE}
```
Any links to them on the site must be updated to reference the ASF archive instead at
https://archive.apache.org/dist/activemq/activemq-artemis/ (this should already have been done implicitly in the previous step).

Ensure old releases are only removed after the site is updated in order to avoid broken links.


## Apache Guide

For more information consult the apache guide at this address:

* http://www.apache.org/dev/publishing-maven-artifacts.html

