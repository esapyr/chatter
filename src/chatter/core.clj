(ns chatter.core
  (:gen-class)
  (:require [manifold.deferred :as d]
            [manifold.stream :as s]
            [aleph.http :as http]
            [seesaw.core :as gui]
            [seesaw.bind :as bind]))

(let [clients (atom #{})]
  (defn server-handle-client [req]
    (d/on-realized (http/websocket-connection req)                                         ;once we have the connection
                   (fn [strm]                                            ;strm = this client's stream
                     (println "New Connection")
                     (swap! clients conj strm)                           ;put connection in set
                     (s/consume (fn [msg]                                ;consume every message from a client, and send to all other clients
                                  (doseq [c-strm @clients]               ;for each client stream
                                    (s/put! c-strm msg)))
                                strm))                                   ;client stream is closed over
                   (fn [error] (println "error connecting to client: " error)))))

(defn start-client [addr]
  (let [display-box  (gui/listbox)
        display-text (gui/scrollable display-box)
        out-vec      (atom [])
        frame        (gui/frame  :title "Chatter" :size [500 :by 500])
        send-bttn    (gui/button :text "Send!")
        in-field     (gui/text)]
    (bind/bind out-vec (bind/property display-box :model))
    (gui/config! frame :content (gui/border-panel
                                 :center display-text
                                 :south (gui/left-right-split in-field send-bttn :divider-location 2/3)))
    (d/on-realized (http/websocket-client addr)
                   (fn [server-strm]
                     (s/consume (fn [msg] (swap! out-vec conj msg)) server-strm)
                     (gui/listen send-bttn :action (fn [e]
                                                     (s/put! server-strm (gui/text in-field))
                                                     (gui/text! in-field ""))))
                   (fn [error] (println "error connecting to server: " error)))
    (-> frame gui/pack! gui/show!)))

(defn -main
  [& args]
  (case (first args)
    "client"     (let [port (or (second args) 9099)]
                   (println "Starting new GUI client")
                   (start-client (str "ws://localhost:" port)))
    "server"     (let [port (or (second args) 9099)]
                   (println "Starting server")
                   (http/start-server server-handle-client {:port (eval port)}))
    nil          (println "Either server <port>")))
