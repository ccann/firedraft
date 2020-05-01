(ns firedraft.game.util)

(defn whose-turn
  [game]
  (get-in game [:players (:turn game)]))
