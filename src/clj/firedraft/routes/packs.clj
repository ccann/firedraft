(ns firedraft.routes.packs
  (:require [firedraft.cards :as cards]
            [firedraft.game.util :refer [supported-sets]]
            [firedraft.middleware :as middleware]
            [firedraft.packs :as packs]
            [hiccup.page :as hiccup]
            [reitit.coercion.spec :as reitit.spec]
            [ring.util.http-response :as http]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(s/def ::set-code
  (s/and string? (set supported-sets)))

(s/def ::cube-id string?)

(s/def ::colors #{"U" "G" "W" "B" "R"})

(defn display-cards
  [cards & [size version]]
  (hiccup/html5
   (let [version (or version "large")
         size (or size "is-one-fifth")]
     {:lang "en"}
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge,chrome=1"}]
      [:meta
       {:name "viewport"
        :content "width=device-width, initial-scale=1.0, maximum-scale=1.0"}]
      [:title "Booster"]
      [:body
       [:div.section
        [:div.container
         [:div.columns.is-multiline
          (for [card cards]
            [:div.column
             {:class size}
             [:figure.image
              [:img {:src (str "https://api.scryfall.com/cards/"
                               (:scryfall-id card)
                               "?version=" version "&format=image")}]]])]]]]
      (hiccup/include-css "/assets/bulma/css/bulma.min.css")
      (hiccup/include-css "/css/screen.css")])))

(defn sample-booster
  [set-code]
  (let [cards (cards/get-cards-by-set set-code)
        pack (packs/create-booster
              {:cards cards
               :set-code set-code})]
    (display-cards pack)))

(defn cubecobra
  [id & [color]]
  (let [cards (cards/import-cubecobra id)
        cards (if color (filter  #(contains? (set (:colors %))
                                             (str/upper-case color))
                                cards)
                  cards)]
    (display-cards cards "is-2" "small")))

(defn routes []
  ["/packs"
   {:coercion reitit.spec/coercion
    :middleware [middleware/wrap-formats]}
   ["/booster/:set-code"
    {:parameters {:path {:set-code ::set-code}}
     :get {:handler
           (fn [req]
             (let [code (keyword (get-in req [:parameters :path :set-code]))]
               (-> (sample-booster code)
                   (http/ok)
                   (http/content-type "text/html"))))}}]
   ["/cubecobra/:cube-id"
    {:parameters {:path {:cube-id ::cube-id}
                  :query {:color ::colors}}
     :get {:handler
           (fn [req]
             (let [id (get-in req [:parameters :path :cube-id])
                   color (get-in req [:parameters :query :color])]
               (-> (cubecobra id color)
                   (http/ok)
                   (http/content-type "text/html"))))}}]])
