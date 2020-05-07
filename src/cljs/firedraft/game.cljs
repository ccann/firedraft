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
  (when id
    (str "https://api.scryfall.com/cards/"
         id
         "?version=large&format=image")))

(defn open-zoom-view! []
  (.add (.-classList (dom/elem "zoom-view"))
        "is-active"))

(defn close-zoom-view! []
  (.remove (.-classList (dom/elem "zoom-view"))
           "is-active"))

(defn open-picker! []
  (.add (.-classList (dom/elem "picker"))
        "is-active"))

(defn close-picker! []
  (.remove (.-classList (dom/elem "picker"))
           "is-active"))

(defn make-pick! [game picked]
  (log/info :send [:game/pick])
  (ws/send! [:game/pick {:game-id (:id @game)
                         :picked picked}]
            1000
            (fn callback [{:keys [picks]}]
              (swap! game assoc :picks picks)))
  (close-picker!))

(defn pass-pick! [game ix]
  (ws/send! [:game/pass {:game-id (:id @game)
                         :pile-ix ix}])
  (close-picker!))

(defn zoom-card! [game card]
  (swap! game assoc :zoom-view card)
  (open-zoom-view!))

(defn zoom-view-modal
  [game-atom]
  (let [close! #(do (swap! game-atom dissoc :zoom-view)
                    (close-zoom-view!))]
    [:div.modal {:id "zoom-view"}
     [:div.modal-content
      {:on-click close!}
      [:figure.image.zoom-view
       [:img.card
        {:src (img-uri (:sid (:zoom-view @game-atom)))}]]]]))

(defn picker-modal
  [game]
  (let [[type ix :as picked] (:pickable @game)
        cards (:cards @game)
        middle (int (/ (count cards) 2))]
    [:div.modal {:id "picker"}
     [:div.modal-background {:on-click close-picker!}]
     [:div.modal-content.picker-modal
      [:div.columns.is-centered.is-mobile.is-multiline
       (doall
        (for [[i card] (map vector (range) cards)]
          ^{:key (:sid card)}
          [:div.column
           [:figure.image
            {:style #js
             {:float (cond (< i middle) "right"
                           (< middle i) "left"
                           :else (if (even? (count cards)) "left" "none"))}}
            [:img.card.modal-card
             {:on-click #(zoom-card! game card)
              :style #js
              {:transformOrigin
               (cond
                 (< (count cards) 3) "center center"
                 (= card (first cards)) "center left"
                 (= card (last cards)) "center right"
                 :else "center center")}
              :src (img-uri (:sid card))}]]]))]

      [:div.columns.is-centered
       [:div.column.no-padding]
       [:div.column
        [:div.level
         [:div.level-item
          [:div.field.is-grouped.buttons.are-medium.modal-buttons
           (when (and (= :pile type) ; cannot pass on a pick from the deck
                      (if (= ix 2)
                        (< 0 (:deck-count @game))
                        (< 0 (get-in @game [:piles-count (inc ix)]))))
             [:button.button.is-danger
              {:on-click #(pass-pick! game ix)}
              "Pass"])

           [:button.button.is-success
            {:on-click #(make-pick! game picked)}
            "Pick"]]]]]
       [:div.column.no-padding]]]
     [:button.modal-close.is-large
      {:on-click close-picker!
       :aria-label "close"}]]))

(defn picks [game]
  (let [picks (:picks @game)]
    [:div.tile.is-parent
     [:div.tile.is-child.picks-container
      [:div.content
       [:h2 "Picks"]]
      [:div.columns.is-mobile.picks.is-variable.is-1
       (doall
        (for [i (range 5)]
          ^{:key i}
          [:div.column.is-one-fifth
           (let [op (case (inc i) 1 >= (2 3 4) = 5 <=)]
             (doall
              (for [[j pick] (map vector (range)
                                  (->> picks
                                       (filter #(op (inc i) (:cmc %)))
                                       (sort-by (juxt :name :col))))]
                ^{:key (str i j)}
                [:figure.image
                 [:img.card.pick
                  {:id (str "pick-" i "-" j)
                   :style #js {:position "absolute"
                               :top (let [ht (some-> (str "pick-" i "-" (dec j))
                                                     (dom/elem)
                                                     (.-clientHeight))]
                                      (* j (* .11 (or ht 0))))}
                   :on-click #(zoom-card! game pick)
                   :src (img-uri (:sid pick))}]])))]))]]]))

(defn- my-turn?
  [game]
  (= (:player game) (get-in game [:players (:turn game)])))

(defn- pile [game index]
  (let [is-my-turn? (my-turn? @game)
        pickable? (= [:pile index] (:pickable @game))
        pile-count (nth (:piles-count @game) index)]
    [:div.column.is-one-quarter
     [:div.level
      [:div.level-item.has-centered-text
       [:div.content.count-label
        [:h4 pile-count]]]]
     [:div.pile
      [:figure.image.is-3by4
       (doall
        (for [n (range pile-count)]
          ^{:key n}
          [:img.card-back.pile-card
           {:id (str "pile-" (inc index) "-" n)
            :style #js {:position "absolute"
                        :top (let [ht (some-> (str "pile-" (inc index) "-" (dec n))
                                              (dom/elem)
                                              (.-clientHeight))]
                               (* n (* .06 (or ht 0))))}
            :class (dom/classes (when pickable? "pickable")
                                (when (my-turn? @game) "pickable-by-me")
                                (when (zero? pile-count) "hidden"))
            :src "img/card-back-arena.png"
            :on-click #(when (and is-my-turn? pickable?)
                         (open-picker!))}]))]]]))

