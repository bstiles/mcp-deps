(defproject mcp-deps "1.0.0-SNAPSHOT"
  :description "Provides facilities for resolving dependencies."
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.sonatype.aether/aether-api "1.8"]
                 [org.sonatype.aether/aether-util "1.8"]
                 [org.sonatype.aether/aether-impl "1.8"]
                 [org.sonatype.aether/aether-connector-wagon "1.8"]
                 [org.apache.maven/maven-aether-provider "3.0.1"]
                 [org.apache.maven.wagon/wagon-file "1.0-beta-7"]
                 [org.apache.maven.wagon/wagon-http-lightweight "1.0-beta-7"
                  :exclusions [commons-logging/commons-logging
                               nekohtml/nekohtml
                               nekohtml/xercesMinimal]]]
  :dev-dependencies [[org.clojure/clojure-contrib "1.2.0"]
                     [swank-clojure "1.3.0"]])
