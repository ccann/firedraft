(ns firedraft.events.games
  (:require [clojure.tools.logging :as log]
            [firedraft.events.game :as game :refer [*games]]
            [firedraft.routes.ws :as ws]
            [medley.core :refer [find-first]]))

(defn update-players!
  [games dropped-uid]
  (if-let [game-entry (find-first #(contains? (set (:players (val %)))
                                              dropped-uid)
                                  games)]
    (let [game (update (val game-entry) :players #(vec (remove #{dropped-uid} %)))
          [uid :as players] (:players game)]
      (if uid
        (do
          ;; update the remaining player's game
          (ws/send! uid [:game/update-players players])
          (assoc games (:id game) game))
        ;; there are no players remaining, remove the game
        (dissoc games (:id game))))
    games))

(defmethod ws/handle-event :chsk/uidport-close
  [{:keys [?data]}]
  (let [disconnected-uid ?data]
    (log/info :drop-player disconnected-uid)
    ;; remove the disconnected uid from the game they're playing
    (swap! *games #(update-players! % disconnected-uid))
    (log/info :games-count (count @*games))
    (game/broadcast-available-games!)))