(defn- deck [game]
  (let [is-my-turn? (my-turn? @game)
        pickable? (= [:deck 0] (:pickable @game))]
    [:div.column.is-one-quarter
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
                             (when (my-turn? @game) "pickable-by-me")
                             (when (zero? (:deck-count @game)) "hidden"))
         :on-click #(when (and is-my-turn? pickable?)
                      (open-picker!))}]]]]))

(defn page [session]
  (r/with-let [game (r/cursor session [:game])]
    (let [drafting? (:started? @game)
          postdraft? (:over? @game)
          predraft? (and (not drafting?) (not (:over? @game)))]
      [:div (dom/header)
       [:div.section
        [:div.tile.is-ancestor
         [:div.container.tile.is-vertical
          [:div.tile.is-parent
           (when predraft?
             [:div.tile.is-child
              [:div.content
               [:div.tags.has-addons.are-medium
                [:span.tag.is-dark "name"]
                [:span.tag.is-primary (:name @game)]]
               [:div.tags.has-addons.are-medium
                [:span.tag.is-dark
                 "mode"]
                [:span.tag.is-primary
                 (:mode @game)]
                (when-let [subtype (case (keys (:opts @game))
                                     [:booster] "booster")]
                  [:span.tag.is-info subtype])]
               [:h2 "Players"]
               [:div
                (for [[i id] (map vector (range) (:players @game))]
                  ^{:key id}
                  [:div.tags.has-addons.are-medium
                   [:span.tag.is-dark (inc i)]
                   [:span.tag.is-family-code.is-primary id]])]]
              (case (count (:players @game))
                2 [:button.button.is-link
                   {:on-click #(start-game! game)}
                   "Start Game"]
                1 [:div.content.is-size-4
                   [:p "Waiting for a second player to join..."]]
                [:div.content
                 [:p.has-text-danger.is-size-4
                  "This game mode only supports 2 players!"]])])
           (when drafting?
             [:div.tile.is-child
              [:div.content.has-text-centered
               [:h2 (if (my-turn? @game)
                      "Your Turn"
                      "Oppo's Turn")]]])]
          (picker-modal game)
          (zoom-view-modal game)
          [:div.tile.is-parent
           [:div.tile.is-child
            (when drafting?
              [:div.columns.is-mobile
               (deck game)
               (pile game 0)
               (pile game 1)
               (pile game 2)])]]
          (when postdraft?
            [:div.content
             [:h2 "Pick List"]
             [:p
              (for [line (str/split-lines (:pick-list @game))]
                [:span line [:br]])]])
          (when drafting?
            (picks game))]]
        #_[:footer.footer
           [:div.content.has-text-centered
            [:p "author: @ccann"]]]]])))

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

(defmethod ws/handle-message :game/update-players
  [{:keys [message]}]
  (log/info :handle :game/update-players)
  (swap! state/session assoc-in [:game :players] message))
