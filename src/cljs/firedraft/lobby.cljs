(ns firedraft.lobby
  (:require [clojure.string :as str]
            [firedraft.common.dom :as dom]
            [firedraft.common.state :as state]
            [firedraft.ws :as ws]
            [taoensso.timbre :as log]
            [firedraft.game.util :as g]))

(defn pack-controls
  [session]
  (for [n (range 6)]
    ^{:key n}
    [:div.control
     [:div.select
      [:select
       {:on-change #(swap! session assoc-in
                           [:game :opts :booster n]
                           (dom/target-value %))}
       (for [-set g/sets]
         ^{:key -set}
         [:option {:title (:name -set)}
          (:code -set)])]]]))

(def game-opt-defaults
  (let [sc (first g/supported-sets)]
    {"winston" {:booster [sc sc sc sc sc sc]
                :set-singleton nil}
     "grid" {}}))

(defn- ->keyword
  [s]
  (keyword (str/replace s #"\s+" "-")))

(defn- set-draft-type!
  [this session]
  (swap! session assoc-in [:game :opts]
         (let [mode (get-in @session [:game :mode])
               v (-> this dom/target-value ->keyword)]
           {v (get-in game-opt-defaults [mode v])})))

(defn- create-game!
  [session]
  (ws/send! [:game/create (:game @session)]
            5000
            (fn callback [data]
              (log/info :game-created (pr-str data))
              (swap! session assoc :game data))))

(defn- set-draft-format!
  [this session]
  (swap! session assoc-in [:game :mode]
         (dom/target-value this)))

(defn section-create-game
  [session]
  (let [winston? (= "winston" (get-in @session [:game :mode]))]
    [:div.section.main_content
     [:div.container
      [:div.content
       [:h3 "Create A Draft"]]
      [:div.field
       [:label.label "Format"]
       [:div.control
        {:on-change #(set-draft-format! % session)}
        [:div.select
         [:select
          (for [mode g/modes] ^{:key mode} [:option mode])]]]]

      (when winston?
        [:div.field
         [:div.field
          [:label.label "Type"]
          [:div.control
           {:on-change #(set-draft-type! % session)}
           [:div.select
            [:select
             [:option "booster"]]]]]
         (when (contains? (get-in @session [:game :opts]) :booster)
           [:div [:label.label "Sets"]
            [:div.field.is-grouped
             (pack-controls session)]])])

      [:div.field
       [:div.control
        [:a {:href "#/game"}
         [:button.button.is-primary
          {:on-click #(create-game! session)}
          "Create Draft"]]]]]]))

(defn open-rules-modal! [session fmt]
  (swap! session assoc :format fmt)
  (.add (.-classList (dom/elem "rules-modal"))
        "is-active"))

(defn close-rules-modal! []
  (.remove (.-classList (dom/elem "rules-modal"))
           "is-active"))


(defn- join-game!
  [session game-id]
  (log/info "join game:" game-id)
  (ws/send! [:game/join game-id]
            2000
            (fn callback [data]
              (if (:error data)
                (log/error [:game/join (:error data)])
                (swap! session assoc :game data)))))

(defn rules-modal
  [session]
  (let [close! #(do (swap! session dissoc :format)
                    (close-rules-modal!))]
    [:div.modal {:id "rules-modal"}
     [:div.modal-background.has-background-black.clickable {:on-click close!}]
     [:div.modal-content
      (case (:format @session)
        :winston
        [:div.content
         [:h2.title "Winston"]
         [:ul
          [:li [:p "6 boosters are shuffled together into one 90-card deck"]]
          [:li [:p "The top 3 cards from the deck are placed face down next to it as 3 new piles of 1 card each."]]
          [:li [:p "The first player looks at Pile 1. They may choose to draft that pile or not."]]
          [:li [:p "If they draft it, that pile is replaced with a new face-down card from the top of the deck."]]
          [:li [:p "If they don't draft it, they put it back and add a new card from the deck to Pile 1 and move on to Pile 2."]]
          [:li [:p "They look at Pile 2 and decide to draft it or not, in the same way as Pile 1. If they do not draft Pile 2, move onto Pile 3."]]
          [:li [:p "If they choose not to draft Pile 3, they must instead draft a card from the top of the deck."]]
          [:li [:p "Continue until all 90 cards have been drafted. Construct 40-card decks and play."]]]
         [:p [:a {:href "https://magic.wizards.com/en/articles/archive/winston-draft-2005-03-25"} "See More"]]]

        :grid
        [:div.content
         [:h2.title "Grid"]
         [:p [:a {:href "https://www.youtube.com/watch?v=yelf_BB6BgY"}
              "See More"]]]

        nil)]
     [:button.modal-close.is-large
      {:on-click close!
       :aria-label "close"}]]))

(defn section-explain-game
  [session]
  [:div.section.main_content
   {:id "app-explainer"}
   [:div.container
    [:div.content
     [:p.title "Draftwith.me is a new way for 2 players to draft online."]
     [:p "Export your picks at the end of the draft and use them to play on MTGA."]
     [:p.subtitle "Draft Format Rules:"]
     [:ul
      [:li
       [:a {:on-click #(open-rules-modal! session :winston)}
        "Winston"]]
      #_[:li
       [:a {:on-click #(open-rules-modal! session :grid)}
        "Grid"]]]]]])

(defn section-join-game
  [session]
  [:div.section.main_content
   [:div.container
    [:div.content
     [:h3 "Drafts"]
     (if (seq (:games @session))
       [:table.table.is-bordered.is-striped.is-hoverable.is-narrow
        [:thead
         [:tr
          [:th "Status"]
          [:th "Name"]
          [:th "Format"]
          [:th "Code"]]]
        [:tbody
         (doall
          (for [game (->> (:games @session)
                          (sort-by :joinable?))]
            ^{:key game}
            [:tr
             [:td (if (:joinable? game)
                    [:div.field
                     [:div.control
                      [:a {:href "#/game"
                           :on-click #(join-game! session (:id game))}
                       "Join Draft"]]]
                    "Full")]
             [:td (:name game)]
             [:td (:mode game)]
             [:td {:id "game-id-input"}
              [:span.is-family-code (:id game)]]]))]]
       [:p "No drafts avaiable. How about creating one?"])]]])

(defn page [session]
  [:div.main
   dom/header
   (rules-modal session)
   (section-explain-game session)
   (section-create-game session)
   (section-join-game session)
   dom/footer])

(defmethod ws/handle-message :game/joined
  [{:keys [message]}]
  (log/info :handle :game/joined)
  (let [players (:players message)]
    (log/info "set players:" (pr-str players))
    (swap! state/session assoc-in [:game :players] players)))

(defmethod ws/handle-message :games/available
  [{:keys [message]}]
  (log/info :handle :games/available)
  (swap! state/session assoc :games message))
