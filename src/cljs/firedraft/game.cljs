(ns firedraft.game
  (:require [reagent.core :as r]
            [firedraft.ws :as ws]
            [firedraft.common.state :as state]
            [firedraft.common.dom :as dom]
            [taoensso.timbre :as log]
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
  (let [[type ix :as picked] (:pickable @game)
        cards (:cards @game)]
    [:div.modal {:id "picker"}
     [:div.modal-background {:on-click close-picker!}]
     [:div.modal-content.has-background-white.picker-modal
      [:div.modal-cards
       [:div.columns.is-centered
        (for [card cards]
          ^{:key (:sid card)}
          [:div.column.modal-card-container
           [:figure.image
            [:img.card.modal-card
             {:style #js
              {:transform-origin
               (cond
                 (< (count cards) 3) "center center"
                 (= card (first cards)) "center left"
                 (= card (last cards)) "center right"
                 :else "center center")}
              :class (when (< 2 (count cards)) "zoomable-card")
              :src (img-uri (:sid card))}]]])]]

      [:div.level
       [:div.level-item
        [:div.field.is-grouped.buttons.are-medium.modal-buttons
         (when (and (= :pile type)      ; cannot pass on a pick from the deck
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
          "Pick"]]]]]
     [:button.modal-close.is-large
      {:aria-label "close"}]]))

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
        pickable? (= [:pile index] (:pickable @game))
        pile-count (nth (:piles-count @game) index)]
    [:div.tile.is-child
     [:div.level
      [:div.level-item.has-centered-text
       [:div.content.count-label
        [:h4 pile-count]]]]
     [:div.pile
      [:figure.image.is-3by4
       (for [n (range pile-count)]
         ^{:key n}
         [:img.card-back.pile-card
          {:id (str "pile-" (inc index))
           :style #js {:position "absolute"
                       :top (* n 15)}
           :class (dom/classes (when pickable? "pickable")
                               (when (zero? pile-count) "hidden"))
           :src "img/card-back-arena.png"
           :on-click #(when (and is-my-turn? pickable?)
                        (open-picker!))}])]]]))

(defn- deck [game]
  (let [is-my-turn? (my-turn? @game)
        pickable? (= [:deck 0] (:pickable @game))]
    [:div.tile.is-child
     [:div.level
      [:div.level-item.has-centered-text
       [:div.content.count-label
        [:h4 (str "Deck: " (:deck-count @game))]]]]
     [:div.pile
      [:figure.image.is-3by4
       [:img.card-back.pile-card
        {:id "deck"
         :style #js {:position "absolute"}
         :src "img/card-back-arena.png"
         :class (dom/classes (when pickable? "pickable")
                             (when (zero? (:deck-count @game)) "hidden"))
         :on-click #(when (and is-my-turn? pickable?)
                      (open-picker!))}]]]]))

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
             (:id @game)]
            [:button.button.is-small
             {:id "copy-button"
              :on-click #(dom/copy-to-clipboard (:id @game))}
             "copy"]]
           [:h2 "Players"]
           [:div
            [:ol {:type "1"}
             (for [id (:players @game)]
               ^{:key id}
               [:li id])]]]
          (when (and (not (:started? @game))
                     (not (:over? @game))
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
           (picks game)]])
       (when (:over? @game)
         [:div.tile.is-vertical.is-parent
          [:div.content.is-child
           [:h2 "Pick List"]
           [:p
            (for [line (str/split-lines (:pick-list @game))]
              [:span line [:br]])]]])]]
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
