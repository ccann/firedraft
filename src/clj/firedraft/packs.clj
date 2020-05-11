(ns firedraft.packs
  (:require [clojure.set :as set]
            [clojure.string :as str]))

(defn- has-rarity [rarity]
  (fn [card]
    (= rarity (:rarity card))))

(defn- has-name [-name]
  (fn [card]
    (if (set? -name)
      (-name (:name card))
      (= -name (:name card)))))

(defn- planeswalker?
  [card]
  (str/includes? (:type card) "Planeswalker"))

(defn- legendary-creature?
  [card]
  (and (str/includes? (:type card) "Legendary")
       (str/includes? (:type card) "Creature")))

(def basic-land-names
  #{"Forest" "Island" "Mountain" "Swamp" "Plains"})

(def gainland-names
  #{"Tranquil Cove"
    "Dismal Backwater"
    "Bloodfell Caves"
    "Rugged Highlands"
    "Blossoming Sands"
    "Scoured Barrens"
    "Swiftwater Cliffs"
    "Jungle Hollow"
    "Wind-Scarred Crag"
    "Thornwood Falls"})

(def tapland-names
  #{"Meandering River"
    "Submerged Boneyard"
    "Cinder Barrens"
    "Timber Gorge"
    "Tranquil Expanse"
    "Forsaken Sanctuary"
    "Highland Lake"
    "Foul Orchard"
    "Stone Quarry"
    "Woodland Stream"})

(def guildgate-names
  #{"Azorius Guildgate"
    "Rakdos Guildgate"
    "Gruul Guildgate"
    "Simic Guildgate"
    "Orzhov Guildgate"
    "Selesnya Guildgate"
    "Boros Guildgate"
    "Golgari Guildgate"
    "Izzet Guildgate"
    "Dimir Guildgate"})

(defn pool
  [rarity set-cards & [exclude]]
  (shuffle
   (case rarity
     "basic" (filter
              (has-name basic-land-names)
              set-cards)
     "common" (->> (filter (has-rarity rarity) set-cards)
                   (remove (has-name
                            (set/union basic-land-names (:common exclude)))))
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

(def hierarchy
  (-> (make-hierarchy)
      (derive :THB :standard)
      (derive :ELD :standard)
      (derive :RIX :standard)
      (derive :XLN :standard)
      (derive :RNA :GRN)
      (derive :IKO :M20)))

(defmulti create-booster (comp keyword :set-code)
  :hierarchy #'hierarchy)

(defmethod create-booster :M20
  [{:keys [cards]}]
  (let [{:keys [pack]} (-create-booster cards {:common gainland-names})
        gainlands (filter (has-name (set gainland-names)) cards)]
    (cond-> pack
      ;; 5 in 12 packs contain a gainland
      (< (rand) 5/12) (assoc :bs [(rand-nth gainlands)])
      true (assemble))))

(defmethod create-booster :WAR
  [{:keys [cards]}]
  (let [[bs cs us rs ms] (map #(pool % cards)
                              ["basic" "common" "uncommon" "rare" "mythic"])
        pack (cond-> {:rs (take 1 rs)
                      :cs (take 10 cs)
                      :bs (take 1 bs)}
               ;; 1 in 8 packs contains a mythic
               (< (rand) 1/8) (add-mythic ms))
        rare (first (:rs pack))]
    ;; either the rare or one of the uncommons must be a planeswalker
    (-> (if (planeswalker? rare)
          (assoc pack :us (take 3 (remove planeswalker? us)))
          (assoc pack :us (shuffle (concat (take 1 (filter planeswalker? us))
                                           (take 2 (remove planeswalker? us))))))
        (assemble))))

(defmethod create-booster :GRN
  [{:keys [cards]}]
  (let [{:keys [pack]} (-create-booster cards {:common guildgate-names})
        guildgates (filter (has-name (set guildgate-names)) cards)]
    ;; there are 2 printings of each guildgate (5 per set)
    (assert (= 10 (count guildgates)))
    (-> pack
        ;; all packs contain a guildgate instead of a basic
        (assoc :bs [(rand-nth guildgates)])
        (assemble))))

(defmethod create-booster :M19
  [{:keys [cards]}]
  (let [{:keys [pack]} (-create-booster cards {:common tapland-names})
        gainlands (filter (has-name (set tapland-names)) cards)]
    (cond-> pack
      ;; 5 in 12 packs contain a tapland
      (< (rand) 5/12) (assoc :bs [(rand-nth gainlands)])
      true (assemble))))

(defmethod create-booster :DOM
  [{:keys [cards]}]
  (let [[bs cs us rs ms] (map #(pool % cards)
                              ["basic" "common" "uncommon" "rare" "mythic"])
        pack (cond-> {:rs (take 1 rs)
                      :cs (take 10 cs)
                      :bs (take 1 bs)}
               ;; 1 in 8 packs contains a mythic
               (< (rand) 1/8) (add-mythic ms))
        rare (first (:rs pack))]
    ;; either the rare or one of the uncommons must be legendary
    (-> (if (legendary-creature? rare)
          (assoc pack :us (take 3 (remove legendary-creature? us)))
          (assoc pack :us (shuffle (concat (take 1 (filter legendary-creature? us))
                                           (take 2 (remove legendary-creature? us))))))
        (assemble))))

(defmethod create-booster :standard
  [{:keys [cards]}]
  (let [{:keys [pack]} (-create-booster cards)]
    (assemble pack)))

(defmethod create-booster :default
  [{:keys [set-code]}]
  (throw (IllegalArgumentException.
          (str "Set code " set-code " is not supported.")))  )
