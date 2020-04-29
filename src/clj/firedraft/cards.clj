(ns firedraft.cards
  (:require [camel-snake-kebab.core :as case]
            [camel-snake-kebab.extras :as casex]
            [clj-http.client :as client]
            [clojure.string :as str]
            [jsonista.core :as json]
            [medley.core :refer [find-first]]
            [clojure.java.io :as io]
            [clojure.set :as set]))

(def supported-sets
  #{"IKO"
    "WAR"
    "M20"
    "THB"})

(def supported-set-types
  #{"expansion" "core"})

(def json-mapper
  (json/object-mapper
   {:decode-key-fn keyword}))

(defn json-decode [decodable]
  (json/read-value decodable json-mapper))

(defn kebab-case-keys
  [m]
  (casex/transform-keys case/->kebab-case-keyword m))

(def *sets-cache
  (atom nil))

(defn- format-set
  [m]
  (-> (kebab-case-keys m)
      (select-keys [:set-type
                    :released-at
                    :id
                    :code
                    :digital
                    :name
                    :icon-svg-uri])
      (update :code #(some-> % str/upper-case))))

(defn get-sets []
  (or (seq @*sets-cache)
      (let [resp (client/get "https://api.scryfall.com/sets")]
        (if (= 200 (:status resp))
          (let [sets (-> resp
                         (get :body)
                         (json-decode)
                         (get :data))
                sets (->> sets
                          (filter #(supported-set-types (get % :set_type)))
                          (filter #(supported-sets (str/upper-case (get % :code))))
                          (map format-set))]
            (reset! *sets-cache sets))
          (throw (ex-info "failed to fetch sets from Scryfall"
                          {:response resp}))))))

(def *cards-by-set-cache
  (atom {}))

(defn- format-card
  [m set-code]
  (-> (kebab-case-keys m)
      (select-keys [:scryfall-oracle-id
                    :name
                    :scryfall-id
                    :image-uri
                    :type
                    :rarity
                    :uuid])
      (assoc :set set-code)))

(defn get-cards-by-set
  [set-code]
  (let [set-code (str/upper-case set-code)]
    (or (get @*cards-by-set-cache set-code)
        (let [resp (client/get (str "https://www.mtgjson.com/json/" set-code ".json"))]
          (if (= 200 (:status resp))
            (let [cards (->> (:body resp)
                             (json-decode)
                             (:cards)
                             (map #(format-card % set-code)))]
              (swap! *cards-by-set-cache assoc set-code cards)
              cards)
            (throw (ex-info "failed to fetch set cards from MTGJson"
                            {:set set-code
                             :response resp})))))))

(def *card-cache
  (atom nil))

(defn get-card
  ([card-name]
   (get-card card-name nil))
  ([card-name set-code]
   (when (empty? @*card-cache)
     (let [set-codes (map :code (get-sets))]
       (reset! *card-cache
               (->> (mapcat get-cards-by-set set-codes)
                    (group-by :name)))))
   (let [printings (get @*card-cache card-name)]
     (cond
       (map? printings) printings
       set-code         (find-first #(= set-code (:set-code %)) printings)
       ;; take an arbitrary printing
       :else            (first printings)))))

;; (io/copy (:body (client/get "https://api.scryfall.com/cards/2ba18114-af6c-48cd-82c9-eb6541d566bf"
;;                             {:as :byte-array
;;                              :query-params {:format "image"
;;                                             :version "png"}}))
;;       (io/file "temp.png"))

;; (map get-card
;;      (-> (slurp "/Users/cody/Downloads/TheCube.txt")
;;          (str/split-lines)))

;; (reset! *card-cache {})
#_(def x (get-card "Fire Prophecy"))


(defmulti add-deck :mode)

(defmethod add-deck "winston"
  [game]
  (let [boosters (get-in game [:opts :booster])]
    (cond boosters
          (assoc game :deck
                 (map (fn [m] {:sid (:scryfall-id m)
                               :name (:name m)})
                      (mapcat (fn [set-code]
                                (let [cards (get-cards-by-set set-code)]
                                  ;; TODO: pack construction
                                  (take 15 (shuffle
                                            (filter
                                             #(not (str/includes? (:type %) "Basic Land"))
                                             cards)))))
                              boosters)))

          :else nil)))

(defn shuffle-deck [game]
  (update game :deck shuffle))
