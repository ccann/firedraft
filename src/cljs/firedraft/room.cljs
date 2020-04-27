(ns firedraft.room
  (:require [reagent.core :as r]))

(defn page [session]
  (r/with-let [match (r/cursor session [:match])
               room (r/cursor session [:room])]
    [:div.section
     [:div.container
      [:h1.title "Firedraft"]
      [:p.subtitle "Room ID: "
       [:span.is-family-code.has-background-grey-lighter
        (:id @room)]]]]))
