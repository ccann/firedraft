(ns firedraft.game
  (:require [reagent.core :as r]))

(defn page [session]
  (r/with-let [match (r/cursor session [:match])
               game (r/cursor session [:game])]
    [:div.section
     [:div.container
      [:h1.title "Firedraft"]
      [:p.subtitle "Game ID: "
       [:span.is-family-code.has-background-grey-lighter
        (:id @game)]]
      [:h2 "Players"]
      [:div.content
       [:ol {:type "1"}
        (for [id (:players @game)]
          ^{:key id}
          [:li id])]]]]))
