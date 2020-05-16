(ns firedraft.routes.packs
  (:require [firedraft.cards :as cards]
            [firedraft.game.util :refer [supported-sets]]
            [firedraft.middleware :as middleware]
            [firedraft.packs :as packs]
            [hiccup.page :as hiccup]
            [reitit.coercion.spec :as reitit.spec]
            [ring.util.http-response :as http]
            [clojure.spec.alpha :as s]))

(s/def ::set-code
  (s/and string? (set supported-sets)))

(s/def ::cube-id string?)

(defn display-cards
  [cards]
  (hiccup/html5
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
          [:div.column.is-one-fifth
           [:figure.image
            [:img {:src (str "https://api.scryfall.com/cards/"
                             (:scryfall-id card)
                             "?version=large&format=image")}]]])]]]]
    (hiccup/include-css "/assets/bulma/css/bulma.min.css")
    (hiccup/include-css "/css/screen.css")]))

(defn sample-booster
  [set-code]
  (let [cards (cards/get-cards-by-set set-code)
        pack (packs/create-booster
              {:cards cards
               :set-code set-code})]
    (display-cards pack)))

(defn cubecobra
  [id]
  (let [cards (filter #(contains? (set (:colors %)) "U")
                      (cards/import-cubecobra id))]
    (display-cards cards)))

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
    {:parameters {:path {:cube-id ::cube-id}}
     :get {:handler
           (fn [req]
             (let [id (get-in req [:parameters :path :cube-id])]
               (-> (cubecobra id)
                   (http/ok)
                   (http/content-type "text/html"))))}}]])
