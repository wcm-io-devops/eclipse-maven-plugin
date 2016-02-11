<img src="http://wcm.io/images/favicon-16@2x.png"/> Maven Eclipse Plugin
======

The Eclipse Plugin is used to generate Eclipse IDE files (.project, .classpath and the .settings folder) from a POM.

This is a fork of the original [Maven Eclipse Plugin](https://maven.apache.org/plugins/maven-eclipse-plugin/) which [was retired end of 2015](http://mail-archives.apache.org/mod_mbox/maven-dev/201510.mbox/%3Cop.x55dxii1kdkhrr%40robertscholte.dynamic.ziggo.nl%3E) in favor of the m2e Eclipse integration.

In our wcm.io and other Maven-based projects we usually use both m2e Integration and the Maven Eclipse Plugin. The Maven Eclipse Plugin is used to generate project-specific eclipse settings files and further files for Checkstyle, Findbugs and PMD based on a global build tools artifact defined a parent POM like `io.wcm.maven:io.wcm.maven.global-parent`, see [Global Parent](http://wcm.io/tooling/maven/global-parent.html) documentation for details.

So we maintain a fork of the original plugin here and publish it unter Apache 2.0 license within the wcm.io DevOps project.

This fork includes the patch from [MECLIPSE-641](https://issues.apache.org/jira/browse/MECLIPSE-641) which was never applied to the original code base, but is important for pregenerating the eclipse project settings.
