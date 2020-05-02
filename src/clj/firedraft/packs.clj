(ns firedraft.packs
  (:require [clojure.set :as set]))

(defn- has-rarity [rarity]
  (fn [card]
    (= rarity (:rarity card))))

(defn- has-name [-name]
  (fn [card]
    (if (set? -name)
      (-name (:name card))
      (= -name (:name card)))))

(def basic-lands
  #{"Forest" "Island" "Mountain" "Swamp" "Plains"})

(defn pool
  [rarity set-cards & [exclude]]
  (shuffle
   (case rarity
     "basic" (filter
              (has-name basic-lands)
              set-cards)
     "common" (->> (filter (has-rarity rarity) set-cards)
                   (remove (has-name
                            (set/union basic-lands (:commons exclude)))))
     (filter (has-rarity rarity) set-cards))))

(defn assemble [{:keys [cs us rs bs foil] :as _pack}]
  (let [cs (if foil
             (conj (vec cs) foil)
             (vec cs))]
    (concat rs us cs bs)))

;; (defn add-foil
;;   [pack set-cards]
;;   (println "add foil")
;;   (let [rand-card (assoc (rand-nth set-cards) :foil? true)]
;;     (-> pack
;;         (assoc :foil rand-card)
;;         (update :cs drop-last))))

(defn add-mythic
  [pack ms]
  (println "add mythic")
  (assoc pack :rs (take 1 ms)))

(defn -create-booster
  [set-cards & [exclude]]
  (let [[bs cs us rs ms] (map #(pool % set-cards exclude)
                              ["basic" "common" "uncommon" "rare" "mythic"])
        basic-pack (cond-> {:rs (take 1 rs)
                            :us (take 3 us)
                            :cs (take 10 cs)
                            :bs (take 1 bs)}
                     ;; 1 in 8 packs contains a mythic
                     (< (rand) 1/8) (add-mythic ms)
                     ;; TODO: revise foil inclusion
                     ;; (< (rand) 1/4) (add-foil set-cards)
                     )]
    {:pack basic-pack
     :basics bs
     :commons cs
     :uncommons us
     :rares rs
     :mythics ms}))

(defmulti create-booster :set-code)

(defmethod create-booster "IKO"
  [{:keys [cards]}]
  (let [gainland-names #{"Tranquil Cove"
                         "Dismal Backwater"
                         "Bloodfell Caves"
                         "Rugged Highlands"
                         "Blossoming Sands"
                         "Scoured Barrens"
                         "Swiftwater Cliffs"
                         "Jungle Hollow"
                         "Wind-Scarred Crag"
                         "Thornwood Falls"}
        {:keys [pack]} (-create-booster cards {:common gainland-names})
        gainlands (filter (has-name (set gainland-names)) cards)
        pack-cards (cond-> pack
                     ;; 5 in 12 packs contain a gainland
                     (< (rand) 5/12) (assoc :bs [(rand-nth gainlands)])
                     true (assemble))]
    pack-cards))
