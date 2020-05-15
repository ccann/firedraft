(ns firedraft.events.games
  (:require [clojure.tools.logging :as log]
            [firedraft.events.game :as game :refer [*games]]
            [firedraft.routes.ws :as ws]
            [medley.core :refer [dissoc-in filter-vals]]
            [mount.core :refer [defstate]]))

(defn- update-players!
  [games dropped-uid]
  (if-let [game (some->> games
                         (filter-vals #((set (:players %)) dropped-uid))
                         first
                         val)]
    (let [ix (.indexOf (:players game) dropped-uid)
          picks (get-in game [:picks dropped-uid])
          {:keys [players id] :as game}
          (-> game
              (update :players assoc ix nil)
              (assoc-in [:picks nil] picks)
              (dissoc-in [:picks dropped-uid]))]
      (if-let [?remaining-players (seq (remove nil? players))]
        (do
          ;; update the remaining players' clients
          (doseq [uid ?remaining-players]
            (ws/send! uid [:game/update-players players]))
          (assoc games id game))
        ;; there are no players remaining, remove the game
        (dissoc games id)))
    games))

(defn- drop-player!
  [{:keys [?data]}]
  (let [disconnected-uid ?data]
    (log/info :drop-player disconnected-uid)
    ;; remove the disconnected uid from the game they're playing
    (swap! *games #(update-players! % disconnected-uid))
    (log/info :games-count (count @*games))
    (game/broadcast-available-games!)))

(defstate event-handlers
  :start
  (defmethod ws/handle-event :chsk/uidport-close [msg] (drop-player! msg)))
