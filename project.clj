(defproject mcp-deps "1.0.0"
  :description "Provides facilities for resolving dependencies."
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.sonatype.aether/aether-api "1.12"]
                 [org.sonatype.aether/aether-util "1.12"]
                 [org.sonatype.aether/aether-impl "1.12"]
                 [org.sonatype.aether/aether-connector-wagon "1.12"]
                 [org.apache.maven/maven-aether-provider "3.0.3"]
                 [org.apache.maven.wagon/wagon-file "1.0"]
                 [org.apache.maven.wagon/wagon-http-lightweight "1.0"
                  :exclusions [commons-logging/commons-logging
                               nekohtml/nekohtml
                               nekohtml/xercesMinimal]]]
  :dev-dependencies [[org.clojure/clojure-contrib "1.2.0"]])
