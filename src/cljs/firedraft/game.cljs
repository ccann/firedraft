(ns firedraft.game
  (:require [reagent.core :as r]
            [firedraft.ws :as ws]
            [firedraft.common :as com]
            [taoensso.timbre :as log]))

(defn- start-game! [game]
  (ws/send! [:game/start (:id @game)]))

(defn- img-uri
  [id]
  (str "https://api.scryfall.com/cards/"
       id
       "?version=large&format=image"))

(defn open-picker! []
  (.add (.-classList (com/elem "picker"))
        "is-active"))

(defn close-picker! []
  (.remove (.-classList (com/elem "picker"))
           "is-active"))

(defn picker-modal
  [game]
  (let [[type ix :as picked] (:pickable @game)]
    [:div.modal {:id "picker"}
     [:div.modal-background
      {:on-click close-picker!}]
     [:div.modal-content.has-background-white
      [:div.level
       [:div.level-item
        (when (:started? @game)
          (for [card (:cards @game)]
            ^{:key (:sid card)}
            [:figure
             [:img.card
              {:src (img-uri (:sid card))}]]))]]

      [:footer.footer
       [:div.level
        ;; cannot pass on a pick from the deck
        (when (= :pile type)
          [:div.level-left
           [:button.button.is-link
            {:on-click #(do (ws/send! [:game/pass {:game-id (:id @game)
                                                   :pile-ix ix}])
                            (close-picker!))}
            "Pass"]])

        [:div.level-right
         [:button.button.is-link
          {:on-click #(do
                        (log/info :send [:game/pick])
                        (ws/send! [:game/pick {:game-id (:id @game)
                                               :picked picked}]
                                  1000
                                  (fn callback [{:keys [picks]}]
                                    (swap! game assoc :picks picks)))
                        (close-picker!))}
          "Pick"]]]]]]))

(defn picks [game]
  [:div.content
   [:div.level
    (log/info :picks (:picks @game))
    (for [pick (:picks @game)]
      ^{:key (:sid pick)}
      [:figure
       [:img.card-back {:src (img-uri (:sid pick))}]])]])

(defn- my-turn?
  [game]
  (= (:player game) (get-in game [:players (:turn game)])))


(defn- pile [game index]
  (let [is-my-turn? (my-turn? @game)
        pickable? (= [:pile index] (:pickable @game))]
    [:div.content
     [:div.level
      [:div.level-item.has-centered-text
       [:h4 (nth (:piles-count @game) index)]]]
     [:figure
      [:img.card-back {:id (str "pile-" (inc index))
                       :class (when pickable? "pickable")
                       :src "img/card-back-arena.png"
                       :on-click #(when (and is-my-turn? pickable?)
                                    (open-picker!))}]]]))

(defn- deck [game]
  (let [is-my-turn? (my-turn? @game)
        pickable? (= [:deck 0] (:pickable @game))]
    [:div.content
     [:div.level
      [:div.level-item.has-centered-text
       [:h4 (str "Deck: " (:deck-count @game))]]]
     [:figure
      [:img.card-back {:id "deck"
                       :src "img/card-back-arena.png"
                       :class (when pickable? "pickable")
                       :on-click #(when (and is-my-turn? pickable?)
                                    (open-picker!))}]]]))

(defn page [session]
  (r/with-let [match (r/cursor session [:match])
               game (r/cursor session [:game])]
    [:div.tile.is-ancestor
     [:div.tile.is-vertical
      [:div.tile

       [:div.tile.is-6.is-parent
        [:div.tile.is-child
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
               [:li id])]]
           (when (and (not (:started? @game))
                      (= 2 (count (:players @game))))
             [:button.button.is-link
              {:on-click #(start-game! game)}
              "Start Game"])]]]]
       (when (:started? @game)
         [:div.section.tile
          [:div.container
           [:div.content
            [:div.level
             [:div.level-item.has-centered-text
              [:h2 (if (my-turn? @game)
                     "Your Turn"
                     "Oppo's Turn")]]]]]])]
      (picker-modal game)
      (when (:started? @game)
        [:div
         [:div.tile
          (deck game)
          (pile game 0)
          (pile game 1)
          (pile game 2)]
         [:div.tile
          (picks game)]])]]))

(defmethod ws/handle-message :game/turn
  [{:keys [message]}]
  (log/info :handle :game/turn)
  (log/info (pr-str message))
  (swap! com/session update :game #(merge % (assoc message :started? true))))

(defmethod ws/handle-message :game/cards
  [{:keys [message]}]
  (log/info :handle :game/cards (mapv :name (:cards message)))
  (swap! com/session assoc-in [:game :cards] (:cards message)))
