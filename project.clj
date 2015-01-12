(defproject event-api "current"
  :description "REST API for recording nucleotid.es benchmarking events."

  :dependencies [[org.clojure/clojure        "1.6.0"]
                 [ring/ring-jetty-adapter    "1.3.1"]
                 [compojure                  "1.3.1"]
                 [com.cemerick/rummage       "1.0.1"]
                 [com.amazonaws/aws-java-sdk "1.3.21.1"]]
  :plugins      [[lein-ring "0.9.0"]]


  :ring     {:handler event-api.core/api :port 8080}

  :profiles {
    :dev     {:dependencies [[ring-mock "0.1.5"]]}
    :uberjar {:aot :all}})

