(defproject matthiasn/trailing-mouse-pointer "0.6.1-SNAPSHOT"
  :description "Sample application built with systems-toolbox library"
  :url "https://github.com/matthiasn/systems-toolbox"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha19"]
                 [org.clojure/clojurescript "1.9.908"]
                 [org.clojure/tools.logging "0.4.0"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [ch.qos.logback/logback-classic "1.2.3"]
                 [matthiasn/systemd-watchdog "0.1.3"]
                 [hiccup "1.0.5"]
                 [clj-pid "0.1.2"]
                 [matthiasn/systems-toolbox "0.6.12"]
                 [matthiasn/systems-toolbox-sente "0.6.16"]
                 [re-frame "0.10.1"]
                 [clj-time "0.14.0"]]

  :source-paths ["src/cljc/" "src/clj/"]

  :clean-targets ^{:protect false} ["resources/public/js/build/" "target/"]

  :main example.core

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-figwheel "0.5.13"]]

  :figwheel {:server-port 3451
             :css-dirs    ["resources/public/css"]}

  :profiles {:uberjar {:aot        :all
                       :auto-clean false}}

  :cljsbuild
  {:builds
   [{:id           "dev"
     :source-paths ["src/cljc/" "src/cljs" "env/dev/cljs"]
     :figwheel     true
     :compiler     {:main          "example.dev"
                    :asset-path    "js/build"
                    :optimizations :none
                    :output-dir    "resources/public/js/build/"
                    :output-to     "resources/public/js/build/example.js"
                    :source-map    true}}
    {:id           "release"
     :source-paths ["src/cljc/" "src/cljs"]
     :compiler     {:main          "example.core"
                    :asset-path    "js/build"
                    :output-to     "resources/public/js/build/example.js"
                    :optimizations :advanced}}]})
