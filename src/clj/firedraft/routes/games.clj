(ns firedraft.routes.games
  (:require [firedraft.events.game :as game]
            [firedraft.middleware :as middleware]
            [ring.util.http-response :as http]))

(defn format-game
  [game]
  ;; TODO add players
  (select-keys game [:mode :id :title]))

(defn read-games
  [_req]
  (->> (game/available-games)
       (mapv format-game)
       http/ok))

(defn routes []
  ["/games" {:middleware [;; middleware/wrap-csrf
                          middleware/wrap-formats]}
   ["" {:get {:handler read-games}}]])
