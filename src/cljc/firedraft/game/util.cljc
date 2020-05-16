(ns firedraft.game.util
  (:require [clojure.string :as str]
            [firedraft.creature-types :as ct]))

(def sets
  [{:code "IKO"
    :name "Ikoria: Lair of Behemoths"
    :number 274}
   {:code "THB"
    :name "Theros: Beyond Death"
    :number 254}
   {:code "ELD"
    :name "Throne of Eldraine"
    :number 269}
   {:code "M20"
    :name "Core Set 2020"
    :number 280}
   {:code "WAR"
    :name "War of the Spark"
    :number 264}
   {:code "RNA"
    :name "Ravnica Allegiance"
    :number 259}
   {:code "GRN"
    :name "Guilds of Ravnica"
    :number 259}
   {:code "M19"
    :name "Core Set 2019"
    :number 280}
   {:code "DOM"
    :name "Dominaria"
    :number 269}
   {:code "RIX"
    :name "Rivals of Ixalan"
    :number 196}
   {:code "XLN"
    :name "Ixalan"
    :number 279}])

(def set-numbers
  (zipmap (map :code sets) (map :number sets)))

(def supported-sets
  (mapv :code sets))

(def modes
  #{"winston"})

(def supported-set-types
  #{"expansion" "core"})

(defn whose-turn
  [game]
  (get-in game [:players (:turn game)]))

(defn increment-turn
  [game]
  (let [game (update game :turn-number #(if % (inc %) 0))]
    (assoc game :turn (mod (:turn-number game)
                           (:max-players game)))))

(defn get-next-player
  [game]
  (whose-turn (increment-turn game)))

(defn joinable?
  [game]
  (< 0 (->> (:players game)
            (remove nil?)
            (count))
     (:max-players game)))

(defn in-progress?
  [game]
  (boolean (:turn-number game)))

(defn active-players
  [game]
  (vec (remove nil? (:players game)))  )

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
