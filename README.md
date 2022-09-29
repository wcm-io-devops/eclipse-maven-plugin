<img src="https://wcm.io/images/favicon-16@2x.png"/> Eclipse Maven Plugin
======
[![Build](https://github.com/wcm-io-devops/eclipse-maven-plugin/workflows/Build/badge.svg?branch=develop)](https://github.com/wcm-io-devops/eclipse-maven-plugin/actions?query=workflow%3ABuild+branch%3Adevelop)
[![Maven Central](https://img.shields.io/maven-central/v/io.wcm.devops.maven.plugins/eclipse-maven-plugin)](https://repo1.maven.org/maven2/io/wcm/devops/maven/plugins/eclipse-maven-plugin)

The Eclipse Plugin is used to generate Eclipse IDE files (.project, .classpath and the .settings folder) from a POM.

This is a fork of the original [Maven Eclipse Plugin](https://maven.apache.org/plugins/maven-eclipse-plugin/) which [was retired end of 2015](http://mail-archives.apache.org/mod_mbox/maven-dev/201510.mbox/%3Cop.x55dxii1kdkhrr%40robertscholte.dynamic.ziggo.nl%3E) in favor of the m2e Eclipse integration.

In our wcm.io and other Maven-based projects we usually use both m2e Integration and the Eclipse Maven Plugin. The Eclipse Maven Plugin is used to generate project-specific eclipse settings files and further files for Checkstyle, Findbugs and PMD based on a global build tools artifact defined a parent POM like `io.wcm.maven:io.wcm.maven.global-parent`, see [Global Parent](https://wcm.io/tooling/maven/global-parent.html) documentation for details.

So we maintain a fork of the original plugin here and publish it under Apache 2.0 license within the wcm.io DevOps project.

Documentation:

* [wcm.io DevOps Eclipse Maven Plugin Documentation](https://devops.wcm.io/maven/plugins/eclipse-maven-plugin/)
* [Changes since the original Maven Eclipse Plugin 2.10.](https://devops.wcm.io/maven/plugins/eclipse-maven-plugin/changes-report.html)

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
