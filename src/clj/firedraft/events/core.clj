(ns firedraft.events.core
  (:require [clojure.tools.logging :as log]
            [crypto.random :as random]
            [firedraft.routes.channel-socket :as chsk]
            [medley.core :refer [find-first]]
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
  (random/base64 32))

(defn find-room-by-id
  [room-id]
  (find-first #(= room-id (:id %)) @*rooms))

(defn join-room
  [{:keys [event client-id ?reply-fn]}]
  (let [[_ data] event]
    ;; data is map with room id and player name
    (when ?reply-fn
      (let [room (find-room-by-id (:room-id data))
            room (update room :players conj client-id)]
        (?reply-fn room)))))

(defn create-room
  [{:keys [event client-id ?reply-fn]}]
  (log/info "handle" (first event))
  (let [[_ data] event]
    ;; data is a room map
    (when ?reply-fn
      (let [id (new-id)
            room (assoc data
                        :id id
                        :players [client-id])]
        (swap! *rooms assoc id room)
        (log/info "create room:" room)
        (?reply-fn room)))))

(defstate event-handlers
  :start
  (do (defmethod chsk/handle-event :room/create [msg] (create-room msg))
      (defmethod chsk/handle-event :room/join [msg] (join-room msg))))
