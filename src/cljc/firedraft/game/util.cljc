(ns firedraft.game.util)

(def supported-sets
  ["IKO"
   "M20"])

(def set-numbers
  {"M20" 280
   "IKO" 274})

(def supported-set-types
  #{"expansion" "core"})

(defn whose-turn
  [game]
  (get-in game [:players (:turn game)]))

(def game-defaults
  {"winston" {:mode "winston"
              :opts {:booster ["IKO" "IKO" "IKO"
                               "IKO" "IKO" "IKO"]}}
   "grid" {:mode "grid"
           :opts {}}})

(def default-game-mode "winston")

(def default-game-config
  (get game-defaults default-game-mode))
