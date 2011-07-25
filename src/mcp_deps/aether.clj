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
           (org.sonatype.aether.resolution ArtifactRequest
                                           ArtifactResolutionException
                                           DependencyRequest)
           (org.sonatype.aether.spi.connector RepositoryConnectorFactory)
           (org.sonatype.aether.transfer TransferListener
                                         TransferEvent)
           (org.sonatype.aether.util.artifact DefaultArtifact
                                              SubArtifact)
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

(defn- resolve-requests
  [repo-system repo-session artifact-requests]
  (try
    (.resolveArtifacts repo-system repo-session artifact-requests)
    (catch ArtifactResolutionException e
      (.getResults e))))

(defn resolve-runtime-artifacts
  "Resolves a list of runtime artifacts from the specified repository.

  artifacts => [[\"group-id\" \"artifact-id\" \"version\"] ...]

  Options:

  :repositories      => [\"url-string locating the remote repository\" ...]
  :local-repository  => \"file path locating the local repository\"
  :offline           => true or false
  :include-sources   => true or false

  Returns:

  {:files (seq of ^java.io.File)
     :classpath \"Platform specific classpath\"}
"
  [artifacts & opts]
  (let [opts (merge {:repositories (default-remote-repositories)
                     :local-repository (default-local-repository)
                     :offline false
                     :include-sources false}
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
        repositories (for [[id location] (:repositories opts)]
                       (RemoteRepository.
                        id
                        "default"
                        location))
        root-dependencies (for [[group-id artifact-id version] artifacts]
                            (Dependency. (DefaultArtifact.
                                           group-id
                                           artifact-id
                                           ""
                                           "jar"
                                           version)
                                         "runtime"))
        required-artifacts-result (.collectDependencies
                                   repo-system
                                   repo-session
                                   (CollectRequest.
                                    root-dependencies
                                    nil
                                    repositories))
        [required-artifacts required-nodes] ((juxt #(.getArtifacts % true)
                                                   #(.getNodes %))
                                             (doto (PreorderNodeListGenerator.)
                                               (->>
                                                (.accept
                                                 (.getRoot required-artifacts-result)))))
        source-nodes (when (:include-sources opts)
                       (let [source-dependencies (map #(Dependency.
                                                        (SubArtifact. %1 "sources" "jar")
                                                        "runtime"
                                                        true)
                                                      required-artifacts)]
                         (.getNodes
                          (doto (PreorderNodeListGenerator.)
                            (->>
                             (.accept
                              (.getRoot
                               (.collectDependencies
                                repo-system
                                repo-session
                                (CollectRequest. source-dependencies nil repositories)))))))))

        resolved-artifacts (filter
                            #(.isResolved %)
                            (resolve-requests
                             repo-system
                             repo-session
                             (map #(ArtifactRequest. %)
                                  (second
                                   (reduce (fn [[seen result] node]
                                             (let [dependency (.getDependency node)]
                                               [(conj seen dependency)
                                                (if (seen dependency)
                                                  result
                                                  (conj result node))]))
                                           [#{} []]
                                           (concat required-nodes source-nodes))))))
        files (map #(.. % getArtifact getFile) resolved-artifacts)]
    {:files files
     :classpath (apply str (interpose java.io.File/pathSeparator
                                      (map #(.getAbsolutePath %) files)))}))
