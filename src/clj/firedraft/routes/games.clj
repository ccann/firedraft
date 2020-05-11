(ns firedraft.routes.games
  (:require [firedraft.events.game :as game]
            [firedraft.middleware :as middleware]
            [ring.util.http-response :as http]))

(defn read-games
  [_req]
  (http/ok (game/available-games)))

(defn routes []
  ["/games" {:middleware [middleware/wrap-formats]}
   ["" {:get {:handler read-games}}]])
