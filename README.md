<img src="http://wcm.io/images/favicon-16@2x.png"/> Eclipse Maven Plugin
======
[![Build Status](https://travis-ci.org/wcm-io-devops/eclipse-maven-plugin.png?branch=develop)](https://travis-ci.org/wcm-io-devops/eclipse-maven-plugin)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.wcm.devops.maven.plugins/eclipse-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.wcm.devops.maven.plugins/eclipse-maven-plugin)

The Eclipse Plugin is used to generate Eclipse IDE files (.project, .classpath and the .settings folder) from a POM.

This is a fork of the original [Maven Eclipse Plugin](https://maven.apache.org/plugins/maven-eclipse-plugin/) which [was retired end of 2015](http://mail-archives.apache.org/mod_mbox/maven-dev/201510.mbox/%3Cop.x55dxii1kdkhrr%40robertscholte.dynamic.ziggo.nl%3E) in favor of the m2e Eclipse integration.

In our wcm.io and other Maven-based projects we usually use both m2e Integration and the Eclipse Maven Plugin. The Eclipse Maven Plugin is used to generate project-specific eclipse settings files and further files for Checkstyle, Findbugs and PMD based on a global build tools artifact defined a parent POM like `io.wcm.maven:io.wcm.maven.global-parent`, see [Global Parent](http://wcm.io/tooling/maven/global-parent.html) documentation for details.

So we maintain a fork of the original plugin here and publish it under Apache 2.0 license within the wcm.io DevOps project.

Changes since the original Maven Eclipse Plugin 2.10:

* Patch from [MECLIPSE-641](https://issues.apache.org/jira/browse/MECLIPSE-641) which was never applied to the original code base, but is important for generating the eclipse project settings (since 3.0.0)
* Add support for `filtering` property on additionalConfig files (since 3.1.0)
* Mark test source folders and test dependencies as "test" for Eclipse 4.8 Photon (since 3.1.0)

To use this in your projects update all your POMs to use

```xml
<plugin>
  <groupId>io.wcm.devops.maven.plugins</groupId>
  <artifactId>eclipse-maven-plugin</artifactId>
  <version>3.1.0</version>
</plugin>
```

instead of

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-eclipse-plugin</artifactId>
</plugin>
```
