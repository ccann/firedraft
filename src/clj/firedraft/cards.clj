(ns firedraft.cards
  (:require [clj-http.client :as client]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [firedraft.common :as com]
            [firedraft.game.util :as g]
            [firedraft.packs :as packs]
            [jsonista.core :as json]
            [mount.core :refer [defstate]])
  (:import java.util.UUID))

(defn- ->Int
  [s]
  ;; some numbers are not parsable e.g. "206†" for the misprint of Corpse Knight
  (try (Integer/parseInt s)
       (catch NumberFormatException _ 10000)))

(defstate set->cards
  :start (com/load-edn (io/resource "set-index.edn")))

(defstate card-index
  :start (com/load-edn (io/resource "card-index.edn")))

(defstate set-list
  :start (com/load-edn (io/resource "set-list.edn")))

(defn get-cards-by-set
  [set-code]
  (->> (get set->cards (keyword set-code))
       (filter #(<= (:number %) (get g/set-numbers (name set-code))))))

(defn get-set
  [set-code]
  (get set-list (keyword set-code)))

(defn set-type
  [{:keys [type]}]
  (case type
    ("expansion" "core" "commander" "draft_innovation" "masters") 4
    "starter" 3
    "dual_deck" 2
    1))

(defn arena-set?
  [{:keys [code]}]
  (if (contains? (set g/supported-sets) (name code)) 1 0))

(defn get-card
  [card-name]
  (let [card-sets (get card-index card-name)
        sets (->> (map get-set (keys card-sets))
                  (sort-by (juxt arena-set? set-type :release-date))
                  (reverse))
        this-set (keyword (:code (first sets)))]
    (-> (get card-sets this-set)
        (assoc :set this-set))))

(defn get-card-all-sets
  [card-name]
  (get card-index card-name))

(defn import-cubecobra
  [cube-id]
  (let [resp (client/get (str "https://cubecobra.com/cube/download/plaintext/"
                              cube-id))]
    (->> (:body resp)
         (str/split-lines)
         (map get-card))))

(defn format-card
  [card]
  {:sid (:scryfall-id card)
   :name (:name card)
   :col (:colors card)
   :uid (str (UUID/randomUUID))
   :cmc (int (or (:converted-mana-cost card) 0))})

(defmulti add-deck :mode)

(defmethod add-deck "winston"
  [game]
  (let [boosters (get-in game [:opts :booster])
        cube-id (get-in game [:opts :cube])]
    (cond boosters
          (assoc game :deck
                 (->> boosters
                      (mapcat (fn [set-code]
                                (let [cards (get-cards-by-set set-code)]
                                  (packs/create-booster
                                   {:cards cards
                                    :set-code set-code}))))
                      (mapv format-card)
                      (shuffle)))
          cube-id
          (assoc game :deck
                 (->> (import-cubecobra cube-id)
                      (shuffle)
                      (take 90)
                      (mapv format-card)))
          :else nil)))

;;; -----------------------------------------
;;; REPL functions for generating index files
;;;

(def card-fields
  ["rarity"
   "scryfallId"
   "convertedManaCost"
   "type"
   "name"
   "number"
   "colors"])

(defn- generate-card-index
  [path]
  (reduce (fn [card-ix [set-code m]]
            (let [cards (get m "cards")
                  set-size (get m "baseSetSize")]
              (reduce (fn [-card-ix card]
                        (let [card-name (get card "name")]
                          (update-in -card-ix [card-name (keyword set-code)]
                                     (fn [?m]
                                       ;; add card IFF:
                                       ;; card hasn't been added yet OR
                                       ;; card is within bounds of base set size
                                       (if (or (not ?m)
                                               (<= (->Int (get card "number")) set-size))
                                         (-> (select-keys card card-fields)
                                             (update "number" ->Int)
                                             (com/kebab-case-keys))
                                         ?m)))))
                      card-ix
                      cards)))
          {}
          (->> (slurp path)
               (json/read-value))))

(defn- generate-set-index
  [path]
  (reduce (fn [set-ix [set-code m]]
            (let [cards (get m "cards")]
              (assoc set-ix (keyword set-code)
                     (map (fn [card]
                            (-> (select-keys card card-fields)
                                (update "number" ->Int)
                                (com/kebab-case-keys)))
                          cards))))
          {}
          (->> (slurp path)
               (json/read-value))))

(defn- generate-set-list
  [path]
  (reduce (fn [set-meta -set]
            (assoc set-meta (keyword (get -set "code"))
                   (com/kebab-case-keys -set)))
          {}
          (->> (slurp path)
               (json/read-value))))

(defn- write-edn!
  [path m]
  (spit (io/file path) (prn-str m)))

(comment
  (->> (generate-set-list "/Users/cody/Downloads/SetList.json")
       (write-edn! "resources/set-list.edn"))
  (->> (generate-card-index "/Users/cody/Downloads/AllPrintings.json")
       (write-edn! "resources/card-index.edn"))
  (->> (generate-set-index "/Users/cody/Downloads/AllPrintings.json")
       (write-edn! "resources/set-index.edn")))
