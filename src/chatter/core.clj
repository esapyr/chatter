(ns chatter.core
  (:gen-class)
  (:require [compojure.core :refer [defroutes GET POST]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [clojure.core.async :refer [go chan thread go-loop alts! close! >! <! >!! <!!]]
            [manifold.stream :as s]
            [aleph.http :as http]))

(def clients (atom #{}))
(def TIMEOUT 30000)

(defn echo-handler [req]
  (let [s @(http/websocket-connection req)]
    (s/connect s s)))

(defn -main
  [& args]
  (case (first args)
    "server" (let [port (or (second args) 9090)]
               (prn "started server")
               (http/start-server echo-handler {:port 9099})
               )))

#_(let [c @(http/websocket-client "ws://localhost:9099")]
  (s/put! c "wor")
  (prn @(s/take! c)))
