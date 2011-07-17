(ns mcp-deps.aether
  (:import (org.apache.maven.repository.internal DefaultServiceLocator
                                                 MavenRepositorySystemSession)
           (org.apache.maven.wagon.providers.file FileWagon)
           (org.apache.maven.wagon.providers.http LightweightHttpWagon)
           (org.sonatype.aether RepositoryListener
                                RepositorySystem
                                RepositorySystemSession)
           (org.sonatype.aether.collection CollectRequest)
           (org.sonatype.aether.connector.wagon WagonProvider
                                                WagonRepositoryConnectorFactory)
           (org.sonatype.aether.graph Dependency)
           (org.sonatype.aether.repository LocalRepository
                                           RemoteRepository)
           (org.sonatype.aether.spi.connector RepositoryConnectorFactory)
           (org.sonatype.aether.transfer TransferListener
                                         TransferEvent)
           (org.sonatype.aether.util.artifact DefaultArtifact)
           (org.sonatype.aether.util.graph PreorderNodeListGenerator))
  (:require [clojure.java.io :as io]
            [clojure.string :as string]))

(defn make-transfer-listener
  []
  (reify TransferListener
    (transferCorrupted [this event] nil)
    (transferFailed [this event])
    (transferInitiated [this event])
    (transferProgressed [this event])
    (transferStarted [this event])
    (transferSucceeded [this event])))

(defn make-repository-listener
  []
  (reify RepositoryListener
    (artifactDeployed [this event])
    (artifactDeploying [this event])
    (artifactDescriptorInvalid [this event])
    (artifactDescriptorMissing [this event])
    (artifactDownloaded [this event])
    (artifactDownloading [this event])
    (artifactInstalled [this event])
    (artifactInstalling [this event])
    (artifactResolved [this event])
    (artifactResolving [this event])
    (metadataDeployed [this event])
    (metadataDeploying [this event])
    (metadataDownloaded [this event])
    (metadataDownloading [this event])
    (metadataInstalled [this event])
    (metadataInstalling [this event])
    (metadataInvalid [this event])
    (metadataResolved [this event])
    (metadataResolving [this event])))

(defn default-local-repository
  []
  (if-let [override (System/getProperty "aether.localRepository")]
    (io/file override)
    (io/file (System/getProperty "user.home") ".m2" "repository")))

(defn default-remote-repositories
  []
  (map-indexed #(if (= 1 (count %2))
                  [(format "central-%s" %1) (first %2)]
                  %2)
               (map #(string/split % #",")
                    (concat ["central,http://repo2.maven.org/maven2/"
                             "clojars,http://clojars.org/repo/"]
                            (if-let [remote (System/getProperty
                                             "aether.remoteRepository")]
                              [remote]
                              nil)))))

(defn resolve-runtime-artifacts
  "Resolves a list of runtime artifacts from the specified repository.

  artifacts => [[\"group-id\" \"artifact-id\" \"version\"] ...]

  Options:

  :repositories      => [\"url-string locating the remote repository\" ...]
  :local-repository  => \"file path locating the local repository\"

  Returns:

  {:root-node ^org.sonatype.aether.graph.DependencyNode
     :files (seq of ^java.io.File)
     :classpath \"Platform specific classpath\"}
"
  [artifacts & opts]
  (let [opts (merge {:repositories (default-remote-repositories)
                     :local-repository (default-local-repository)
                     :offline false}
                    (apply hash-map opts))
        repo-system (->
                     (doto (DefaultServiceLocator.)
                       (.setServices
                        WagonProvider
                        (into-array
                         [(proxy [WagonProvider] []
                            (lookup [role-hint]
                                    (condp = role-hint
                                        "file" (FileWagon.)
                                        "http" (LightweightHttpWagon.)
                                        nil))
                            (release [wagon]))]))
                       (.addService
                        RepositoryConnectorFactory
                        WagonRepositoryConnectorFactory))
                     (.getService RepositorySystem))
        repo-session (doto (MavenRepositorySystemSession.)
                       (.setLocalRepositoryManager
                        (.newLocalRepositoryManager
                         repo-system
                         (LocalRepository.
                          (:local-repository opts))))
                       (.setTransferListener (make-transfer-listener))
                       (.setRepositoryListener (make-repository-listener))
                       (.setOffline (:offline opts)))
        generator (PreorderNodeListGenerator.)
        root-node (doto (.getRoot
                         (.collectDependencies
                          repo-system
                          repo-session
                          (doto (CollectRequest.)
                            (.setDependencies
                             (for [[group-id artifact-id version] artifacts]
                               (Dependency. (DefaultArtifact.
                                              group-id
                                              artifact-id
                                              ""
                                              "jar"
                                              version)
                                            "runtime")))
                            (.setRepositories
                             (for [[id location] (:repositories opts)]
                               (RemoteRepository.
                                id
                                "default"
                                location))))))
                    (.accept generator))]
    (.resolveDependencies repo-system repo-session root-node nil)
    {:root-node root-node
     :files (seq (.getFiles generator))
     :classpath (.getClassPath generator)}))


