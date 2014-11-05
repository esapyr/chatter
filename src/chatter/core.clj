(ns chatter.core
  (:gen-class)
  (:require [clojure.core.async :refer [go chan alts! >!]]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [aleph.http :as http]))

(def clients (atom #{}))

(defn server-handle-client [req]
  (println "~Starting new connection with request~")
  (let [deferred-strm (http/websocket-connection req)]
    (d/on-realized deferred-strm                                         ;once we have the connection
                   (fn [strm]                                            ;strm = this client's stream
                     (println "!Connected!")
                     (swap! clients conj strm)                           ;put connection in set
                     (s/consume (fn [msg]                                ;consume every message from a client, and send to all other clients
                                  (doseq [c-strm @clients]               ;for each client stream
                                    (when (not= c-strm strm)             ;if it's not the active client's stream
                                      (println "sent: " msg " to " c-strm)
                                      (s/put! c-strm msg))))
                                strm))                                   ;client stream is closed over
                   (fn [error] (println "error connecting to client: " error)))))

#_(defn- non-blocking-read-line [reader]
  (print "out of loop:" (.ready reader))
  (loop [input (vector)]
    (if (= (last input) \newline)
      (print "in loop: " (.ready reader))
      (apply str input)
      (if (.ready reader)
        (recur (conj input (char (.read reader))))
        (recur input)))))

(defn- request-user-input []
  (print ">")
  (flush)
  @(d/timeout! (d/future (read-line)) 5000 nil))

(defn start-client []
  (let [deferred-strm        (http/websocket-client "ws://localhost:9099")
        server-ch            (chan)
        user-ch              (chan)]
    (println "~Waiting for connection~")
    (d/on-realized deferred-strm                                           ;once we have a connection
                   (fn [server-strm]
                     (println "!Connected!")
                     (s/consume #(go (>! server-ch %)) server-strm)                     ;consume all server input
                     (go (>! user-ch (s/periodically 1000 request-user-input)))              ;ask for user input every second (wait for 5 for input)
                     (go (let [[value ch] (alts! [user-ch server-ch] :priority true)]
                           (condp = ch
                             server-ch (println value)
                             user-ch   (s/put! server-strm value)))))
                   (fn [error] (println "error connecting to server: " error)))))

(defn -main
  [& args]
  (case (first args)
    "client" (start-client)
    "server" (let [port (or (second args) 9099)]
               (println "~Starting server~")
               (println "Waiting for connections...")
               (http/start-server server-handle-client {:port (eval port)}))))

#_(defn echo-handler [req]
    (let [s @(http/websocket-connection req)]
      (s/connect s s)))

#_(let [c @(http/websocket-client "ws://localhost:9099")]
    (s/put! c "wor")
    (prn @(s/take! c)))
