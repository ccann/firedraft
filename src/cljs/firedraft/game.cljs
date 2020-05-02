(ns firedraft.game
  (:require [reagent.core :as r]
            [firedraft.ws :as ws]
            [firedraft.common.state :as state]
            [firedraft.common.dom :as dom]
            [taoensso.timbre :as log]
            [ajax.core :as ajax]
            [clojure.string :as str]))

(defn- start-game! [game]
  (ws/send! [:game/start (:id @game)]))

(defn- img-uri
  [id]
  (str "https://api.scryfall.com/cards/"
       id
       "?version=large&format=image"))

(defn open-picker! []
  (.add (.-classList (dom/elem "picker"))
        "is-active"))

(defn close-picker! []
  (.remove (.-classList (dom/elem "picker"))
           "is-active"))

(defn picker-modal
  [game]
  (let [[type ix :as picked] (:pickable @game)]
    [:div.modal {:id "picker"}
     [:div.modal-background
      {:on-click close-picker!}]
     [:div.modal-content.has-background-white
      [:div.level.modal-card-container
       [:div.level-item
        (when (:started? @game)
          (for [card (:cards @game)]
            ^{:key (:sid card)}
            [:figure
             [:img.card.modal-card
              {:src (img-uri (:sid card))}]]))]]

      [:div.level
       ;; cannot pass on a pick from the deck
       [:div.level-item
        [:div.field.is-grouped.buttons.are-medium.modal-buttons
         (when (and (= :pile type)
                    (if (= ix 2)
                      (< 0 (:deck-count @game))
                      (< 0 (get-in @game [:piles-count (inc ix)]))))
           [:button.button.is-danger
            {:on-click #(do (ws/send! [:game/pass {:game-id (:id @game)
                                                   :pile-ix ix}])
                            (close-picker!))}
            "Pass"])

         [:button.button.is-success
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
  [:div.tile.is-child
   [:div.content
    [:h2 "Picks"]]
   [:div.picks
    (for [[i pick] (map vector (range) (:picks @game))]
      ^{:key i}
      [:figure
       [:img.card.pick
        {:style #js {:position "absolute"
                     :top (* i 33)}
         :src (img-uri (:sid pick))}]])]])

(defn- my-turn?
  [game]
  (= (:player game) (get-in game [:players (:turn game)])))

(defn- pile [game index]
  (let [is-my-turn? (my-turn? @game)
        pickable? (= [:pile index] (:pickable @game))]
    [:div.tile.is-child
     [:div.level
      [:div.level-item.has-centered-text
       [:div.content
        [:h4 (nth (:piles-count @game) index)]]]]
     [:div.level
      [:div.level-item
       [:figure.image
        [:img.card-back {:id (str "pile-" (inc index))
                         :class (when pickable? "pickable")
                         :src "img/card-back-arena.png"
                         :on-click #(when (and is-my-turn? pickable?)
                                      (open-picker!))}]]]]]))

(defn- deck [game]
  (let [is-my-turn? (my-turn? @game)
        pickable? (= [:deck 0] (:pickable @game))]
    [:div.tile.is-child
     [:div.level
      [:div.level-item.has-centered-text
       [:div.content
        [:h4 (str "Deck: " (:deck-count @game))]]]]
     [:div.level
      [:div.level-item.has-centered-text
       [:figure.image
        [:img.card-back {:id "deck"
                         :src "img/card-back-arena.png"
                         :class (when pickable? "pickable")
                         :on-click #(when (and is-my-turn? pickable?)
                                      (open-picker!))}]]]]]))

(defn page [session]
  (r/with-let [game (r/cursor session [:game])]
    [:div.section
     [:div.tile.is-ancestor
      [:div.container.tile.is-vertical
       [:div.tile
        [:div.tile.is-8.is-parent
         [:div.tile.is-child
          (dom/header)
          [:div.content
           [:p.subtitle "Game ID: "
            [:span.is-family-code.has-background-grey-lighter
             (:id @game)]]
           [:h2 "Players"]
           [:div
            [:ol {:type "1"}
             (for [id (:players @game)]
               ^{:key id}
               [:li id])]]]
          (when (and (not (:started? @game))
                     (= 2 (count (:players @game))))
            [:button.button.is-link
             {:on-click #(start-game! game)}
             "Start Game"])]]
        (when (:started? @game)
          [:div.tile.is-4.is-child
           [:div.level
            [:div.level-item.has-centered-text
             [:div.content
              [:h2 (if (my-turn? @game)
                     "Your Turn"
                     "Oppo's Turn")]]]]])]

       (picker-modal game)
       (when (:started? @game)
         [:div
          [:div.tile.is-parent
           (deck game)
           (pile game 0)
           (pile game 1)
           (pile game 2)]
          [:div.tile.is-parent
           (picks game)]])]]
     (when (:over? @game)
       [:div.content
        [:p
         (for [line (str/split-lines (:pick-list @game))]
           [:span line [:br]])]])
     #_[:footer.footer
        [:div.content.has-text-centered
         [:p "author: @ccann"]]]]))

;; handle stepping through the game state
;; whose turn it is may or may not change
(defmethod ws/handle-message :game/step
  [{:keys [message]}]
  (log/info :handle :game/step)
  (swap! state/session update :game #(merge % (assoc message :started? true))))

(defmethod ws/handle-message :game/cards
  [{:keys [message]}]
  (let [cards (:cards message)]
    (log/info :handle :game/cards (mapv :name cards))
    (when (seq cards)
      (swap! state/session assoc-in [:game :cards] cards))))

(defmethod ws/handle-message :game/end
  [{:keys [message]}]
  (log/info :handle :game/end)
  (swap! state/session assoc-in [:game :pick-list] message)
  (swap! state/session assoc-in [:game :started?] false)
  (swap! state/session assoc-in [:game :over?] true))
