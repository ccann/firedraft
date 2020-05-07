(ns firedraft.game.util
  (:require [firedraft.creature-types :as ct]
            [clojure.string :as str]))

(def supported-sets
  ["IKO"
   "M20"])

(def set-numbers
  {"M20" 280
   "IKO" 274})

(def modes
  #{"winston"})

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

(defn random-name []
  (str/lower-case (str (rand-nth ct/descriptors)
                       "-"
                       (rand-nth ct/types))))
