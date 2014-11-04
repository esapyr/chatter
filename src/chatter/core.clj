(ns chatter.core
  (:gen-class)
  (:require [org.httpkit.client :as http]
            [org.httpkit.server :refer :all]
            [org.httpkit.timer :refer :all]
            [compojure.core :refer [defroutes GET POST]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [clojure.core.async :refer [go chan thread go-loop alts! close! >! <! >!! <!!]]))

(def clients (atom #{}))
(def TIMEOUT 30000)

; Client functions
(defn get-server-output [server-output]
  "dispatches thread that loops until ^D, blocks on input"
  (go-loop []
           (let [value (<! server-output)]
             (when-not (nil? value)
               (println value)
               (recur)))))

(defn server-callback [server-output]
  "http connection w/ callback that put's response in channel"
  (http/get "http://localhost:9090/" {:keep-alive TIMEOUT}
            (fn [res]
              (prn res)
              ;(go (>! server-output msg-from-server))
              )))

(defn start-client []
  (println "Input a message to send to all clients. :close to close connection")
  (let [server-output (chan)
        _             (server-callback server-output)
        _             (get-server-output server-output)]
    (loop []
      (let [res (read-line)]
        (when-not (or (nil? res) (= res ":close"))
          (do
            (http/post "http://localhost:9090/" {:query-params {:msg res} :keep-alive TIMEOUT})
            (recur)))))))

; Server functions
(defn server-send-to-all [msg]
  (prn "sending" msg)
  (doseq [c @clients]
    (send! c msg false))) ;false -> don't close after sending

(defn handler-old [request]
  (with-channel request channel
    (swap! clients conj channel)
    (on-close channel (fn [status] (do
                                     (swap! clients disj channel)
                                     (server-send-to-all (str channel " has dissconected")))))
    (send! channel (str "request"))))

(defn handler [request]
  (with-channel request channel
    (on-close channel (fn [status] (println "channel closed, " status)))
    (loop [id 0]
      (when (< id 10)
        (schedule-task (* id 200) ;; send a message every 200ms
                       (send! channel (str "message from server #" id) false)) ; false => don't close after send
        (recur (inc id))))
    (schedule-task 10000 (close channel))))

(defroutes server-handler
  (GET "/" [] handler)
  (POST "/" {{msg :msg} :query-params} (server-send-to-all msg)))

(defn start-server [port]
  (println "Server Started")
  (run-server (wrap-defaults server-handler api-defaults) {:port (or port 9090)}))

(defn -main
  [& args]
  (case (first args)
    "server" (start-server (second args))
    "client" (start-client)))
