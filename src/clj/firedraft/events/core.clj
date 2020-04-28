(ns firedraft.events.core
  (:require [clojure.tools.logging :as log]
            [crypto.random :as random]
            [firedraft.routes.ws :as ws]
            [mount.core :refer [defstate]]))

;; {:title "Our Fun Game"
;;  :id "8njcTLq0ZVnebaPtXrXhpXG1+bEnCyK8vtY8wt+EAuk="
;;  :mode "winston"
;;  :opts {:booster ["IKO" "IKO" "IKO"
;;                   "IKO" "IKO" "IKO"]
;;         :cube "abcdefg"}
;;  :players ["uid1" "uid2"]}

(def game-modes
  #{"winston"})

(defstate *games
  :start (atom {}))

(defn new-id []
  (random/hex 16))

(defn join-game
  [{:keys [event uid ?reply-fn]}]
  (log/info "handle" (first event) (:id (second event)))
  (let [[_ data] event]
    (if-let [game (get @*games (:id data))]
      (let [game (update game :players conj uid)
            players (:players game)]
        (log/info "join game:" uid)
        (doseq [uid players]
          (log/info "send" :game/joined "to" uid)
          (ws/send! uid [:game/joined game]))
        (?reply-fn game))
      (do (log/error "no such game" (:id data))
          (?reply-fn {:error :not-found})))))

(defn create-game
  [{:keys [event uid ?reply-fn ring-req]}]
  (log/info "handle" (first event) uid)
  (let [[_ data] event]
    (log/info "ring session" (:session ring-req))
    ;; data is a game map
    (when ?reply-fn
      (let [id (new-id)
            game (assoc data
                        :id id
                        :players [uid])]
        (swap! *games assoc id game)
        (log/info "create game:" game)
        (?reply-fn game)))))

(defstate event-handlers
  :start
  (do (defmethod ws/handle-event :game/create [msg] (create-game msg))
      (defmethod ws/handle-event :game/join [msg] (join-game msg))))
