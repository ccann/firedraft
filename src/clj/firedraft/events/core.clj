(ns firedraft.events.core
  (:require [clojure.tools.logging :as log]
            [crypto.random :as random]
            [firedraft.routes.ws :as ws]
            [mount.core :refer [defstate]]))

;; {:title "Our Fun Game"
;;  :id "8njcTLq0ZVnebaPtXrXhpXG1+bEnCyK8vtY8wt+EAuk="
;;  :game {:mode "winston"
;;         :opts {:booster ["IKO" "IKO" "IKO"
;;                          "IKO" "IKO" "IKO"]
;;                :cube "abcdefg"}}
;;  :players ["uid1" "uid2"]}

(def game-modes
  #{"winston"})

(defstate *rooms
  :start (atom {}))

(defn new-id []
  (random/hex 16))

(defn join-room
  [{:keys [event uid ?reply-fn]}]
  (log/info "handle" (first event) (:id (second event)))
  (let [[_ data] event]
    (if-let [room (get @*rooms (:id data))]
      (let [room (update room :players conj uid)
            players (:players room)]
        (log/info "join room:" uid)
        (doseq [uid players]
          (log/info "send" :room/joined "to" uid)
          (ws/send! uid [:room/joined room]))
        (?reply-fn room))
      (do (log/error "no such room" (:id data))
          (?reply-fn {:error :not-found})))))

(defn create-room
  [{:keys [event uid ?reply-fn ring-req]}]
  (log/info "handle" (first event) uid)
  (let [[_ data] event]
    (log/info "ring session" (:session ring-req))
    ;; data is a room map
    (when ?reply-fn
      (let [id (new-id)
            room (assoc data
                        :id id
                        :players [uid])]
        (swap! *rooms assoc id room)
        (log/info "create room:" room)
        (?reply-fn room)))))

(defstate event-handlers
  :start
  (do (defmethod ws/handle-event :room/create [msg] (create-room msg))
      (defmethod ws/handle-event :room/join [msg] (join-room msg))))
